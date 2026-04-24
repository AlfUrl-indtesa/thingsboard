package org.thingsboard.server.service.report;

import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportDataResult;
import org.thingsboard.server.common.data.report.ReportTemplate;

public interface ReportDataService {

    ReportDataResult collectReportData(ReportTemplate template, GenerateReportRequest request);
}