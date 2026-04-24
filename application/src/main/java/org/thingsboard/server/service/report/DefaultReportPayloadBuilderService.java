package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportDataResult;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportTemplate;

import java.time.Instant;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class DefaultReportPayloadBuilderService implements ReportPayloadBuilderService {

    private final ObjectMapper objectMapper;
    private final ReportDataService reportDataService;

    @Override
    public JsonNode buildPayload(ReportTemplate reportTemplate, GenerateReportRequest request) {
        ReportDataResult dataResult = reportDataService.collectReportData(reportTemplate, request);

        ObjectNode root = objectMapper.createObjectNode();

        root.set("meta", buildMeta(reportTemplate));
        root.set("period", buildPeriod(request));
        root.set("branding", buildBranding(reportTemplate));
        root.set("context", buildContext(reportTemplate, dataResult));
        root.set("summary", buildSummary(dataResult));
        root.set("data", buildData(dataResult));
        root.set("sections", buildSections(reportTemplate));

        return root;
    }

    private JsonNode buildMeta(ReportTemplate reportTemplate) {
        ObjectNode meta = objectMapper.createObjectNode();
        if (reportTemplate.getId() != null) {
            meta.put("templateId", reportTemplate.getId().toString());
        }
        meta.put("templateName", reportTemplate.getName());
        meta.put("reportType", reportTemplate.getType().name());
        meta.put("generatedAt", Instant.now().toString());
        meta.put("outputFormat", reportTemplate.getOutputFormat().name());
        return meta;
    }

    private JsonNode buildPeriod(GenerateReportRequest request) {
        ObjectNode period = objectMapper.createObjectNode();
        period.put("startTs", request.getStartTs());
        period.put("endTs", request.getEndTs());

        if (request.getTimezone() != null) {
            period.put("timezone", request.getTimezone());
        }
        if (request.getLocale() != null) {
            period.put("locale", request.getLocale());
        }

        return period;
    }

    private JsonNode buildBranding(ReportTemplate reportTemplate) {
        if (reportTemplate.getBranding() == null) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.valueToTree(reportTemplate.getBranding());
    }

    private JsonNode buildContext(ReportTemplate reportTemplate, ReportDataResult dataResult) {
        ObjectNode context = objectMapper.createObjectNode();

        if (reportTemplate.getCustomerId() != null) {
            context.put("customerId", reportTemplate.getCustomerId().getId().toString());
        }

        ArrayNode entitiesNode = objectMapper.createArrayNode();
        if (dataResult.getEntities() != null) {
            dataResult.getEntities().forEach(entity -> entitiesNode.add(objectMapper.valueToTree(entity)));
        }
        context.set("entities", entitiesNode);

        return context;
    }

    private JsonNode buildSummary(ReportDataResult dataResult) {
        ObjectNode summary = objectMapper.createObjectNode();

        ArrayNode kpisNode = objectMapper.createArrayNode();
        if (dataResult.getKpis() != null) {
            dataResult.getKpis().forEach(kpi -> kpisNode.add(objectMapper.valueToTree(kpi)));
        }
        summary.set("kpis", kpisNode);

        ArrayNode observationsNode = objectMapper.createArrayNode();
        if (dataResult.getObservations() != null) {
            dataResult.getObservations().forEach(observationsNode::add);
        }
        summary.set("observations", observationsNode);

        return summary;
    }

    private JsonNode buildData(ReportDataResult dataResult) {
        ObjectNode data = objectMapper.createObjectNode();

        ArrayNode entitiesNode = objectMapper.createArrayNode();
        if (dataResult.getEntities() != null) {
            dataResult.getEntities().forEach(entity -> entitiesNode.add(objectMapper.valueToTree(entity)));
        }
        data.set("entities", entitiesNode);

        ArrayNode kpisNode = objectMapper.createArrayNode();
        if (dataResult.getKpis() != null) {
            dataResult.getKpis().forEach(kpi -> kpisNode.add(objectMapper.valueToTree(kpi)));
        }
        data.set("kpis", kpisNode);

        ArrayNode seriesNode = objectMapper.createArrayNode();
        if (dataResult.getTimeSeries() != null) {
            dataResult.getTimeSeries().forEach(series -> seriesNode.add(objectMapper.valueToTree(series)));
        }
        data.set("timeSeries", seriesNode);

        ArrayNode tablesNode = objectMapper.createArrayNode();
        if (dataResult.getTables() != null) {
            dataResult.getTables().forEach(table -> tablesNode.add(objectMapper.valueToTree(table)));
        }
        data.set("tables", tablesNode);

        ArrayNode alarmsNode = objectMapper.createArrayNode();
        if (dataResult.getAlarms() != null) {
            dataResult.getAlarms().forEach(alarm -> alarmsNode.add(objectMapper.valueToTree(alarm)));
        }
        data.set("alarms", alarmsNode);

        ArrayNode observationsNode = objectMapper.createArrayNode();
        if (dataResult.getObservations() != null) {
            dataResult.getObservations().forEach(observationsNode::add);
        }
        data.set("observations", observationsNode);

        return data;
    }

    private JsonNode buildSections(ReportTemplate reportTemplate) {
        ArrayNode sections = objectMapper.createArrayNode();

        if (reportTemplate.getSections() == null || reportTemplate.getSections().isEmpty()) {
            return sections;
        }

        reportTemplate.getSections().stream()
                .filter(section -> Boolean.TRUE.equals(section.getVisible()))
                .sorted(Comparator.comparing(ReportSectionConfig::getOrder))
                .forEach(section -> sections.add(buildSectionNode(section)));

        return sections;
    }

    private JsonNode buildSectionNode(ReportSectionConfig section) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("key", section.getKey());
        node.put("type", section.getType().name());
        node.put("title", section.getTitle());
        node.put("order", section.getOrder());
        node.put("visible", section.getVisible());
        node.put("pageBreakBefore", section.getPageBreakBefore());

        if (section.getConfig() != null) {
            node.set("config", section.getConfig());
        }

        return node;
    }
}