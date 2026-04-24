package org.thingsboard.server.service.report;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
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
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class DefaultTbTelemetryReader implements TbTelemetryReader {

    private static final int DEFAULT_LIMIT = 1000;
    private static final String DEFAULT_ORDER_BY = "ASC";

    private final TimeseriesService timeseriesService;

    @Override
    public List<ReportMetricPoint> readTimeseries(TenantId tenantId,
                                                  EntityId entityId,
                                                  String key,
                                                  Long startTs,
                                                  Long endTs,
                                                  Long interval,
                                                  Integer limit,
                                                  ReportAggregationType aggregation,
                                                  String orderBy) {
        validate(tenantId, entityId, key, startTs, endTs);

        long effectiveInterval = resolveInterval(startTs, endTs, interval, aggregation);
        int effectiveLimit = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;
        String effectiveOrderBy = normalizeOrderBy(orderBy);
        Aggregation tbAggregation = mapAggregation(aggregation);

        ReadTsKvQuery query = new BaseReadTsKvQuery(
                key,
                startTs,
                endTs,
                effectiveInterval,
                effectiveLimit,
                tbAggregation,
                effectiveOrderBy
        );

        try {
            ListenableFuture<List<TsKvEntry>> future = timeseriesService.findAll(
                    tenantId,
                    entityId,
                    Collections.singletonList(query)
            );

            List<TsKvEntry> entries = future.get();
            return mapEntries(entries);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "Telemetry query interrupted for key: " + key,
                    e
            );
        } catch (ExecutionException e) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "Failed to read telemetry for key: " + key,
                    e
            );
        } catch (Exception e) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "Unexpected telemetry read error for key: " + key,
                    e
            );
        }
    }

    private void validate(TenantId tenantId,
                          EntityId entityId,
                          String key,
                          Long startTs,
                          Long endTs) {
        if (tenantId == null) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "TenantId is required for telemetry query"
            );
        }

        if (entityId == null) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "EntityId is required for telemetry query"
            );
        }

        if (key == null || key.isBlank()) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "Telemetry key is required"
            );
        }

        if (startTs == null || endTs == null || startTs >= endTs) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_TIME_RANGE,
                    "Invalid telemetry time range"
            );
        }
    }

    private long resolveInterval(Long startTs,
                                 Long endTs,
                                 Long interval,
                                 ReportAggregationType aggregation) {
        if (aggregation == null || aggregation == ReportAggregationType.NONE) {
            return interval != null && interval > 0 ? interval : 1L;
        }

        if (interval != null && interval > 0) {
            return interval;
        }

        long computed = endTs - startTs;
        return computed > 0 ? computed : 1L;
    }

    private String normalizeOrderBy(String orderBy) {
        if (orderBy == null || orderBy.isBlank()) {
            return DEFAULT_ORDER_BY;
        }

        String normalized = orderBy.trim().toUpperCase();
        return ("ASC".equals(normalized) || "DESC".equals(normalized))
                ? normalized
                : DEFAULT_ORDER_BY;
    }

    private Aggregation mapAggregation(ReportAggregationType aggregation) {
        if (aggregation == null) {
            return Aggregation.NONE;
        }

        switch (aggregation) {
            case AVG:
                return Aggregation.AVG;
            case MIN:
                return Aggregation.MIN;
            case MAX:
                return Aggregation.MAX;
            case SUM:
                return Aggregation.SUM;
            case COUNT:
                return Aggregation.COUNT;
            case NONE:
            default:
                return Aggregation.NONE;
        }
    }

    private List<ReportMetricPoint> mapEntries(List<TsKvEntry> entries) {
        List<ReportMetricPoint> result = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return result;
        }

        for (TsKvEntry entry : entries) {
            Double numericValue = toDouble(entry.getValueAsString());
            if (numericValue != null) {
                result.add(new ReportMetricPoint(entry.getTs(), numericValue));
            }
        }

        return result;
    }

    private Double toDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}