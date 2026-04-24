package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportTemplate;

@Service
@RequiredArgsConstructor
public class DefaultReportRequestBuilderService implements ReportRequestBuilderService {

    private final ObjectMapper objectMapper;

    @Override
    public JsonNode buildExecutionRequest(ReportTemplate reportTemplate, GenerateReportRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("templateId", reportTemplate.getId().toString());
        root.put("templateName", reportTemplate.getName());
        root.put("reportType", reportTemplate.getType().name());
        root.put("startTs", request.getStartTs());
        root.put("endTs", request.getEndTs());

        if (request.getLocale() != null) {
            root.put("locale", request.getLocale());
        }
        if (request.getTimezone() != null) {
            root.put("timezone", request.getTimezone());
        }
        if (request.getEntityIds() != null) {
            root.set("entityIds", objectMapper.valueToTree(request.getEntityIds()));
        }

        root.set("scope", objectMapper.valueToTree(reportTemplate.getEntityFilter()));
        root.set("generationOptions", objectMapper.valueToTree(reportTemplate.getGenerationOptions()));

        return root;
    }
}