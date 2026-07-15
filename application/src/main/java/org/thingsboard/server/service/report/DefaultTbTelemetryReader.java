/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.Aggregation;
import org.thingsboard.server.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.server.common.data.kv.ReadTsKvQuery;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.report.ReportAggregationType;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportMetricPoint;
import org.thingsboard.server.dao.timeseries.TimeseriesService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbTelemetryReader implements TbTelemetryReader {

    private static final String DEFAULT_ORDER_BY = "ASC";

    private static final int DEFAULT_PAGE_SIZE = 1000;
    private static final int DEFAULT_MAX_POINTS_PER_SERIES = 100_000;
    private static final int DEFAULT_MAX_PAGES = 1000;

    private static final int MAX_ALLOWED_PAGE_SIZE = 10_000;
    private static final int MAX_ALLOWED_POINTS_PER_SERIES = 1_000_000;
    private static final int MAX_ALLOWED_PAGES = 10_000;

    private final TimeseriesService timeseriesService;

    /**
     * Cantidad de registros solicitados por consulta interna.
     */
    @Value("${report.telemetry.page-size:1000}")
    private int configuredPageSize = DEFAULT_PAGE_SIZE;

    /**
     * Protección de memoria. Cuando ReportTelemetryQuery.limit es null,
     * éste es el máximo total de puntos permitidos por serie.
     */
    @Value("${report.telemetry.max-points-per-series:100000}")
    private int configuredMaxPointsPerSeries = DEFAULT_MAX_POINTS_PER_SERIES;

    /**
     * Protección adicional contra ciclos de paginación excesivos.
     */
    @Value("${report.telemetry.max-pages:1000}")
    private int configuredMaxPages = DEFAULT_MAX_PAGES;

    @Override
    public List<ReportMetricPoint> readTimeseries(
            TenantId tenantId,
            EntityId entityId,
            String key,
            Long startTs,
            Long endTs,
            Long interval,
            Integer limit,
            ReportAggregationType aggregation,
            String orderBy) {

        validate(
                tenantId,
                entityId,
                key,
                startTs,
                endTs);

        String effectiveOrderBy = normalizeOrderBy(orderBy);

        ReportAggregationType effectiveAggregation = aggregation != null
                ? aggregation
                : ReportAggregationType.NONE;

        long effectiveInterval = resolveInterval(
                startTs,
                endTs,
                interval,
                effectiveAggregation);

        Aggregation tbAggregation = mapAggregation(effectiveAggregation);

        /*
         * Las series sin agregación necesitan paginación real.
         * Son las utilizadas para estadísticas, gráficas,
         * análisis avanzado y comparación de periodos.
         */
        if (effectiveAggregation == ReportAggregationType.NONE) {
            return readRawTimeseriesPaged(
                    tenantId,
                    entityId,
                    key,
                    startTs,
                    endTs,
                    effectiveInterval,
                    limit,
                    effectiveOrderBy);
        }

        /*
         * Las consultas agregadas normalmente producen muchos menos
         * puntos. Se mantienen como una sola consulta para no modificar
         * la alineación temporal de los buckets.
         */
        return readAggregatedTimeseries(
                tenantId,
                entityId,
                key,
                startTs,
                endTs,
                effectiveInterval,
                limit,
                tbAggregation,
                effectiveOrderBy);
    }

    private List<ReportMetricPoint> readRawTimeseriesPaged(
            TenantId tenantId,
            EntityId entityId,
            String key,
            long startTs,
            long endTs,
            long interval,
            Integer requestedLimit,
            String orderBy) {

        int totalLimit = resolveTotalLimit(requestedLimit);

        int pageSize = Math.min(
                resolvePageSize(),
                totalLimit);

        int maximumPages = resolveMaxPages();
        log.warn(
                "REPORT_PAGING_CONFIG: entityId={}, key={}, " +
                        "requestedLimit={}, totalLimit={}, pageSize={}, " +
                        "maxPages={}, range=[{},{}], orderBy={}",
                entityId,
                key,
                requestedLimit,
                totalLimit,
                pageSize,
                maximumPages,
                startTs,
                endTs,
                orderBy);

        Map<Long, ReportMetricPoint> pointsByTimestamp = new HashMap<>();

        int pageCount = 0;

        if ("DESC".equals(orderBy)) {
            long cursorEndTs = endTs;

            while (cursorEndTs > startTs
                    && pointsByTimestamp.size() < totalLimit
                    && pageCount < maximumPages) {

                int remaining = totalLimit -
                        pointsByTimestamp.size();

                int currentPageSize = Math.min(pageSize, remaining);

                List<TsKvEntry> entries = executeQuery(
                        tenantId,
                        entityId,
                        key,
                        startTs,
                        cursorEndTs,
                        interval,
                        currentPageSize,
                        Aggregation.NONE,
                        "DESC");

                pageCount++;

                if (entries == null || entries.isEmpty()) {
                    break;
                }

                appendNumericEntries(
                        entries,
                        pointsByTimestamp,
                        totalLimit);

                long minimumTimestamp = findMinimumTimestamp(entries);

                /*
                 * Protección contra una consulta que no avance.
                 */
                if (minimumTimestamp == Long.MAX_VALUE
                        || minimumTimestamp >= cursorEndTs
                        || minimumTimestamp <= startTs) {
                    break;
                }

                if (entries.size() < currentPageSize) {
                    break;
                }

                cursorEndTs = minimumTimestamp - 1L;
            }
        } else {
            long cursorStartTs = startTs;

            while (cursorStartTs < endTs
                    && pointsByTimestamp.size() < totalLimit
                    && pageCount < maximumPages) {

                int remaining = totalLimit -
                        pointsByTimestamp.size();

                int currentPageSize = Math.min(pageSize, remaining);

                List<TsKvEntry> entries = executeQuery(
                        tenantId,
                        entityId,
                        key,
                        cursorStartTs,
                        endTs,
                        interval,
                        currentPageSize,
                        Aggregation.NONE,
                        "ASC");

                pageCount++;

                if (entries == null || entries.isEmpty()) {
                    break;
                }

                appendNumericEntries(
                        entries,
                        pointsByTimestamp,
                        totalLimit);

                long maximumTimestamp = findMaximumTimestamp(entries);

                /*
                 * Protección contra una consulta que no avance.
                 */
                if (maximumTimestamp == Long.MIN_VALUE
                        || maximumTimestamp < cursorStartTs
                        || maximumTimestamp >= endTs
                        || maximumTimestamp == Long.MAX_VALUE) {
                    break;
                }

                if (entries.size() < currentPageSize) {
                    break;
                }

                long nextStartTs = maximumTimestamp + 1L;

                if (nextStartTs <= cursorStartTs) {
                    break;
                }

                cursorStartTs = nextStartTs;
            }
        }

        List<ReportMetricPoint> result = sortPoints(
                new ArrayList<>(
                        pointsByTimestamp.values()),
                orderBy);

        if (pageCount > 1) {
            log.info(
                    "Report telemetry paged read completed: " +
                            "entityId={}, key={}, range=[{},{}], " +
                            "pages={}, points={}, orderBy={}",
                    entityId,
                    key,
                    startTs,
                    endTs,
                    pageCount,
                    result.size(),
                    orderBy);
        }

        if (result.size() >= totalLimit) {
            log.warn(
                    "Report telemetry reached point cap: " +
                            "entityId={}, key={}, range=[{},{}], " +
                            "points={}, cap={}. Additional points may have been omitted.",
                    entityId,
                    key,
                    startTs,
                    endTs,
                    result.size(),
                    totalLimit);
        }

        if (pageCount >= maximumPages) {
            log.warn(
                    "Report telemetry reached page cap: " +
                            "entityId={}, key={}, pages={}, maxPages={}",
                    entityId,
                    key,
                    pageCount,
                    maximumPages);
        }

        return result;
    }

    private List<ReportMetricPoint> readAggregatedTimeseries(
            TenantId tenantId,
            EntityId entityId,
            String key,
            long startTs,
            long endTs,
            long interval,
            Integer requestedLimit,
            Aggregation aggregation,
            String orderBy) {

        int effectiveLimit = resolveTotalLimit(requestedLimit);

        List<TsKvEntry> entries = executeQuery(
                tenantId,
                entityId,
                key,
                startTs,
                endTs,
                interval,
                effectiveLimit,
                aggregation,
                orderBy);

        Map<Long, ReportMetricPoint> pointsByTimestamp = new HashMap<>();

        appendNumericEntries(
                entries,
                pointsByTimestamp,
                effectiveLimit);

        List<ReportMetricPoint> result = sortPoints(
                new ArrayList<>(
                        pointsByTimestamp.values()),
                orderBy);

        if (entries != null
                && entries.size() >= effectiveLimit) {
            log.warn(
                    "Aggregated report telemetry reached point cap: " +
                            "entityId={}, key={}, points={}, cap={}",
                    entityId,
                    key,
                    result.size(),
                    effectiveLimit);
        }

        return result;
    }

    private List<TsKvEntry> executeQuery(
            TenantId tenantId,
            EntityId entityId,
            String key,
            long startTs,
            long endTs,
            long interval,
            int limit,
            Aggregation aggregation,
            String orderBy) {

        ReadTsKvQuery query = new BaseReadTsKvQuery(
                key,
                startTs,
                endTs,
                interval,
                limit,
                aggregation,
                orderBy);

        try {
            ListenableFuture<List<TsKvEntry>> future = timeseriesService.findAll(
                    tenantId,
                    entityId,
                    Collections.singletonList(query));

            List<TsKvEntry> entries = future.get();

            return entries != null
                    ? entries
                    : Collections.emptyList();

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "Telemetry query interrupted for key: " + key,
                    exception);

        } catch (ExecutionException exception) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "Failed to read telemetry for key: " + key,
                    exception);

        } catch (RuntimeException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "Unexpected telemetry read error for key: " + key,
                    exception);
        }
    }

    private void appendNumericEntries(
            List<TsKvEntry> entries,
            Map<Long, ReportMetricPoint> pointsByTimestamp,
            int totalLimit) {

        if (entries == null || entries.isEmpty()) {
            return;
        }

        for (TsKvEntry entry : entries) {
            if (entry == null
                    || pointsByTimestamp.size() >= totalLimit) {
                break;
            }

            Double numericValue = toDouble(
                    entry.getValueAsString());

            if (numericValue == null) {
                continue;
            }

            long timestamp = entry.getTs();

            /*
             * Una clave sólo debe tener un valor por timestamp.
             * putIfAbsent también elimina duplicados entre páginas.
             */
            pointsByTimestamp.putIfAbsent(
                    timestamp,
                    new ReportMetricPoint(
                            timestamp,
                            numericValue));
        }
    }

    private long findMaximumTimestamp(
            List<TsKvEntry> entries) {

        long maximum = Long.MIN_VALUE;

        for (TsKvEntry entry : entries) {
            if (entry != null) {
                maximum = Math.max(
                        maximum,
                        entry.getTs());
            }
        }

        return maximum;
    }

    private long findMinimumTimestamp(
            List<TsKvEntry> entries) {

        long minimum = Long.MAX_VALUE;

        for (TsKvEntry entry : entries) {
            if (entry != null) {
                minimum = Math.min(
                        minimum,
                        entry.getTs());
            }
        }

        return minimum;
    }

    private List<ReportMetricPoint> sortPoints(
            List<ReportMetricPoint> points,
            String orderBy) {

        Comparator<ReportMetricPoint> comparator = Comparator.comparingLong(
                ReportMetricPoint::getTs);

        if ("DESC".equals(orderBy)) {
            comparator = comparator.reversed();
        }

        points.sort(comparator);

        return points;
    }

    private void validate(
            TenantId tenantId,
            EntityId entityId,
            String key,
            Long startTs,
            Long endTs) {

        if (tenantId == null) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "TenantId is required for telemetry query");
        }

        if (entityId == null) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "EntityId is required for telemetry query");
        }

        if (key == null || key.isBlank()) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "Telemetry key is required");
        }

        if (startTs == null
                || endTs == null
                || startTs >= endTs) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_TIME_RANGE,
                    "Invalid telemetry time range");
        }
    }

    private long resolveInterval(
            Long startTs,
            Long endTs,
            Long interval,
            ReportAggregationType aggregation) {

        if (aggregation == null
                || aggregation == ReportAggregationType.NONE) {
            return interval != null && interval > 0
                    ? interval
                    : 1L;
        }

        if (interval != null && interval > 0) {
            return interval;
        }

        long computed = endTs - startTs;

        return computed > 0
                ? computed
                : 1L;
    }

    private int resolvePageSize() {
        int value = configuredPageSize > 0
                ? configuredPageSize
                : DEFAULT_PAGE_SIZE;

        return Math.min(
                MAX_ALLOWED_PAGE_SIZE,
                Math.max(1, value));
    }

    private int resolveTotalLimit(
            Integer requestedLimit) {

        int configuredMaximum = configuredMaxPointsPerSeries > 0
                ? configuredMaxPointsPerSeries
                : DEFAULT_MAX_POINTS_PER_SERIES;

        configuredMaximum = Math.min(
                MAX_ALLOWED_POINTS_PER_SERIES,
                Math.max(1, configuredMaximum));

        if (requestedLimit != null
                && requestedLimit > 0) {
            return Math.min(
                    requestedLimit,
                    configuredMaximum);
        }

        return configuredMaximum;
    }

    private int resolveMaxPages() {
        int value = configuredMaxPages > 0
                ? configuredMaxPages
                : DEFAULT_MAX_PAGES;

        return Math.min(
                MAX_ALLOWED_PAGES,
                Math.max(1, value));
    }

    private String normalizeOrderBy(
            String orderBy) {

        if (orderBy == null
                || orderBy.isBlank()) {
            return DEFAULT_ORDER_BY;
        }

        String normalized = orderBy.trim().toUpperCase();

        return "DESC".equals(normalized)
                ? "DESC"
                : "ASC";
    }

    private Aggregation mapAggregation(
            ReportAggregationType aggregation) {

        if (aggregation == null) {
            return Aggregation.NONE;
        }

        return switch (aggregation) {
            case AVG -> Aggregation.AVG;
            case MIN -> Aggregation.MIN;
            case MAX -> Aggregation.MAX;
            case SUM -> Aggregation.SUM;
            case COUNT -> Aggregation.COUNT;
            case NONE -> Aggregation.NONE;
        };
    }

    private Double toDouble(
            String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}