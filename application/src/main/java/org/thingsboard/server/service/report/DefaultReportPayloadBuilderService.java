/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportDataResult;
import org.thingsboard.server.common.data.report.ReportKpi;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportVariableMetadata;

import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DefaultReportPayloadBuilderService implements ReportPayloadBuilderService {

    private final ObjectMapper objectMapper;
    private final ReportDataService reportDataService;
    private final ReportVariableMetadataService variableMetadataService;

    @Override
    public JsonNode buildPayload(ReportTemplate reportTemplate, GenerateReportRequest request) {
        validateInputs(reportTemplate, request);

        ReportDataResult dataResult =
                reportDataService.collectReportData(
                        reportTemplate,
                        request);

        if (dataResult == null) {
            throw new ReportServiceException(
                    org.thingsboard.server.common.data.report.ReportErrorCode.PAYLOAD_BUILD_FAILED,
                    "Report data collection returned no result");
        }

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

    private void validateInputs(
            ReportTemplate reportTemplate,
            GenerateReportRequest request) {

        if (reportTemplate == null) {
            throw new ReportServiceException(
                    org.thingsboard.server.common.data.report.ReportErrorCode.TEMPLATE_NOT_FOUND,
                    "Report template is required");
        }

        if (request == null
                || request.getStartTs() == null
                || request.getEndTs() == null
                || request.getStartTs() >= request.getEndTs()) {
            throw new ReportServiceException(
                    org.thingsboard.server.common.data.report.ReportErrorCode.INVALID_TIME_RANGE,
                    "Valid report time range is required");
        }
    }

    private JsonNode buildMeta(ReportTemplate reportTemplate) {
        ObjectNode meta = objectMapper.createObjectNode();

        if (reportTemplate.getId() != null) {
            meta.put("templateId", reportTemplate.getId().toString());
        }

        meta.put("templateName", reportTemplate.getName());

        if (reportTemplate.getType() != null) {
            meta.put("reportType", reportTemplate.getType().name());
        }

        meta.put("generatedAt", Instant.now().toString());

        if (reportTemplate.getOutputFormat() != null) {
            meta.put("outputFormat", reportTemplate.getOutputFormat().name());
        }

        return meta;
    }

    private JsonNode buildPeriod(GenerateReportRequest request) {
        ObjectNode period = objectMapper.createObjectNode();

        period.put("startTs", request.getStartTs());
        period.put("endTs", request.getEndTs());

        if (hasText(request.getTimezone())) {
            period.put("timezone", request.getTimezone());
        }

        if (hasText(request.getLocale())) {
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
            dataResult.getKpis().forEach(kpi -> kpisNode.add(buildKpiNode(kpi)));
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
            dataResult.getKpis().forEach(kpi -> kpisNode.add(buildKpiNode(kpi)));
        }

        data.set("kpis", kpisNode);

        ArrayNode seriesNode = objectMapper.createArrayNode();

        if (dataResult.getTimeSeries() != null) {
            dataResult.getTimeSeries().forEach(series -> {
                JsonNode node = objectMapper.valueToTree(series);
                seriesNode.add(enrichVariableNode(node));
            });
        }

        data.set("timeSeries", seriesNode);

        ArrayNode tablesNode = objectMapper.createArrayNode();

        if (dataResult.getTables() != null) {
            dataResult.getTables().forEach(table -> {
                JsonNode node = objectMapper.valueToTree(table);
                tablesNode.add(enrichVariableNode(node));
            });
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
                .filter(section -> section != null
                        && Boolean.TRUE.equals(section.getVisible()))
                .sorted(
                        Comparator.comparing(
                                ReportSectionConfig::getOrder,
                                Comparator.nullsLast(
                                        Integer::compareTo)))
                .forEach(section -> sections.add(buildSectionNode(section)));

        return sections;
    }

    private JsonNode buildSectionNode(ReportSectionConfig section) {
        ObjectNode node = objectMapper.createObjectNode();

        node.put("key", section.getKey());

        if (section.getType() != null) {
            node.put("type", section.getType().name());
        }

        node.put("title", firstNotBlank(section.getTitle(), humanize(section.getKey())));
        node.put("order", section.getOrder());
        node.put("visible", section.getVisible());
        node.put("pageBreakBefore", section.getPageBreakBefore());

        if (section.getConfig() != null) {
            node.set("config", enrichVariableNode(section.getConfig()));
        }

        return node;
    }

    private JsonNode buildKpiNode(ReportKpi kpi) {
        ObjectNode node = objectMapper.createObjectNode();

        String key = kpi.getKey();
        String unit = resolveUnit(key, kpi.getUnit());
        String label = resolveLabel(key, kpi.getLabel(), unit);

        /*
         * key conserva la llave técnica real.
         * label/displayName/title/name son campos visuales para PDF/UI.
         */
        node.put("key", key);
        node.put("label", label);
        node.put("displayName", label);
        node.put("title", label);
        node.put("name", label);

        if (hasText(kpi.getEntityName())) {
            node.put("entityName", kpi.getEntityName());
        }

        if (hasText(unit)) {
            node.put("unit", unit);
        }

        if (kpi.getValue() != null) {
            node.put("value", kpi.getValue());
        } else {
            node.putNull("value");
        }

        if (hasText(kpi.getFormattedValue())) {
            node.put("formattedValue", kpi.getFormattedValue());
        }

        if (kpi.getAggregation() != null) {
            node.set("aggregation", objectMapper.valueToTree(kpi.getAggregation()));
        }

        if (kpi.getStatus() != null) {
            node.set("status", objectMapper.valueToTree(kpi.getStatus()));
        }

        return node;
    }

    private JsonNode enrichVariableNode(JsonNode source) {
        if (source == null || source.isNull()) {
            return objectMapper.nullNode();
        }

        if (source.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();

            for (JsonNode item : source) {
                array.add(enrichVariableNode(item));
            }

            return array;
        }

        if (!source.isObject()) {
            return source.deepCopy();
        }

        ObjectNode target = objectMapper.createObjectNode();
        ObjectNode sourceObject = (ObjectNode) source;

        Iterator<Map.Entry<String, JsonNode>> fields = sourceObject.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            target.set(field.getKey(), enrichVariableNode(field.getValue()));
        }

        enrichObjectWithDisplayFields(target);

        return target;
    }

    private void enrichObjectWithDisplayFields(ObjectNode node) {
        String key = textValue(node, "key");

        if (!hasText(key)) {
            return;
        }

        String providedLabel = firstNotBlank(
                textValue(node, "label"),
                textValue(node, "displayName"),
                textValue(node, "title"),
                textValue(node, "name"));

        String providedUnit = textValue(node, "unit");

        String unit = resolveUnit(key, providedUnit);
        String label = resolveLabel(key, providedLabel, unit);

        if (!hasText(label)) {
            return;
        }

        /*
         * No reemplazamos key porque puede usarse internamente para lookup.
         * Enriquecemos campos visuales para que el renderer no tenga que usar key.
         */
        node.put("label", label);
        node.put("displayName", label);

        if (!hasText(textValue(node, "title")) || looksTechnical(textValue(node, "title"))) {
            node.put("title", label);
        }

        if (!hasText(textValue(node, "name")) || looksTechnical(textValue(node, "name"))) {
            node.put("name", label);
        }

        if (hasText(unit)) {
            node.put("unit", unit);
        }
    }

    private String resolveLabel(String key, String providedLabel, String providedUnit) {
        ReportVariableMetadata metadata = variableMetadataService.resolve(key, providedLabel, providedUnit);

        return firstNotBlank(
                metadata != null ? metadata.getLabel() : null,
                providedLabel,
                humanize(key),
                key,
                "Variable");
    }

    private String resolveUnit(String key, String providedUnit) {
        ReportVariableMetadata metadata = variableMetadataService.resolve(key, null, providedUnit);

        return firstNotBlank(
                providedUnit,
                metadata != null ? metadata.getUnit() : null,
                "");
    }

    private static String textValue(ObjectNode node, String field) {
        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        if (value.isTextual()) {
            return value.asText();
        }

        return value.asText(null);
    }

    private static String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }

        return "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean looksTechnical(String value) {
        if (!hasText(value)) {
            return false;
        }

        String text = value.trim();

        return text.contains("_")
                || text.contains(".")
                || text.matches(".*[A-Za-z]+[0-9]+.*");
    }

    private static String humanize(String value) {
        if (!hasText(value)) {
            return "Variable";
        }

        String text = value
                .replace("_", " ")
                .replace("-", " ")
                .replace("/", " / ")
                .replaceAll("\\s+", " ")
                .trim();

        if (!hasText(text)) {
            return "Variable";
        }

        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
