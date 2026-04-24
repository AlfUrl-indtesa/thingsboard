package org.thingsboard.server.service.report;

import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportAggregationType;
import org.thingsboard.server.common.data.report.ReportMetricPoint;

import java.util.List;

public interface TbTelemetryReader {

    List<ReportMetricPoint> readTimeseries(TenantId tenantId,
                                           EntityId entityId,
                                           String key,
                                           Long startTs,
                                           Long endTs,
                                           Long interval,
                                           Integer limit,
                                           ReportAggregationType aggregation,
                                           String orderBy);
}