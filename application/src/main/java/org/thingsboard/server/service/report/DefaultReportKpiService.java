package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.*;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultReportKpiService implements ReportKpiService {

    private final ReportTelemetryService reportTelemetryService;
    private final ReportKpiCalculationSupport calculationSupport;
    private final ObjectMapper objectMapper;

    @Override
    public List<ReportKpi> buildKpis(ReportTemplate template,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities) {
        List<ReportKpi> result = new ArrayList<>();

        if (template == null || template.getSections() == null || template.getSections().isEmpty()) {
            return result;
        }

        TenantId tenantId = template.getTenantId();

        for (ReportSectionConfig section : template.getSections()) {
            if (section.getType() != ReportSectionType.KPI_GRID || !Boolean.TRUE.equals(section.getVisible())) {
                continue;
            }

            List<ReportKpiQuery> queries = extractKpiQueries(section.getConfig());
            if (queries.isEmpty()) {
                continue;
            }

            for (ReportKpiQuery query : queries) {
                if (Boolean.TRUE.equals(query.getCombineEntities())) {
                    ReportKpi combinedKpi = buildCombinedKpi(tenantId, request, entities, query);
                    if (combinedKpi != null) {
                        result.add(combinedKpi);
                    }
                } else {
                    result.addAll(buildPerEntityKpis(tenantId, request, entities, query));
                }
            }
        }

        return result;
    }

    private List<ReportKpi> buildPerEntityKpis(TenantId tenantId,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities,
            ReportKpiQuery query) {
        List<ReportKpi> result = new ArrayList<>();

        for (ReportTargetEntity entity : entities) {
            ReportTelemetryQuery telemetryQuery = buildTelemetryQuery(query, request);
            ReportTimeSeries series = reportTelemetryService.findSeries(tenantId, entity, telemetryQuery);

            Double value = calculationSupport.calculate(series.getPoints(), query.getAggregation());
            if (value == null) {
                continue;
            }

            ReportKpi kpi = new ReportKpi();
            kpi.setKey(query.getKey());
            kpi.setLabel(query.getLabel() + " - " + entity.getName());
            kpi.setValue(value);
            kpi.setFormattedValue(calculationSupport.format(value));
            kpi.setUnit(query.getUnit());
            kpi.setStatus(query.getStatus());

            result.add(kpi);
        }

        return result;
    }

    private ReportKpi buildCombinedKpi(TenantId tenantId,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities,
            ReportKpiQuery query) {
        List<ReportMetricPoint> allPoints = new ArrayList<>();

        for (ReportTargetEntity entity : entities) {
            ReportTelemetryQuery telemetryQuery = buildTelemetryQuery(query, request);
            ReportTimeSeries series = reportTelemetryService.findSeries(tenantId, entity, telemetryQuery);
            if (series.getPoints() != null) {
                allPoints.addAll(series.getPoints());
            }
        }

        Double value = calculationSupport.calculate(allPoints, query.getAggregation());
        if (value == null) {
            return null;
        }

        ReportKpi kpi = new ReportKpi();
        kpi.setKey(query.getKey());
        kpi.setLabel(query.getLabel());
        kpi.setValue(value);
        kpi.setFormattedValue(calculationSupport.format(value));
        kpi.setUnit(query.getUnit());
        kpi.setStatus(query.getStatus());

        return kpi;
    }

    private ReportTelemetryQuery buildTelemetryQuery(ReportKpiQuery query,
            GenerateReportRequest request) {
        ReportTelemetryQuery telemetryQuery = new ReportTelemetryQuery();
        telemetryQuery.setKey(query.getKey());
        telemetryQuery.setLabel(query.getLabel());
        telemetryQuery.setUnit(query.getUnit());
        telemetryQuery.setStartTs(request.getStartTs());
        telemetryQuery.setEndTs(request.getEndTs());
        telemetryQuery.setAggregation(ReportAggregationType.NONE);
        telemetryQuery.setOrderBy("ASC");
        return telemetryQuery;
    }

    private List<ReportKpiQuery> extractKpiQueries(JsonNode config) {
        List<ReportKpiQuery> result = new ArrayList<>();
        if (config == null || config.isNull()) {
            return result;
        }

        JsonNode itemsNode = config.get("items");
        if (itemsNode == null || !itemsNode.isArray()) {
            return result;
        }

        for (JsonNode itemNode : itemsNode) {
            ReportKpiQuery query = objectMapper.convertValue(itemNode, ReportKpiQuery.class);
            result.add(query);
        }

        return result;
    }
}