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
    private final ReportVariableConfigService variableConfigService;

    @Override
    public List<ReportKpi> buildKpis(
            ReportTemplate template,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities) {

        List<ReportKpi> result = new ArrayList<>();

        if (template == null
                || template.getSections() == null
                || template.getSections().isEmpty()) {
            return result;
        }

        TenantId tenantId = template.getTenantId();

        /*
         * Cuando exista una sección KPI_GRID explícita,
         * ésta tiene prioridad sobre el fallback del revamp.
         */
        boolean hasExplicitKpiGrid = template.getSections()
                .stream()
                .filter(section -> section != null)
                .filter(section -> !Boolean.FALSE.equals(
                        section.getVisible()))
                .anyMatch(section -> section.getType() == ReportSectionType.KPI_GRID);

        for (ReportSectionConfig section : template.getSections()) {

            if (section == null
                    || Boolean.FALSE.equals(
                            section.getVisible())) {
                continue;
            }

            if (hasExplicitKpiGrid) {
                if (section.getType() != ReportSectionType.KPI_GRID) {
                    continue;
                }

                List<ReportKpiQuery> queries = extractKpiQueries(
                        section.getConfig());

                for (ReportKpiQuery query : queries) {
                    if (Boolean.TRUE.equals(
                            query.getCombineEntities())) {
                        ReportKpi combinedKpi = buildCombinedKpi(
                                tenantId,
                                request,
                                entities,
                                query);

                        if (combinedKpi != null) {
                            result.add(combinedKpi);
                        }
                    } else {
                        result.addAll(
                                buildPerEntityKpis(
                                        tenantId,
                                        request,
                                        entities,
                                        query));
                    }
                }

                continue;
            }

            /*
             * Compatibilidad con el revamp actual:
             * GENERAL_STATISTICS contiene las variables
             * seleccionadas por el usuario.
             */
            if (section.getType() != ReportSectionType.GENERAL_STATISTICS) {
                continue;
            }

            List<ReportVariableConfig> variables = variableConfigService.extractVariables(
                    section.getConfig());

            result.addAll(
                    buildVariableAverageKpis(
                            tenantId,
                            request,
                            entities,
                            variables));

            /*
             * Sólo debe existir una tabla de estadísticas
             * generales. Evitamos procesar otra sección
             * con las mismas variables.
             */
            break;
        }

        return result;
    }

    private List<ReportKpi> buildVariableAverageKpis(
            TenantId tenantId,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities,
            List<ReportVariableConfig> variables) {

        List<ReportKpi> result = new ArrayList<>();

        if (entities == null
                || entities.isEmpty()
                || variables == null
                || variables.isEmpty()) {
            return result;
        }

        for (ReportVariableConfig variable : variables) {
            if (variable == null
                    || Boolean.FALSE.equals(
                            variable.getEnabled())
                    || variable.getKey() == null
                    || variable.getKey().isBlank()) {
                continue;
            }

            for (ReportTargetEntity entity : entities) {
                if (!matchesEntity(
                        variable,
                        entity)) {
                    continue;
                }

                ReportTelemetryQuery telemetryQuery = buildTelemetryQuery(
                        variable,
                        request);

                ReportTimeSeries series = reportTelemetryService.findSeries(
                        tenantId,
                        entity,
                        telemetryQuery);

                List<ReportMetricPoint> convertedPoints = convertPoints(
                        series != null
                                ? series.getPoints()
                                : null,
                        variable);

                Double value = calculationSupport.calculate(
                        convertedPoints,
                        ReportKpiAggregationType.AVG);

                if (value == null) {
                    continue;
                }

                ReportVariableMetadata metadata = variableMetadataService.resolve(
                        variable.getKey(),
                        variable.getLabel(),
                        variable.getUnit());

                ReportKpi kpi = new ReportKpi();

                kpi.setKey(
                        variable.getKey());

                kpi.setLabel(
                        metadata.getLabel());

                kpi.setEntityName(
                        variable.getEntityName() != null
                                && !variable.getEntityName().isBlank()
                                        ? variable.getEntityName()
                                        : entity.getName());

                kpi.setAggregation(
                        ReportAggregationType.AVG);

                kpi.setUnit(
                        metadata.getUnit());

                kpi.setValue(value);

                kpi.setFormattedValue(
                        calculationSupport.format(value));

                result.add(kpi);
            }
        }

        return result;
    }

    private ReportTelemetryQuery buildTelemetryQuery(
            ReportVariableConfig variable,
            GenerateReportRequest request) {

        ReportVariableMetadata metadata = variableMetadataService.resolve(
                variable.getKey(),
                variable.getLabel(),
                variable.getUnit());

        ReportTelemetryQuery telemetryQuery = new ReportTelemetryQuery();

        telemetryQuery.setKey(
                variable.getKey());

        telemetryQuery.setLabel(
                metadata.getLabel());

        telemetryQuery.setUnit(
                metadata.getUnit());

        telemetryQuery.setStartTs(
                request.getStartTs());

        telemetryQuery.setEndTs(
                request.getEndTs());

        telemetryQuery.setAggregation(
                ReportAggregationType.NONE);

        telemetryQuery.setOrderBy("ASC");

        return telemetryQuery;
    }

    private List<ReportMetricPoint> convertPoints(
            List<ReportMetricPoint> points,
            ReportVariableConfig variable) {

        List<ReportMetricPoint> converted = new ArrayList<>();

        if (points == null
                || points.isEmpty()) {
            return converted;
        }

        for (ReportMetricPoint point : points) {
            if (point == null
                    || point.getValue() == null) {
                continue;
            }

            converted.add(
                    new ReportMetricPoint(
                            point.getTs(),
                            variableConfigService.applyConversion(
                                    variable,
                                    point.getValue())));
        }

        return converted;
    }

    private boolean matchesEntity(
            ReportVariableConfig variable,
            ReportTargetEntity entity) {

        if (variable == null
                || variable.getEntityId() == null
                || variable.getEntityId().getId() == null
                || variable.getEntityId().getEntityType() == null
                || entity == null
                || entity.getEntityId() == null
                || entity.getEntityType() == null) {
            return false;
        }

        return variable.getEntityId()
                .getId()
                .equals(entity.getEntityId())
                && variable.getEntityId()
                        .getEntityType()
                        .name()
                        .equals(entity.getEntityType());
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