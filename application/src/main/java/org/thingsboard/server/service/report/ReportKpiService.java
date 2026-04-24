package org.thingsboard.server.service.report;

import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportKpi;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTemplate;

import java.util.List;

public interface ReportKpiService {

    List<ReportKpi> buildKpis(ReportTemplate template,
                              GenerateReportRequest request,
                              List<ReportTargetEntity> entities);
}