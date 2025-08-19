/*
 * Copyright © 2016-2025 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0
 */
package org.thingsboard.server.controller;

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
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.kv.TsKvQuery;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.kv.BooleanDataEntry;
import org.thingsboard.server.common.data.kv.DoubleDataEntry;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.kv.JsonDataEntry;
import org.thingsboard.server.common.data.query.Aggregation;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.exception.ThingsboardErrorCode;
import org.thingsboard.server.exception.ThingsboardException;
import org.thingsboard.server.queue.util.TbCoreComponent;

import javax.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@TbCoreComponent
@RequestMapping("/api")
public class DataExportController extends BaseController {

    private final TimeseriesService tsService;

    public DataExportController(TimeseriesService tsService) {
        this.tsService = tsService;
    }

    // Soporta ambas variantes: /api/data_export/... y /api/data-export/...
    @GetMapping(value = {
            "/data_export/{entityType}/{entityId}",
            "/data-export/{entityType}/{entityId}"
    })
    @PreAuthorize("hasAuthority('CUSTOMER_USER')")
    public void exportData(@PathVariable String entityType,
                           @PathVariable UUID entityId,
                           @RequestParam String keys,
                           @RequestParam long startTs,
                           @RequestParam long endTs,
                           @RequestParam(required = false) Long interval,    // ms (opcional)
                           @RequestParam(required = false) String agg,       // NONE|MIN|MAX|AVG|SUM
                           @RequestParam(defaultValue = "CSV") String format,
                           HttpServletResponse response) throws Exception {

        // 1) Validaciones de entidad/rol/permisos
        if (!EntityType.DEVICE.name().equals(entityType)) {
            throw new ThingsboardException("Only DEVICE is supported",
                    ThingsboardErrorCode.BAD_REQUEST_PARAMS, HttpStatus.BAD_REQUEST);
        }

        // checkDeviceId aplica reglas tenant/customer + READ
        Device device = checkDeviceId(new DeviceId(entityId), Operation.READ);

        // 2) Normalización de fechas
        long now = System.currentTimeMillis();
        if (endTs > now) {
            endTs = now;
        }
        if (startTs > endTs) {
            throw new ThingsboardException("startTs must be <= endTs",
                    ThingsboardErrorCode.BAD_REQUEST_PARAMS, HttpStatus.BAD_REQUEST);
        }

        // 3) Parse de keys (vienen URL-encoded)
        List<String> keyList = Arrays.stream(keys.split(","))
                .map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8))
                .filter(k -> !k.isBlank())
                .collect(Collectors.toList());
        if (keyList.isEmpty()) {
            throw new ThingsboardException("keys parameter is empty",
                    ThingsboardErrorCode.BAD_REQUEST_PARAMS, HttpStatus.BAD_REQUEST);
        }

        // 4) Agregación/intervalo
        Aggregation aggregation = Aggregation.NONE;
        if (agg != null && !agg.isBlank()) {
            try {
                aggregation = Aggregation.valueOf(agg);
            } catch (IllegalArgumentException e) {
                throw new ThingsboardException("Unsupported agg: " + agg,
                        ThingsboardErrorCode.BAD_REQUEST_PARAMS, HttpStatus.BAD_REQUEST);
            }
        }
        long usedInterval = interval != null ? Math.max(0, interval) : 0L;
        // Si se pidió agregación sin interval, exige interval > 0
        if (aggregation != Aggregation.NONE && usedInterval <= 0) {
            throw new ThingsboardException("interval (ms) must be > 0 when agg != NONE",
                    ThingsboardErrorCode.BAD_REQUEST_PARAMS, HttpStatus.BAD_REQUEST);
        }

        // 5) Construir consultas TsKv por key
        // limit: 0 = sin límite (el servicio aplica el necesario según agg/interval)
        int limit = 0;
        List<TsKvQuery> queries = new ArrayList<>(keyList.size());
        for (String key : keyList) {
            queries.add(new BaseTsKvQuery(
                    key,
                    startTs,
                    endTs,
                    usedInterval,
                    limit,
                    aggregation
            ));
        }

        // 6) Ejecutar consulta (una sola llamada para todas las keys)
        EntityId eid = EntityIdFactory.getByTypeAndUuid(entityType, entityId);
        // retorna lista de listas alineada a 'queries' (una lista por key)
        List<List<TsKvEntry>> resultPerKey = tsService.findAll(getTenantId(), eid, queries);

        // 7) Pivot por timestamp: Map<ts, String[]>
        // índice de columna por key
        Map<String, Integer> keyIndex = new LinkedHashMap<>();
        for (int i = 0; i < keyList.size(); i++) {
            keyIndex.put(keyList.get(i), i);
        }
        // ts -> array de valores por columna
        TreeMap<Long, String[]> rows = new TreeMap<>();
        for (int i = 0; i < keyList.size(); i++) {
            String key = keyList.get(i);
            List<TsKvEntry> series = (i < resultPerKey.size()) ? resultPerKey.get(i) : Collections.emptyList();
            for (TsKvEntry e : series) {
                long ts = e.getTs();
                String[] row = rows.computeIfAbsent(ts, t -> new String[keyList.size()]);
                row[i] = kvToString(e);
            }
        }

        // 8) Serializar
        String outFormat = format == null ? "CSV" : format.trim().toUpperCase(Locale.ROOT);
        switch (outFormat) {
            case "JSON":
                writeJson(response, keyList, rows);
                break;
            case "CSV":
            default:
                writeCsv(response, keyList, rows);
                break;
        }
    }

    private static String kvToString(TsKvEntry e) {
        Optional<KvEntry> v = e.getKv();
        if (v.isEmpty()) {
            return null;
        }
        KvEntry kv = v.get();
        if (kv instanceof StringDataEntry) {
            return ((StringDataEntry) kv).getValue();
        } else if (kv instanceof LongDataEntry) {
            return String.valueOf(((LongDataEntry) kv).getValue());
        } else if (kv instanceof DoubleDataEntry) {
            return String.valueOf(((DoubleDataEntry) kv).getValue());
        } else if (kv instanceof BooleanDataEntry) {
            return String.valueOf(((BooleanDataEntry) kv).getValue());
        } else if (kv instanceof JsonDataEntry) {
            return ((JsonDataEntry) kv).getJsonValue();
        } else {
            return kv.getValueAsString();
        }
    }

    private void writeCsv(HttpServletResponse response, List<String> keyList, NavigableMap<Long, String[]> rows) throws Exception {
        String filename = "data_export_" + System.currentTimeMillis() + ".csv";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setContentType("text/csv");

        // encabezado
        StringBuilder sb = new StringBuilder();
        sb.append("timestamp");
        for (String k : keyList) {
            sb.append(',').append(escapeCsv(k));
        }
        sb.append('\n');
        response.getWriter().write(sb.toString());
        sb.setLength(0);

        // filas
        for (Map.Entry<Long, String[]> en : rows.entrySet()) {
            long ts = en.getKey();
            String[] cols = en.getValue();
            sb.append(ts);
            for (int i = 0; i < keyList.size(); i++) {
                sb.append(',');
                String v = (cols != null && i < cols.length) ? cols[i] : null;
                if (v != null) {
                    sb.append(escapeCsv(v));
                }
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

        // Estructura: [{ts:..., k1:..., k2:...}, ...]
        StringBuilder sb = new StringBuilder();
        sb.append('[');
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
        boolean hasNewline = s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (hasComma || hasQuote || hasNewline) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        char c0 = s.charAt(0);
        if (!(Character.isDigit(c0) || c0 == '-' || c0 == '+')) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
