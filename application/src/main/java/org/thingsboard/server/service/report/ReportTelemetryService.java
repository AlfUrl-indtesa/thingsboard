package org.thingsboard.server.service.report;

import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTelemetryQuery;
import org.thingsboard.server.common.data.report.ReportTimeSeries;

import java.util.List;

public interface ReportTelemetryService {

    ReportTimeSeries findSeries(TenantId tenantId,
                                ReportTargetEntity entity,
                                ReportTelemetryQuery query);

    List<ReportTimeSeries> findSeries(TenantId tenantId,
                                      List<ReportTargetEntity> entities,
                                      List<ReportTelemetryQuery> queries);
}