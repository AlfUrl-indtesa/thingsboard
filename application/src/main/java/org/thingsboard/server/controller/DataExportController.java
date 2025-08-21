/**
 * Copyright © 2016-2025 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.thingsboard.common.util.JacksonUtil;

import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.Aggregation;
import org.thingsboard.server.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.server.common.data.kv.ReadTsKvQuery;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.config.annotations.ApiOperation;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.permission.Operation;

import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@TbCoreComponent
@RequiredArgsConstructor
@RequestMapping("/api")
public class DataExportController extends BaseController {

    private final TimeseriesService timeseriesService;

    public enum DataExportFormat { CSV, JSON }

    @ApiOperation(value = "Export time-series data (exportData)")
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN','CUSTOMER_USER')")
    @GetMapping(value = {"/data_export/{entityType}/{entityId}", "/data-export/{entityType}/{entityId}"})
    public void exportData(@PathVariable("entityType") String strEntityType,
                           @PathVariable("entityId") String strEntityId,
                           @RequestParam("keys") String keysParam,
                           @RequestParam("startTs") long startTs,
                           @RequestParam("endTs") long endTs,
                           @RequestParam(name = "interval", required = false, defaultValue = "0") long interval,
                           @RequestParam(name = "agg", required = false, defaultValue = "NONE") String aggStr,
                           @RequestParam(name = "format", required = false, defaultValue = "CSV") String formatStr,
                           @RequestHeader(name = HttpHeaders.ACCEPT_ENCODING, required = false) String acceptEncodingHeader,
                           HttpServletResponse response) throws Exception {

        if (!"DEVICE".equalsIgnoreCase(strEntityType)) {
            throw new ThingsboardException("Only DEVICE entityType is supported",
                    ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        if (startTs <= 0 || endTs <= 0 || startTs >= endTs) {
            throw new ThingsboardException("Invalid time range",
                    ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        long now = System.currentTimeMillis();
        if (endTs > now) {
            endTs = now;
        }

        // Permisos y pertenencia
        DeviceId deviceId = new DeviceId(toUUID(strEntityId)); // usa el de BaseController
        Device device = checkDeviceId(deviceId, Operation.READ_TELEMETRY);
        TenantId tenantId = getTenantId();

        // Claves
        List<String> keys = parseKeys(keysParam);
        if (keys.isEmpty()) {
            throw new ThingsboardException("keys parameter is empty",
                    ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }

        // Agregación e intervalo
        Aggregation aggregation;
        try {
            aggregation = Aggregation.valueOf(aggStr.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new ThingsboardException("Invalid agg parameter: " + aggStr,
                    ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        if (aggregation != Aggregation.NONE && interval <= 0) {
            throw new ThingsboardException("interval must be > 0 when agg != NONE",
                    ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        if (aggregation == Aggregation.NONE) {
            interval = 0L; // explícito en RAW
        }

        final int limit = 100_000; // límite razonable por consulta
        List<ReadTsKvQuery> queries = keys.stream()
                .map(k -> new BaseReadTsKvQuery(k, startTs, endTs, interval, limit, aggregation))
                .collect(Collectors.toList());

        List<TsKvEntry> entries = timeseriesService.findAll(tenantId, deviceId, queries).get();

        DataExportFormat format = DataExportFormat.valueOf(formatStr.toUpperCase(Locale.ROOT));

        switch (format) {
            case JSON: {
                // JSON agrupado por key: { "key": [ {ts, value}, ... ], ... }
                Map<String, List<Map<String, Object>>> json = new LinkedHashMap<>();
                for (String k : keys) {
                    json.put(k, new ArrayList<>());
                }
                for (TsKvEntry e : entries) {
                    String key = e.getKey();
                    List<Map<String, Object>> arr = json.get(key);
                    if (arr == null) continue;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("ts", e.getTs());
                    row.put("value", valueAsString(e));
                    arr.add(row);
                }
                byte[] jsonBytes = JacksonUtil.toString(json).getBytes(StandardCharsets.UTF_8);
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"data_export_" + Instant.ofEpochMilli(now) + ".json\"");
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                compressResponseWithGzipIFAccepted(acceptEncodingHeader, response, jsonBytes);
                break;
            }
            case CSV:
            default: {
                // CSV de 3 columnas: key,ts,value
                StringBuilder sb = new StringBuilder();
                sb.append("key,ts,value\n");
                for (TsKvEntry e : entries) {
                    sb.append(escapeCsv(e.getKey())).append(',')
                      .append(e.getTs()).append(',')
                      .append(escapeCsv(valueAsString(e))).append('\n');
                }
                byte[] csvBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"data_export_" + Instant.ofEpochMilli(now) + ".csv\"");
                response.setContentType("text/csv; charset=UTF-8");
                try (OutputStream os = response.getOutputStream()) {
                    os.write(csvBytes);
                    os.flush();
                }
                break;
            }
        }
    }

    private List<String> parseKeys(String keysParam) {
        if (keysParam == null || keysParam.isBlank()) return Collections.emptyList();
        return Arrays.stream(keysParam.split(","))
                .map(k -> URLDecoder.decode(k.trim(), StandardCharsets.UTF_8))
                .filter(k -> !k.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    // TsKvEntry getters devuelven Optional<> en esta rama
    private String valueAsString(TsKvEntry e) {
        if (e.getStrValue().isPresent()) return e.getStrValue().get();
        if (e.getLongValue().isPresent()) return String.valueOf(e.getLongValue().get());
        if (e.getDoubleValue().isPresent()) return String.valueOf(e.getDoubleValue().get());
        if (e.getBooleanValue().isPresent()) return String.valueOf(e.getBooleanValue().get());
        if (e.getJsonValue().isPresent()) return e.getJsonValue().get();
        return "";
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        boolean mustQuote = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String v = s.replace("\"", "\"\"");
        return mustQuote ? "\"" + v + "\"" : v;
    }
}
