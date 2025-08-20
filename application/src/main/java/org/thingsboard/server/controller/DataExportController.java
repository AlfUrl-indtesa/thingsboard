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

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.kv.BaseTsKvQuery;
import org.thingsboard.server.common.data.kv.BooleanDataEntry;
import org.thingsboard.server.common.data.kv.DoubleDataEntry;
import org.thingsboard.server.common.data.kv.JsonDataEntry;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.kv.TsKvQuery;
// ⚠️ En tu rama, Aggregation suele estar aquí:
import org.thingsboard.server.common.data.kv.Aggregation;
// Si te fallara, prueba: import org.thingsboard.server.common.data.query.Aggregation;

import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.config.annotations.ApiOperation;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.permission.Operation;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@TbCoreComponent
@RequiredArgsConstructor
@RequestMapping("/api")
public class DataExportController extends BaseController {

    private final TimeseriesService timeseriesService;

    @ApiOperation(value = "Export device telemetry (exportData)",
            notes = "Exports timeseries for the given device and keys within the time window. " +
                    "Supports raw and aggregated queries. Returns CSV (default) or JSON.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "text/csv",
            examples = @ExampleObject(value = "timestamp,key1,key2\n1712092800000,12.3,1\n")))
    @GetMapping(value = {"/data_export/{entityType}/{entityId}", "/data-export/{entityType}/{entityId}"})
    @PreAuthorize("hasAuthority('CUSTOMER_USER')")
    public void exportData(@PathVariable String entityType,
                           @PathVariable("entityId") String strEntityId,
                           @RequestParam String keys,
                           @RequestParam long startTs,
                           @RequestParam long endTs,
                           @RequestParam(required = false) Long interval,      // ms (opcional)
                           @RequestParam(required = false) String agg,         // NONE|MIN|MAX|AVG|SUM
                           @RequestParam(defaultValue = "CSV") String format,  // CSV | JSON
                           HttpServletResponse response) throws Exception {

        if (!EntityType.DEVICE.name().equals(entityType)) {
            throw new ThingsboardException("Only DEVICE is supported",
                    ThingsboardErrorCode.BAD_REQUEST_PARAMS, HttpStatus.BAD_REQUEST);
        }

        // Permisos y pertenencia al customer actual
        DeviceId deviceId = new DeviceId(toUUID(strEntityId));
        Device device = checkDeviceId(deviceId, Operation.READ);

        long now = System.currentTimeMillis();
        if (endTs > now) endTs = now;
        if (startTs > endTs) {
            throw new ThingsboardException("startTs must be <= endTs",
                    ThingsboardErrorCode.BAD_REQUEST_PARAMS, HttpStatus.BAD_REQUEST);
        }

        List<String> keyList = Arrays.stream(keys.split(","))
                .map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .collect(Collectors.toList());
        if (keyList.isEmpty()) {
            throw new ThingsboardException("keys parameter is empty",
                    ThingsboardErrorCode.BAD_REQUEST_PARAMS, HttpStatus.BAD_REQUEST);
        }

        Aggregation aggregation = Aggregation.NONE;
        if (agg != null && !agg.isBlank()) {
            try {
                aggregation = Aggregation.valueOf(agg.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ThingsboardException("Unsupported agg: " + agg,
                        ThingsboardErrorCode.BAD_REQUEST_PARAMS, HttpStatus.BAD_REQUEST);
            }
        }
        long usedInterval = interval != null ? Math.max(0, interval) : 0L;
        if (aggregation != Aggregation.NONE && usedInterval <= 0) {
            throw new ThingsboardException("interval (ms) must be > 0 when agg != NONE",
                    ThingsboardErrorCode.BAD_REQUEST_PARAMS, HttpStatus.BAD_REQUEST);
        }

        // Construir queries por key
        int limit = 0; // sin límite explícito
        List<TsKvQuery> queries = new ArrayList<>(keyList.size());
        for (String key : keyList) {
            queries.add(new BaseTsKvQuery(key, startTs, endTs, usedInterval, limit, aggregation));
        }

        // Ejecutar consulta para todas las keys
        EntityId eid = EntityIdFactory.getByTypeAndUuid(entityType, deviceId.getId());
        List<List<TsKvEntry>> resultPerKey = timeseriesService.findAll(getTenantId(), eid, queries);

        // Pivot por timestamp (columnas por key)
        Map<String, Integer> keyIndex = new LinkedHashMap<>();
        for (int i = 0; i < keyList.size(); i++) keyIndex.put(keyList.get(i), i);

        TreeMap<Long, String[]> rows = new TreeMap<>();
        for (int i = 0; i < keyList.size(); i++) {
            List<TsKvEntry> series = (i < resultPerKey.size()) ? resultPerKey.get(i) : Collections.emptyList();
            for (TsKvEntry e : series) {
                long ts = e.getTs();
                String[] row = rows.computeIfAbsent(ts, t -> new String[keyList.size()]);
                row[i] = kvToString(e);
            }
        }

        String out = format == null ? "CSV" : format.trim().toUpperCase(Locale.ROOT);
        if ("JSON".equals(out)) {
            writeJson(response, keyList, rows);
        } else {
            writeCsv(response, keyList, rows);
        }
    }

    /* ===== Helpers de serialización ===== */

    private static String kvToString(TsKvEntry e) {
        Optional<KvEntry> v = e.getKv();
        if (v.isEmpty()) return null;
        KvEntry kv = v.get();
        if (kv instanceof StringDataEntry) return ((StringDataEntry) kv).getValue();
        if (kv instanceof LongDataEntry)   return String.valueOf(((LongDataEntry) kv).getValue());
        if (kv instanceof DoubleDataEntry) return String.valueOf(((DoubleDataEntry) kv).getValue());
        if (kv instanceof BooleanDataEntry)return String.valueOf(((BooleanDataEntry) kv).getValue());
        if (kv instanceof JsonDataEntry)   return ((JsonDataEntry) kv).getJsonValue();
        return kv.getValueAsString();
    }

    private void writeCsv(HttpServletResponse response, List<String> keyList, NavigableMap<Long, String[]> rows) throws Exception {
        String filename = "data_export_" + System.currentTimeMillis() + ".csv";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setContentType("text/csv");

        StringBuilder sb = new StringBuilder();
        sb.append("timestamp");
        for (String k : keyList) sb.append(',').append(escapeCsv(k));
        sb.append('\n');
        response.getWriter().write(sb.toString());
        sb.setLength(0);

        for (Map.Entry<Long, String[]> en : rows.entrySet()) {
            long ts = en.getKey();
            String[] cols = en.getValue();
            sb.append(ts);
            for (int i = 0; i < keyList.size(); i++) {
                sb.append(',');
                String v = (cols != null && i < cols.length) ? cols[i] : null;
                if (v != null) sb.append(escapeCsv(v));
            }
            sb.append('\n');
            response.getWriter().write(sb.toString());
            sb.setLength(0);
        }
        response.getWriter().flush();
        response.flushBuffer();
    }

    private void writeJson(HttpServletResponse response, List<String> keyList, NavigableMap<Long, String[]> rows) throws Exception {
        String filename = "data_export_" + System.currentTimeMillis() + ".json";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setContentType("application/json");

        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<Long, String[]> en : rows.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('{');
            sb.append("\"timestamp\":").append(en.getKey());
            String[] cols = en.getValue();
            for (int i = 0; i < keyList.size(); i++) {
                sb.append(',');
                sb.append('"').append(jsonEscape(keyList.get(i))).append('"').append(':');
                String v = (cols != null && i < cols.length) ? cols[i] : null;
                if (v == null) {
                    sb.append("null");
                } else if (isNumeric(v) || "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) {
                    sb.append(v);
                } else {
                    sb.append('"').append(jsonEscape(v)).append('"');
                }
            }
            sb.append('}');
        }
        sb.append(']');
        response.getWriter().write(sb.toString());
        response.getWriter().flush();
        response.flushBuffer();
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        boolean hasComma = s.indexOf(',') >= 0;
        boolean hasQuote = s.indexOf('"') >= 0;
        boolean hasNL = s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (hasComma || hasQuote || hasNL) return '"' + s.replace("\"", "\"\"") + '"';
        return s;
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        char c0 = s.charAt(0);
        if (!(Character.isDigit(c0) || c0 == '-' || c0 == '+')) return false;
        try { Double.parseDouble(s); return true; } catch (Exception e) { return false; }
    }
}
