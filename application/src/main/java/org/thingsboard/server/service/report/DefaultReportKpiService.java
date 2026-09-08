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
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportAggregationType;
import org.thingsboard.server.common.data.report.ReportKpi;
import org.thingsboard.server.common.data.report.ReportKpiAggregationType;
import org.thingsboard.server.common.data.report.ReportKpiQuery;
import org.thingsboard.server.common.data.report.ReportMetricPoint;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportSectionType;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTelemetryQuery;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportTimeSeries;
import org.thingsboard.server.common.data.report.ReportVariableConfig;
import org.thingsboard.server.common.data.report.ReportVariableMetadata;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultReportKpiService implements ReportKpiService {

    /*
     * Sigue siendo necesario para las secciones KPI_GRID explícitas.
     */
    private final ReportTelemetryService reportTelemetryService;

    private final ReportKpiCalculationSupport calculationSupport;

    private final ObjectMapper objectMapper;

    private final ReportVariableMetadataService variableMetadataService;

    /*
     * Se utiliza para extraer las variables configuradas dentro de
     * GENERAL_STATISTICS.
     */
    private final ReportVariableConfigService variableConfigService;

    /*
     * Centraliza para las variables del revamp:
     *
     * - validación de entidad;
     * - construcción de la consulta;
     * - label y unidad;
     * - escala y offset;
     * - nombre visible de la entidad;
     * - reutilización de la caché de telemetría.
     */
    private final ReportVariableSeriesService reportVariableSeriesService;

    @Override
    public List<ReportKpi> buildKpis(
            ReportTemplate template,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities) {

        List<ReportKpi> result = new ArrayList<>();

        if (template == null
                || template.getSections() == null
                || template.getSections().isEmpty()
                || entities == null
                || entities.isEmpty()) {
            return result;
        }

        TenantId tenantId = template.getTenantId();

        /*
         * Una sección KPI_GRID explícita tiene prioridad.
         *
         * De esta forma se conserva compatibilidad con plantillas
         * que definen sus indicadores manualmente y se evita añadir
         * también los KPI automáticos de GENERAL_STATISTICS.
         */
        boolean hasExplicitKpiGrid = template.getSections()
                .stream()
                .filter(section -> section != null)
                .filter(section -> !Boolean.FALSE.equals(section.getVisible()))
                .anyMatch(section -> section.getType() == ReportSectionType.KPI_GRID);

        for (ReportSectionConfig section : template.getSections()) {

            if (section == null
                    || Boolean.FALSE.equals(section.getVisible())) {
                continue;
            }

            if (hasExplicitKpiGrid) {
                if (section.getType() != ReportSectionType.KPI_GRID) {
                    continue;
                }

                List<ReportKpiQuery> queries = extractKpiQueries(section.getConfig());

                for (ReportKpiQuery query : queries) {
                    if (query == null
                            || query.getKey() == null
                            || query.getKey().isBlank()) {
                        continue;
                    }

                    if (Boolean.TRUE.equals(query.getCombineEntities())) {
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
             * Compatibilidad con la estructura actual del revamp:
             * GENERAL_STATISTICS contiene la lista principal de
             * variables seleccionadas por el usuario.
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
             * Sólo se procesa una sección de estadísticas generales
             * para evitar KPI duplicados.
             */
            break;
        }

        return result;
    }

    /**
     * Genera un KPI promedio por variable y entidad para las
     * plantillas del revamp actual.
     *
     * Los puntos recibidos desde ReportVariableSeriesService ya
     * contienen escala y offset aplicados.
     */
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
                    || Boolean.FALSE.equals(variable.getEnabled())
                    || variable.getKey() == null
                    || variable.getKey().isBlank()) {
                continue;
            }

            for (ReportTargetEntity entity : entities) {
                /*
                 * ReportVariableSeriesService valida internamente
                 * si la variable pertenece a esta entidad.
                 */
                ReportTimeSeries series = reportVariableSeriesService.findSeries(
                        tenantId,
                        entity,
                        variable,
                        request);

                if (series == null
                        || series.getPoints() == null
                        || series.getPoints().isEmpty()) {
                    continue;
                }

                Double value = calculationSupport.calculate(
                        series.getPoints(),
                        ReportKpiAggregationType.AVG);

                if (value == null) {
                    continue;
                }

                ReportKpi kpi = new ReportKpi();

                kpi.setKey(series.getKey());
                kpi.setLabel(series.getLabel());
                kpi.setEntityName(series.getEntityName());
                kpi.setAggregation(ReportKpiAggregationType.AVG);
                kpi.setUnit(series.getUnit());
                kpi.setValue(value);
                kpi.setFormattedValue(
                        calculationSupport.format(value));

                result.add(kpi);
            }
        }

        return result;
    }

    /**
     * Construye los KPI explícitos de una sección KPI_GRID,
     * conservando su agregación y estado configurados.
     */
    private List<ReportKpi> buildPerEntityKpis(
            TenantId tenantId,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities,
            ReportKpiQuery query) {

        List<ReportKpi> result = new ArrayList<>();

        if (entities == null
                || entities.isEmpty()
                || query == null) {
            return result;
        }

        ReportVariableMetadata metadata = variableMetadataService.resolve(
                query.getKey(),
                query.getLabel(),
                query.getUnit());

        for (ReportTargetEntity entity : entities) {
            ReportTelemetryQuery telemetryQuery = buildTelemetryQuery(
                    query,
                    request);

            ReportTimeSeries series = reportTelemetryService.findSeries(
                    tenantId,
                    entity,
                    telemetryQuery);

            if (series == null
                    || series.getPoints() == null
                    || series.getPoints().isEmpty()) {
                continue;
            }

            Double value = calculationSupport.calculate(
                    series.getPoints(),
                    query.getAggregation());

            if (value == null) {
                continue;
            }

            ReportKpi kpi = new ReportKpi();

            kpi.setKey(query.getKey());
            kpi.setLabel(metadata.getLabel());
            kpi.setEntityName(entity.getName());
            kpi.setAggregation(
                    query.getAggregation() != null
                            ? query.getAggregation()
                            : ReportKpiAggregationType.AVG);
            kpi.setUnit(metadata.getUnit());
            kpi.setValue(value);
            kpi.setFormattedValue(
                    calculationSupport.format(value));
            kpi.setStatus(query.getStatus());

            result.add(kpi);
        }

        return result;
    }

    /**
     * Genera un solo KPI combinando los puntos de todas las
     * entidades seleccionadas.
     */
    private ReportKpi buildCombinedKpi(
            TenantId tenantId,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities,
            ReportKpiQuery query) {

        if (entities == null
                || entities.isEmpty()
                || query == null) {
            return null;
        }

        List<ReportMetricPoint> allPoints = new ArrayList<>();

        for (ReportTargetEntity entity : entities) {
            ReportTelemetryQuery telemetryQuery = buildTelemetryQuery(
                    query,
                    request);

            ReportTimeSeries series = reportTelemetryService.findSeries(
                    tenantId,
                    entity,
                    telemetryQuery);

            if (series != null
                    && series.getPoints() != null
                    && !series.getPoints().isEmpty()) {
                allPoints.addAll(series.getPoints());
            }
        }

        if (allPoints.isEmpty()) {
            return null;
        }

        Double value = calculationSupport.calculate(
                allPoints,
                query.getAggregation());

        if (value == null) {
            return null;
        }

        ReportVariableMetadata metadata = variableMetadataService.resolve(
                query.getKey(),
                query.getLabel(),
                query.getUnit());

        ReportKpi kpi = new ReportKpi();

        kpi.setKey(query.getKey());
        kpi.setLabel(metadata.getLabel());

        /*
         * Un KPI combinado representa varias entidades y por eso
         * no se asigna un nombre de entidad individual.
         */
        kpi.setEntityName(null);

        kpi.setAggregation(
                query.getAggregation() != null
                        ? query.getAggregation()
                        : ReportKpiAggregationType.AVG);
        kpi.setUnit(metadata.getUnit());
        kpi.setValue(value);
        kpi.setFormattedValue(
                calculationSupport.format(value));
        kpi.setStatus(query.getStatus());

        return kpi;
    }

    /**
     * Esta ruta se conserva para las consultas KPI_GRID.
     *
     * Las variables de GENERAL_STATISTICS ya utilizan
     * ReportVariableSeriesService.
     */
    private ReportTelemetryQuery buildTelemetryQuery(
            ReportKpiQuery query,
            GenerateReportRequest request) {

        ReportVariableMetadata metadata = variableMetadataService.resolve(
                query.getKey(),
                query.getLabel(),
                query.getUnit());

        ReportTelemetryQuery telemetryQuery = new ReportTelemetryQuery();

        telemetryQuery.setKey(query.getKey());
        telemetryQuery.setLabel(metadata.getLabel());
        telemetryQuery.setUnit(metadata.getUnit());

        if (request != null) {
            telemetryQuery.setStartTs(request.getStartTs());
            telemetryQuery.setEndTs(request.getEndTs());
        }

        telemetryQuery.setAggregation(
                ReportAggregationType.NONE);
        telemetryQuery.setOrderBy("ASC");

        return telemetryQuery;
    }

    private List<ReportKpiQuery> extractKpiQueries(
            JsonNode config) {

        List<ReportKpiQuery> result = new ArrayList<>();

        if (config == null
                || config.isNull()) {
            return result;
        }

        JsonNode itemsNode = config.get("items");

        if (itemsNode == null
                || !itemsNode.isArray()) {
            return result;
        }

        for (JsonNode itemNode : itemsNode) {
            if (itemNode == null
                    || itemNode.isNull()) {
                continue;
            }

            try {
                ReportKpiQuery query =
                        objectMapper.convertValue(
                                itemNode,
                                ReportKpiQuery.class);

                if (query != null) {
                    result.add(query);
                }
            } catch (IllegalArgumentException exception) {
                throw new ReportServiceException(
                        org.thingsboard.server.common.data.report.ReportErrorCode.PAYLOAD_BUILD_FAILED,
                        "Invalid KPI report configuration",
                        exception);
            }
        }

        return result;
    }

}
