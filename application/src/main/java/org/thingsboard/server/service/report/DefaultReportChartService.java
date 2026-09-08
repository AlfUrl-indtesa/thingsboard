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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportChartQuery;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultReportChartService implements ReportChartService {

        /*
         * Se conserva para las secciones CHART tradicionales,
         * configuradas mediante ReportChartQuery.
         */
        private final ReportTelemetryService reportTelemetryService;

        private final ObjectMapper objectMapper;

        /*
         * Se conserva para resolver label y unidad de las
         * consultas ReportChartQuery tradicionales.
         */
        private final ReportVariableMetadataService variableMetadataService;

        /*
         * Extrae la configuración estructurada de variables
         * utilizada por el revamp actual.
         */
        private final ReportVariableConfigService variableConfigService;

        /*
         * Centraliza para las variables del revamp:
         *
         * - validación de entidad;
         * - construcción de consultas;
         * - label y unidad;
         * - nombre visible de entidad;
         * - escala y offset;
         * - granularidad;
         * - reutilización de la caché de telemetría.
         */
        private final ReportVariableSeriesService reportVariableSeriesService;

        @Override
        public List<ReportTimeSeries> buildTimeSeries(
                        ReportTemplate template,
                        GenerateReportRequest request,
                        List<ReportTargetEntity> entities) {

                List<ReportTimeSeries> result = new ArrayList<>();

                if (template == null
                                || template.getSections() == null
                                || template.getSections().isEmpty()) {
                        return result;
                }

                TenantId tenantId = template.getTenantId();

                for (ReportSectionConfig section : template.getSections()) {

                        if (!isChartSection(section)
                                        || Boolean.FALSE.equals(
                                                        section.getVisible())) {
                                continue;
                        }

                        /*
                         * La estructura actual del revamp almacena una lista
                         * de ReportVariableConfig dentro de TIME_SERIES_CHART.
                         */
                        List<ReportVariableConfig> variables = variableConfigService.extractVariables(
                                        section.getConfig());

                        List<ReportVariableConfig> chartVariables = variables.stream()
                                        .filter(variable -> variable != null)
                                        .filter(variable -> !Boolean.FALSE.equals(
                                                        variable.getEnabled()))
                                        .filter(variable -> !Boolean.FALSE.equals(
                                                        variable.getChartEnabled()))
                                        .toList();

                        if (!chartVariables.isEmpty()) {
                                result.addAll(
                                                buildSeriesForVariables(
                                                                tenantId,
                                                                request,
                                                                entities,
                                                                chartVariables));

                                /*
                                 * Cuando la sección ya contiene variables
                                 * estructuradas, no se procesa también "items"
                                 * para evitar series duplicadas.
                                 */
                                continue;
                        }

                        /*
                         * Compatibilidad con secciones CHART tradicionales
                         * que todavía utilicen ReportChartQuery.
                         */
                        List<ReportChartQuery> queries = extractChartQueries(
                                        section.getConfig());

                        if (queries.isEmpty()) {
                                continue;
                        }

                        for (ReportChartQuery query : queries) {
                                result.addAll(
                                                buildSeriesForQuery(
                                                                tenantId,
                                                                request,
                                                                entities,
                                                                query));
                        }
                }

                return result;
        }

        /**
         * Construye las series correspondientes a las variables
         * configuradas en el revamp actual.
         */
        private List<ReportTimeSeries> buildSeriesForVariables(
                        TenantId tenantId,
                        GenerateReportRequest request,
                        List<ReportTargetEntity> entities,
                        List<ReportVariableConfig> variables) {

                List<ReportTimeSeries> result = new ArrayList<>();

                if (entities == null
                                || entities.isEmpty()
                                || variables == null
                                || variables.isEmpty()) {
                        return result;
                }

                for (ReportVariableConfig variable : variables) {
                        if (variable == null
                                        || variable.getKey() == null
                                        || variable.getKey().isBlank()) {
                                continue;
                        }

                        for (ReportTargetEntity entity : entities) {
                                /*
                                 * El servicio devuelve null cuando la variable
                                 * no pertenece a la entidad actual.
                                 */
                                ReportTimeSeries series = reportVariableSeriesService.findSeries(
                                                tenantId,
                                                entity,
                                                variable,
                                                request);

                                if (series == null) {
                                        continue;
                                }

                                populatePreviousPeriod(
                                                tenantId,
                                                request,
                                                entity,
                                                variable,
                                                series);

                                result.add(series);
                        }
                }

                return result;
        }

        /**
         * Adjunta los puntos del periodo anterior cuando el análisis
         * de la variable tiene habilitada la comparación.
         *
         * ReportVariableSeriesService devuelve los puntos anteriores
         * con la misma escala y offset aplicados a la serie actual.
         */
        private void populatePreviousPeriod(
                        TenantId tenantId,
                        GenerateReportRequest request,
                        ReportTargetEntity entity,
                        ReportVariableConfig variable,
                        ReportTimeSeries currentSeries) {

                currentSeries.setPreviousStartTs(null);
                currentSeries.setPreviousEndTs(null);
                currentSeries.setPreviousPoints(
                                new ArrayList<>());

                if (!shouldComparePreviousPeriod(variable)) {
                        return;
                }

                PreviousPeriod previousPeriod = calculatePreviousPeriod(request);

                if (previousPeriod == null) {
                        log.warn(
                                        "Unable to calculate previous report period. "
                                                        + "entityId={}, key={}, startTs={}, endTs={}",
                                        entity != null
                                                        ? entity.getEntityId()
                                                        : null,
                                        variable != null
                                                        ? variable.getKey()
                                                        : null,
                                        request != null
                                                        ? request.getStartTs()
                                                        : null,
                                        request != null
                                                        ? request.getEndTs()
                                                        : null);

                        return;
                }

                currentSeries.setPreviousStartTs(
                                previousPeriod.startTs);

                currentSeries.setPreviousEndTs(
                                previousPeriod.endTs);

                try {
                        ReportTimeSeries previousSeries = reportVariableSeriesService.findSeries(
                                        tenantId,
                                        entity,
                                        variable,
                                        previousPeriod.startTs,
                                        previousPeriod.endTs);

                        List<ReportMetricPoint> previousPoints = previousSeries == null
                                        || previousSeries.getPoints() == null
                                                        ? new ArrayList<>()
                                                        : new ArrayList<>(
                                                                        previousSeries.getPoints());

                        currentSeries.setPreviousPoints(
                                        previousPoints);

                        log.info(
                                        "Report previous-period comparison: "
                                                        + "entityId={}, key={}, "
                                                        + "currentRange=[{},{}], "
                                                        + "previousRange=[{},{}], "
                                                        + "currentPoints={}, previousPoints={}",
                                        entity != null
                                                        ? entity.getEntityId()
                                                        : null,
                                        variable != null
                                                        ? variable.getKey()
                                                        : null,
                                        request != null
                                                        ? request.getStartTs()
                                                        : null,
                                        request != null
                                                        ? request.getEndTs()
                                                        : null,
                                        previousPeriod.startTs,
                                        previousPeriod.endTs,
                                        sizeOf(currentSeries.getPoints()),
                                        sizeOf(previousPoints));
                } catch (RuntimeException exception) {
                        /*
                         * Una falla en la consulta del periodo anterior
                         * no debe impedir la generación del reporte actual.
                         */
                        currentSeries.setPreviousPoints(
                                        new ArrayList<>());

                        log.warn(
                                        "Failed to read previous report period. "
                                                        + "entityId={}, key={}, "
                                                        + "previousRange=[{},{}]",
                                        entity != null
                                                        ? entity.getEntityId()
                                                        : null,
                                        variable != null
                                                        ? variable.getKey()
                                                        : null,
                                        previousPeriod.startTs,
                                        previousPeriod.endTs,
                                        exception);
                }
        }

        private boolean shouldComparePreviousPeriod(
                        ReportVariableConfig variable) {

                if (variable == null
                                || variable.getAnalysis() == null) {
                        return false;
                }

                /*
                 * La consulta adicional sólo se realiza cuando:
                 *
                 * 1. el análisis avanzado está habilitado;
                 * 2. la comparación con el periodo anterior está habilitada.
                 */
                return Boolean.TRUE.equals(
                                variable.getAnalysis().getEnabled())
                                && Boolean.TRUE.equals(
                                                variable.getAnalysis()
                                                                .getComparePreviousPeriod());
        }

        private PreviousPeriod calculatePreviousPeriod(
                        GenerateReportRequest request) {

                if (request == null) {
                        return null;
                }

                Long currentStartTs = request.getStartTs();

                Long currentEndTs = request.getEndTs();

                if (currentStartTs == null
                                || currentEndTs == null
                                || currentStartTs < 0
                                || currentEndTs <= currentStartTs) {
                        return null;
                }

                long duration = currentEndTs - currentStartTs;

                if (duration <= 0
                                || currentStartTs <= 0) {
                        return null;
                }

                /*
                 * Periodo actual:
                 *
                 * [currentStartTs, currentEndTs]
                 *
                 * Periodo anterior adyacente:
                 *
                 * [currentStartTs - duration, currentStartTs - 1]
                 */
                long previousStartTs = Math.max(
                                0L,
                                currentStartTs - duration);

                long previousEndTs = currentStartTs - 1L;

                if (previousEndTs < previousStartTs) {
                        return null;
                }

                return new PreviousPeriod(
                                previousStartTs,
                                previousEndTs);
        }

        /**
         * Ruta de compatibilidad para secciones CHART tradicionales.
         *
         * No utiliza ReportVariableConfig porque ReportChartQuery ya
         * contiene agregación, intervalo, límite y orden propios.
         */
        private List<ReportTimeSeries> buildSeriesForQuery(
                        TenantId tenantId,
                        GenerateReportRequest request,
                        List<ReportTargetEntity> entities,
                        ReportChartQuery query) {

                List<ReportTimeSeries> result = new ArrayList<>();

                if (entities == null
                                || entities.isEmpty()
                                || query == null
                                || query.getKey() == null
                                || query.getKey().isBlank()) {
                        return result;
                }

                ReportTelemetryQuery telemetryQuery = buildTelemetryQuery(
                                query,
                                request);

                for (ReportTargetEntity entity : entities) {
                        ReportTimeSeries series = reportTelemetryService.findSeries(
                                        tenantId,
                                        entity,
                                        telemetryQuery);

                        if (series != null) {
                                result.add(series);
                        }
                }

                return result;
        }

        /**
         * Construye únicamente las consultas de ReportChartQuery.
         *
         * Las consultas basadas en ReportVariableConfig son
         * responsabilidad de ReportVariableSeriesService.
         */
        private ReportTelemetryQuery buildTelemetryQuery(
                        ReportChartQuery query,
                        GenerateReportRequest request) {

                ReportVariableMetadata metadata = variableMetadataService.resolve(
                                query.getKey(),
                                query.getLabel(),
                                query.getUnit());

                ReportTelemetryQuery telemetryQuery = new ReportTelemetryQuery();

                telemetryQuery.setKey(
                                query.getKey());

                telemetryQuery.setLabel(
                                metadata.getLabel());

                telemetryQuery.setUnit(
                                metadata.getUnit());

                if (request != null) {
                        telemetryQuery.setStartTs(
                                        request.getStartTs());

                        telemetryQuery.setEndTs(
                                        request.getEndTs());
                }

                telemetryQuery.setAggregation(
                                query.getAggregation());

                telemetryQuery.setInterval(
                                query.getInterval());

                telemetryQuery.setLimit(
                                query.getLimit());

                telemetryQuery.setOrderBy(
                                query.getOrderBy());

                return telemetryQuery;
        }

        private List<ReportChartQuery> extractChartQueries(
                        JsonNode config) {

                List<ReportChartQuery> result = new ArrayList<>();

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
                                ReportChartQuery query =
                                                objectMapper.convertValue(
                                                                itemNode,
                                                                ReportChartQuery.class);

                                if (query != null) {
                                        result.add(query);
                                }
                        } catch (IllegalArgumentException exception) {
                                throw new ReportServiceException(
                                                org.thingsboard.server.common.data.report.ReportErrorCode.PAYLOAD_BUILD_FAILED,
                                                "Invalid chart report configuration",
                                                exception);
                        }
                }

                return result;
        }

        private int sizeOf(
                        List<?> values) {

                return values != null
                                ? values.size()
                                : 0;
        }

        /**
         * Solamente TIME_SERIES_CHART genera las series principales
         * del revamp.
         *
         * DAILY_PERFORMANCE y DAILY_CHARTS utilizan posteriormente
         * esas mismas series y no deben provocar nuevas consultas.
         */
        private boolean isChartSection(
                        ReportSectionConfig section) {

                if (section == null
                                || section.getType() == null) {
                        return false;
                }

                return section.getType() == ReportSectionType.CHART
                                || section.getType() == ReportSectionType.TIME_SERIES_CHART;
        }

        private static final class PreviousPeriod {

                private final long startTs;
                private final long endTs;

                private PreviousPeriod(
                                long startTs,
                                long endTs) {

                        this.startTs = startTs;
                        this.endTs = endTs;
                }
        }
}
