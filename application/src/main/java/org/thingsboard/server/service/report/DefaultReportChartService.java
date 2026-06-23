package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportChartQuery;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportSectionType;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTelemetryQuery;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportTimeSeries;
import org.thingsboard.server.common.data.report.ReportVariableMetadata;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultReportChartService implements ReportChartService {

    private final ReportTelemetryService reportTelemetryService;
    private final ObjectMapper objectMapper;
    private final ReportVariableMetadataService variableMetadataService;

    @Override
    public List<ReportTimeSeries> buildTimeSeries(ReportTemplate template,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities) {
        List<ReportTimeSeries> result = new ArrayList<>();

        if (template == null || template.getSections() == null || template.getSections().isEmpty()) {
            return result;
        }

        TenantId tenantId = template.getTenantId();

        for (ReportSectionConfig section : template.getSections()) {
            if (section.getType() != ReportSectionType.CHART || !Boolean.TRUE.equals(section.getVisible())) {
                continue;
            }

            List<ReportChartQuery> queries = extractChartQueries(section.getConfig());
            if (queries.isEmpty()) {
                continue;
            }

            for (ReportChartQuery query : queries) {
                List<ReportTimeSeries> series = buildSeriesForQuery(tenantId, request, entities, query);
                result.addAll(series);
            }
        }

        return result;
    }

    private List<ReportTimeSeries> buildSeriesForQuery(TenantId tenantId,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities,
            ReportChartQuery query) {
        List<ReportTimeSeries> result = new ArrayList<>();

        if (entities == null || entities.isEmpty()) {
            return result;
        }

        ReportTelemetryQuery telemetryQuery = buildTelemetryQuery(query, request);

        for (ReportTargetEntity entity : entities) {
            ReportTimeSeries series = reportTelemetryService.findSeries(tenantId, entity, telemetryQuery);
            if (series != null) {
                result.add(series);
            }
        }

        return result;
    }

    private ReportTelemetryQuery buildTelemetryQuery(ReportChartQuery query,
            GenerateReportRequest request) {
        ReportVariableMetadata metadata = variableMetadataService.resolve(
                query.getKey(),
                query.getLabel(),
                query.getUnit());

        ReportTelemetryQuery telemetryQuery = new ReportTelemetryQuery();
        telemetryQuery.setKey(query.getKey());
        telemetryQuery.setLabel(metadata.getLabel());
        telemetryQuery.setUnit(metadata.getUnit());
        telemetryQuery.setStartTs(request.getStartTs());
        telemetryQuery.setEndTs(request.getEndTs());
        telemetryQuery.setAggregation(query.getAggregation());
        telemetryQuery.setInterval(query.getInterval());
        telemetryQuery.setLimit(query.getLimit());
        telemetryQuery.setOrderBy(query.getOrderBy());
        return telemetryQuery;
    }

    private List<ReportChartQuery> extractChartQueries(JsonNode config) {
        List<ReportChartQuery> result = new ArrayList<>();

        if (config == null || config.isNull()) {
            return result;
        }

        JsonNode itemsNode = config.get("items");
        if (itemsNode == null || !itemsNode.isArray()) {
            return result;
        }

        for (JsonNode itemNode : itemsNode) {
            ReportChartQuery query = objectMapper.convertValue(itemNode, ReportChartQuery.class);
            result.add(query);
        }

        return result;
    }
}