package org.thingsboard.server.service.report;

import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportTemplate;

public interface ReportValidationService {

    void validateTemplateForSave(ReportTemplate reportTemplate);

    void validateTemplateForExecution(ReportTemplate reportTemplate);

    void validateGenerateRequest(GenerateReportRequest request);
}