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
    private final ReportVariableMetadataService variableMetadataService;

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

        ReportVariableMetadata metadata = variableMetadataService.resolve(
                query.getKey(),
                query.getLabel(),
                query.getUnit());
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
            kpi.setLabel(metadata.getLabel());
            kpi.setEntityName(entity.getName());
            kpi.setAggregation(toReportAggregationType(query.getAggregation()));
            kpi.setUnit(metadata.getUnit());
            kpi.setValue(value);
            kpi.setFormattedValue(
                    calculationSupport.format(value));
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

        ReportVariableMetadata metadata = variableMetadataService.resolve(
                query.getKey(),
                query.getLabel(),
                query.getUnit());

        Double value = calculationSupport.calculate(allPoints, query.getAggregation());
        if (value == null) {
            return null;
        }

        ReportKpi kpi = new ReportKpi();

        kpi.setKey(query.getKey());
        kpi.setLabel(metadata.getLabel());

        /*
         * Un KPI combinado representa múltiples entidades,
         * por lo que no se asigna una entidad individual.
         */
        kpi.setEntityName(null);

        kpi.setAggregation(toReportAggregationType(query.getAggregation()));
        kpi.setUnit(metadata.getUnit());
        kpi.setValue(value);
        kpi.setFormattedValue(
                calculationSupport.format(value));
        kpi.setStatus(query.getStatus());

        return kpi;
    }

    private ReportTelemetryQuery buildTelemetryQuery(ReportKpiQuery query,
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

    private ReportAggregationType toReportAggregationType(ReportKpiAggregationType aggregationType) {
        if (aggregationType == null) {
            return ReportAggregationType.AVG;
        }

        try {
            return ReportAggregationType.valueOf(aggregationType.name());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported KPI aggregation type: " + aggregationType, e);
        }
    }
}