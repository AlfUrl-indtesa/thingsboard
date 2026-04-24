package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportTemplate;

public interface ReportRequestBuilderService {

    JsonNode buildExecutionRequest(ReportTemplate reportTemplate, GenerateReportRequest request);
}