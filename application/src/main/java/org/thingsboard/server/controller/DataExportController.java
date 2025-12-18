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
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.Aggregation;
import org.thingsboard.server.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.server.common.data.kv.ReadTsKvQuery;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.config.annotations.ApiOperation;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.permission.Operation;

import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@TbCoreComponent
@RequiredArgsConstructor
@RequestMapping("/api")
public class DataExportController extends BaseController {

    private final TimeseriesService timeseriesService;
    private final TaskScheduler taskScheduler;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public enum DataExportFormat { CSV, JSON }

    @ApiOperation(value = "Export time-series data (exportData)")
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN','CUSTOMER_USER')")
    @GetMapping(value = {"/data_export/{entityType}/{entityId}", "/data-export/{entityType}/{entityId}"})
    public void exportData(@PathVariable("entityType") String strEntityType,
                           @PathVariable("entityId") String strEntityId,
                           @RequestParam("keys") String keysParam,
                           @RequestParam("startTs") long startTs,
                           @RequestParam("endTs") long endTs,
                           @RequestParam(name = "format", required = false, defaultValue = "CSV") String formatStr,
                           HttpServletResponse response) throws Exception {

        if (!"DEVICE".equalsIgnoreCase(strEntityType)) {
            throw new ThingsboardException("Only DEVICE entityType is supported", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        long now = System.currentTimeMillis();
        if (startTs <= 0 || endTs <= 0 || startTs >= endTs) {
            throw new ThingsboardException("Invalid time range", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        if (endTs > now) endTs = now;

        DeviceId deviceId = new DeviceId(toUUID(strEntityId));
        checkDeviceId(deviceId, Operation.READ);
        TenantId tenantId = getTenantId();

        List<String> keys = parseKeys(keysParam);
        if (keys.isEmpty()) {
            throw new ThingsboardException("keys parameter is empty", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }

        final int fLimit = 100_000;
        final long fStartTs = startTs, fEndTs = endTs;
        final long fInterval = 0L;
        final Aggregation fAgg = Aggregation.NONE;

        List<ReadTsKvQuery> queries = keys.stream()
                .map(k -> new BaseReadTsKvQuery(k, fStartTs, fEndTs, fInterval, fLimit, fAgg))
                .collect(Collectors.toList());

        List<TsKvEntry> entries = timeseriesService.findAll(tenantId, deviceId, queries).get();

        DataExportFormat format = DataExportFormat.valueOf(formatStr.toUpperCase(Locale.ROOT));
        if (format == DataExportFormat.JSON) {
            Map<String, List<Map<String, Object>>> json = new LinkedHashMap<>();
            for (String k : keys) json.put(k, new ArrayList<>());
            for (TsKvEntry e : entries) {
                List<Map<String, Object>> arr = json.get(e.getKey());
                if (arr == null) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ts", e.getTs());
                row.put("value", valueAsString(e));
                arr.add(row);
            }
            byte[] jsonBytes = JacksonUtil.toString(json).getBytes(StandardCharsets.UTF_8);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"data_export_" + Instant.ofEpochMilli(now) + ".json\"");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            try (OutputStream os = response.getOutputStream()) {
                os.write(jsonBytes);
                os.flush();
            }
        } else {
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"data_export_" + Instant.ofEpochMilli(now) + ".csv\"");
            response.setContentType("text/csv; charset=UTF-8");
            try (OutputStream os = response.getOutputStream()) {
                writePivotCsv(os, keys, entries);
                os.flush();
            }
        }
    }

    @ApiOperation(value = "Bulk export as ZIP (exportBulk)")
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN','CUSTOMER_USER')")
    @GetMapping(value = {"/data_export/bulk/{entityType}", "/data-export/bulk/{entityType}"})
    public void exportBulk(@PathVariable String entityType,
                           @RequestParam("deviceIds") String deviceIdsCsv,
                           @RequestParam("keys") String keysParam,
                           @RequestParam("startTs") long startTs,
                           @RequestParam("endTs") long endTs,
                           @RequestParam(name = "format", required = false, defaultValue = "CSV") String formatStr,
                           HttpServletResponse response) throws Exception {

        if (!EntityType.DEVICE.name().equalsIgnoreCase(entityType)) {
            throw new ThingsboardException("Only DEVICE supported", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        long now = System.currentTimeMillis();
        if (startTs <= 0 || endTs <= 0 || startTs >= endTs) {
            throw new ThingsboardException("Invalid time range", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        if (endTs > now) endTs = now;

        List<String> deviceIds = Arrays.stream(deviceIdsCsv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        if (deviceIds.isEmpty()) {
            throw new ThingsboardException("deviceIds is empty", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }

        List<String> keys = parseKeys(keysParam);
        if (keys.isEmpty()) {
            throw new ThingsboardException("keys parameter is empty", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }

        byte[] zipBytes = generateZipForDevices(deviceIds, keys, startTs, endTs,
                DataExportFormat.valueOf(formatStr.toUpperCase(Locale.ROOT)));
        response.setStatus(200);
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"data_export_bulk.zip\"");
        try (OutputStream os = response.getOutputStream()) {
            os.write(zipBytes);
            os.flush();
        }
    }

    @Data
    public static class DataExportJob {
        private String jobId;
        private boolean allDevices;
        private List<String> deviceIds;
        private List<String> keys;
        private long lookbackMs;
        private String cron;
        private String email; // <- ahora es opcional: si viene vacío usamos el correo del usuario
    }

    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    @ApiOperation(value = "Schedule periodic backups via email (scheduleExport)")
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN','CUSTOMER_USER')")
    @PostMapping("/data_export/schedule")
    public void scheduleExport(@RequestBody DataExportJob job) throws Exception {
        if (job.getCron() == null || job.getCron().isBlank()) {
            throw new ThingsboardException("cron required", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        if (job.getKeys() == null || job.getKeys().isEmpty()) {
            throw new ThingsboardException("keys required", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }

        // 1) Si no llega email, usamos el del usuario autenticado
        String resolvedEmail = (job.getEmail() != null && !job.getEmail().isBlank())
                ? job.getEmail()
                : Optional.ofNullable(getCurrentUser().getEmail()).orElse(null);

        if (resolvedEmail == null || resolvedEmail.isBlank()) {
            throw new ThingsboardException("email not provided and current user has no email", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }

        TenantId tenantId = getTenantId();
        CustomerId customerId = getCurrentUser().getCustomerId();

        List<String> deviceIds;
        if (job.isAllDevices()) {
            deviceIds = findAllCustomerDeviceIds(tenantId, customerId).stream().map(UUID::toString).toList();
        } else {
            if (job.getDeviceIds() == null || job.getDeviceIds().isEmpty()) {
                throw new ThingsboardException("deviceIds required when allDevices=false", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
            }
            deviceIds = job.getDeviceIds();
        }

        final String jobId = (job.getJobId() != null && !job.getJobId().isBlank()) ? job.getJobId() : UUID.randomUUID().toString();
        ScheduledFuture<?> prev = tasks.remove(jobId);
        if (prev != null) prev.cancel(false);

        CronTrigger trigger = new CronTrigger(job.getCron());
        ScheduledFuture<?> future = taskScheduler.schedule(() -> {
            try {
                long end = System.currentTimeMillis();
                long start = end - Math.max(1, job.getLookbackMs());
                byte[] zip = generateZipForDevices(deviceIds, job.getKeys(), start, end, DataExportFormat.CSV);
                sendEmailWithAttachment(resolvedEmail, "ThingsBoard Data Backup", "Adjunto respaldo automático.", zip, "data_backup.zip");
            } catch (Exception e) {
                log.error("Data export job {} failed", jobId, e);
            }
        }, trigger);
        tasks.put(jobId, future);
    }

    private List<String> parseKeys(String keysParam) {
        if (keysParam == null || keysParam.isBlank()) return Collections.emptyList();
        return Arrays.stream(keysParam.split(","))
                .map(k -> URLDecoder.decode(k.trim(), StandardCharsets.UTF_8))
                .filter(k -> !k.isBlank()).distinct().collect(Collectors.toList());
    }

    private String valueAsString(TsKvEntry e) {
        if (e.getStrValue().isPresent())     return e.getStrValue().get();
        if (e.getLongValue().isPresent())    return String.valueOf(e.getLongValue().get());
        if (e.getDoubleValue().isPresent())  return String.valueOf(e.getDoubleValue().get());
        if (e.getBooleanValue().isPresent()) return String.valueOf(e.getBooleanValue().get());
        if (e.getJsonValue().isPresent())    return e.getJsonValue().get();
        return "";
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        boolean mustQuote = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String v = s.replace("\"", "\"\"");
        return mustQuote ? "\"" + v + "\"" : v;
    }

    /**
     * CSV pivotado: una fila por timestamp, columnas por key, incluye ISO legible.
     * Header: ts,iso,<key1>,<key2>,...
     */
    private void writePivotCsv(OutputStream os, List<String> keys, List<TsKvEntry> entries) throws Exception {
        // BOM UTF-8 para Excel
        os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        // Header
        StringBuilder header = new StringBuilder();
        header.append("ts,iso");
        for (String k : keys) {
            header.append(',').append(escapeCsv(k));
        }
        header.append('\n');
        os.write(header.toString().getBytes(StandardCharsets.UTF_8));

        // Filas por timestamp (orden ascendente)
        // ts -> (key -> value)
        TreeMap<Long, Map<String, String>> byTs = new TreeMap<>();
        // Set para filtrar rápido (evita O(n) por contains)
        Set<String> keySet = new HashSet<>(keys);

        for (TsKvEntry e : entries) {
            if (!keySet.contains(e.getKey())) continue;
            byTs.computeIfAbsent(e.getTs(), t -> new HashMap<>())
                    .put(e.getKey(), valueAsString(e));
        }

        StringBuilder row = new StringBuilder(512);
        for (Map.Entry<Long, Map<String, String>> it : byTs.entrySet()) {
            long ts = it.getKey();
            Map<String, String> rowMap = it.getValue();

            row.setLength(0);
            row.append(ts).append(',').append(escapeCsv(Instant.ofEpochMilli(ts).toString()));

            for (String k : keys) {
                row.append(',');
                String v = rowMap.get(k);
                if (v != null && !v.isEmpty()) {
                    row.append(escapeCsv(v));
                }
            }
            row.append('\n');
            os.write(row.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private byte[] generateZipForDevices(List<String> deviceIds, List<String> keys,
                                         long startTs, long endTs, DataExportFormat format) throws Exception {
        TenantId tenantId = getTenantId();
        final int fLimit = 100_000;
        final long fStartTs = startTs, fEndTs = endTs;
        final long fInterval = 0L;
        final Aggregation fAgg = Aggregation.NONE;

        try (var baos = new java.io.ByteArrayOutputStream();
             var zip = new ZipOutputStream(baos)) {

            for (String devIdStr : deviceIds) {
                DeviceId deviceId = new DeviceId(toUUID(devIdStr));
                checkDeviceId(deviceId, Operation.READ);

                List<ReadTsKvQuery> queries = keys.stream()
                        .map(k -> new BaseReadTsKvQuery(k, fStartTs, fEndTs, fInterval, fLimit, fAgg))
                        .collect(Collectors.toList());
                List<TsKvEntry> entries = timeseriesService.findAll(tenantId, deviceId, queries).get();

                String entryName = "device_" + devIdStr + (format == DataExportFormat.JSON ? ".json" : ".csv");
                zip.putNextEntry(new ZipEntry(entryName));

                if (format == DataExportFormat.JSON) {
                    Map<String, List<Map<String, Object>>> json = new LinkedHashMap<>();
                    for (String k : keys) json.put(k, new ArrayList<>());
                    for (TsKvEntry e : entries) {
                        List<Map<String, Object>> arr = json.get(e.getKey());
                        if (arr == null) continue;
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("ts", e.getTs());
                        row.put("value", valueAsString(e));
                        arr.add(row);
                    }
                    byte[] bytes = JacksonUtil.toString(json).getBytes(StandardCharsets.UTF_8);
                    zip.write(bytes);
                } else {
                    // CSV pivotado dentro del ZIP (con BOM)
                    writePivotCsv(zip, keys, entries);
                }

                zip.closeEntry();
            }
            zip.finish();
            return baos.toByteArray();
        }
    }

    private void sendEmailWithAttachment(String to, String subject, String body, byte[] data, String filename) throws Exception {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("JavaMailSender no está disponible; se omite el envío de correo de backup.");
            return;
        }
        var mime = mailSender.createMimeMessage();
        var helper = new MimeMessageHelper(mime, true, "UTF-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, false);
        helper.addAttachment(filename, new ByteArrayResource(data));
        mailSender.send(mime);
    }

    private List<UUID> findAllCustomerDeviceIds(TenantId tenantId, CustomerId customerId) {
        List<UUID> result = new ArrayList<>();
        PageLink pageLink = new PageLink(100);
        while (true) {
            PageData<Device> page = deviceService.findDevicesByTenantIdAndCustomerId(tenantId, customerId, pageLink);
            for (Device d : page.getData()) {
                result.add(d.getId().getId());
            }
            if (page.hasNext()) {
                pageLink = pageLink.nextPageLink();
            } else break;
        }
        return result;
    }
}
