package org.thingsboard.server.service.report;

import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportTimeSeries;

import java.util.List;

public interface ReportChartService {

    List<ReportTimeSeries> buildTimeSeries(ReportTemplate template,
                                           GenerateReportRequest request,
                                           List<ReportTargetEntity> entities);
}