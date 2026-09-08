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
import org.thingsboard.server.common.data.report.ReportMetricPoint;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportSectionType;
import org.thingsboard.server.common.data.report.ReportTable;
import org.thingsboard.server.common.data.report.ReportTableColumn;
import org.thingsboard.server.common.data.report.ReportTableQuery;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTelemetryQuery;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportTimeSeries;
import org.thingsboard.server.common.data.report.ReportVariableConfig;
import org.thingsboard.server.common.data.report.ReportVariableMetadata;
import org.thingsboard.server.common.data.report.ReportSeriesStatistics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DefaultReportTableService implements ReportTableService {

    private static final String ENTITY_COLUMN_KEY = "entity";
    private static final String ENTITY_COLUMN_LABEL = "Equipo";

    private static final String VARIABLE_COLUMN_KEY = "variable";
    private static final String UNIT_COLUMN_KEY = "unit";

    /*
     * Se conserva para las secciones TABLE tradicionales,
     * configuradas mediante ReportTableQuery.
     */
    private final ReportTelemetryService reportTelemetryService;

    private final ReportKpiCalculationSupport calculationSupport;

    private final ObjectMapper objectMapper;

    /*
     * Se conserva para resolver label y unidad de las
     * consultas ReportTableQuery tradicionales.
     */
    private final ReportVariableMetadataService variableMetadataService;

    /*
     * Extrae la configuración estructurada de variables
     * utilizada por GENERAL_STATISTICS.
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
     * - reutilización de la caché de telemetría.
     */
    private final ReportVariableSeriesService reportVariableSeriesService;
    private final ReportSeriesStatisticsService reportSeriesStatisticsService;

    @Override
    public List<ReportTable> buildTables(
            ReportTemplate template,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities) {

        List<ReportTable> result = new ArrayList<>();

        if (template == null
                || template.getSections() == null
                || template.getSections().isEmpty()) {
            return result;
        }

        TenantId tenantId = template.getTenantId();

        for (ReportSectionConfig section : template.getSections()) {

            if (!isStatisticsSection(section)
                    || Boolean.FALSE.equals(
                            section.getVisible())) {
                continue;
            }

            /*
             * Estructura utilizada por el revamp actual:
             * GENERAL_STATISTICS contiene ReportVariableConfig.
             */
            List<ReportVariableConfig> variables = variableConfigService.extractVariables(
                    section.getConfig());

            List<ReportVariableConfig> tableVariables = variables.stream()
                    .filter(variable -> variable != null)
                    .filter(variable -> !Boolean.FALSE.equals(
                            variable.getEnabled()))
                    .filter(variable -> !Boolean.FALSE.equals(
                            variable.getTableEnabled()))
                    .toList();

            if (!tableVariables.isEmpty()) {
                ReportTable table = buildVariableStatsTable(
                        tenantId,
                        request,
                        entities,
                        section,
                        tableVariables);

                if (table != null) {
                    result.add(table);
                }

                /*
                 * La sección ya fue procesada mediante variables
                 * estructuradas. No se procesan también columnas
                 * tradicionales para evitar tablas duplicadas.
                 */
                continue;
            }

            /*
             * Compatibilidad con secciones TABLE tradicionales,
             * configuradas mediante un arreglo "columns".
             */
            List<ReportTableQuery> queries = extractTableQueries(
                    section.getConfig());

            if (queries.isEmpty()) {
                continue;
            }

            ReportTable table = buildTableForSection(
                    tenantId,
                    request,
                    entities,
                    section,
                    queries);

            if (table != null) {
                result.add(table);
            }
        }

        return result;
    }

    /**
     * Construye la tabla general de estadísticas del revamp.
     */
    private ReportTable buildVariableStatsTable(
            TenantId tenantId,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities,
            ReportSectionConfig section,
            List<ReportVariableConfig> variables) {

        if (entities == null
                || entities.isEmpty()
                || variables == null
                || variables.isEmpty()) {
            return null;
        }

        ReportTable table = new ReportTable();

        table.setKey(
                section.getKey());

        table.setTitle(
                section.getTitle());

        table.setColumns(
                buildVariableStatsColumns(
                        variables));

        table.setRows(
                buildVariableStatsRows(
                        tenantId,
                        request,
                        entities,
                        variables));

        return table;
    }

    /**
     * Define las columnas visibles de acuerdo con las estadísticas
     * habilitadas en cada variable.
     */
    private List<ReportTableColumn> buildVariableStatsColumns(
            List<ReportVariableConfig> variables) {

        List<ReportTableColumn> columns = new ArrayList<>();

        columns.add(
                column(
                        ENTITY_COLUMN_KEY,
                        ENTITY_COLUMN_LABEL,
                        "left"));

        columns.add(
                column(
                        VARIABLE_COLUMN_KEY,
                        "Variable",
                        "left"));

        columns.add(
                column(
                        UNIT_COLUMN_KEY,
                        "Unidad",
                        "left"));

        boolean includeCount = variables.stream()
                .filter(variable -> variable != null)
                .anyMatch(variable -> variable.getStats() == null
                        || !Boolean.FALSE.equals(
                                variable.getStats()
                                        .getCount()));

        boolean includeMin = variables.stream()
                .filter(variable -> variable != null)
                .anyMatch(variable -> variable.getStats() == null
                        || !Boolean.FALSE.equals(
                                variable.getStats()
                                        .getMin()));

        boolean includeMax = variables.stream()
                .filter(variable -> variable != null)
                .anyMatch(variable -> variable.getStats() == null
                        || !Boolean.FALSE.equals(
                                variable.getStats()
                                        .getMax()));

        boolean includeAvg = variables.stream()
                .filter(variable -> variable != null)
                .anyMatch(variable -> variable.getStats() == null
                        || !Boolean.FALSE.equals(
                                variable.getStats()
                                        .getAvg()));

        boolean includeSum = variables.stream()
                .filter(variable -> variable != null)
                .anyMatch(variable -> variable.getStats() != null
                        && Boolean.TRUE.equals(
                                variable.getStats()
                                        .getSum()));

        boolean includeFirst = variables.stream()
                .filter(variable -> variable != null)
                .anyMatch(variable -> variable.getStats() != null
                        && Boolean.TRUE.equals(
                                variable.getStats()
                                        .getFirst()));

        boolean includeLast = variables.stream()
                .filter(variable -> variable != null)
                .anyMatch(variable -> variable.getStats() != null
                        && Boolean.TRUE.equals(
                                variable.getStats()
                                        .getLast()));

        boolean includeDelta = variables.stream()
                .filter(variable -> variable != null)
                .anyMatch(variable -> variable.getStats() != null
                        && Boolean.TRUE.equals(
                                variable.getStats()
                                        .getDelta()));

        if (includeCount) {
            columns.add(
                    column(
                            "count",
                            "Muestras",
                            "right"));
        }

        if (includeMin) {
            columns.add(
                    column(
                            "min",
                            "Mínimo",
                            "right"));
        }

        if (includeMax) {
            columns.add(
                    column(
                            "max",
                            "Máximo",
                            "right"));
        }

        if (includeAvg) {
            columns.add(
                    column(
                            "avg",
                            "Promedio",
                            "right"));
        }

        if (includeSum) {
            columns.add(
                    column(
                            "sum",
                            "Suma",
                            "right"));
        }

        if (includeFirst) {
            columns.add(
                    column(
                            "first",
                            "Primero",
                            "right"));
        }

        if (includeLast) {
            columns.add(
                    column(
                            "last",
                            "Último",
                            "right"));
        }

        if (includeDelta) {
            columns.add(
                    column(
                            "delta",
                            "Delta",
                            "right"));
        }

        return columns;
    }

    /**
     * Genera una fila por cada combinación válida:
     *
     * entidad + variable.
     *
     * ReportVariableSeriesService entrega los puntos con escala
     * y offset ya aplicados.
     */
    private List<Map<String, Object>> buildVariableStatsRows(
            TenantId tenantId,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities,
            List<ReportVariableConfig> variables) {

        List<Map<String, Object>> rows = new ArrayList<>();

        if (entities == null
                || entities.isEmpty()
                || variables == null
                || variables.isEmpty()) {
            return rows;
        }

        for (ReportVariableConfig variable : variables) {
            if (variable == null
                    || variable.getKey() == null
                    || variable.getKey().isBlank()) {
                continue;
            }

            for (ReportTargetEntity entity : entities) {
                /*
                 * Devuelve null cuando la variable no corresponde
                 * a la entidad actual.
                 */
                ReportTimeSeries series = reportVariableSeriesService.findSeries(
                        tenantId,
                        entity,
                        variable,
                        request);

                if (series == null) {
                    continue;
                }

                ReportSeriesStatistics statistics = reportSeriesStatisticsService.calculate(
                        series.getPoints());

                Map<String, Object> row = new LinkedHashMap<>();

                row.put(
                        ENTITY_COLUMN_KEY,
                        series.getEntityName());

                row.put(
                        VARIABLE_COLUMN_KEY,
                        series.getLabel());

                row.put(
                        UNIT_COLUMN_KEY,
                        hasText(series.getUnit())
                                ? series.getUnit()
                                : "-");

                if (isCountEnabled(variable)) {
                    row.put(
                            "count",
                            statistics.getValidPointCount());
                }

                if (isMinEnabled(variable)) {
                    row.put(
                            "min",
                            formatStatistic(
                                    statistics.isHasData(),
                                    statistics.getMin()));
                }

                if (isMaxEnabled(variable)) {
                    row.put(
                            "max",
                            formatStatistic(
                                    statistics.isHasData(),
                                    statistics.getMax()));
                }

                if (isAvgEnabled(variable)) {
                    row.put(
                            "avg",
                            formatStatistic(
                                    statistics.isHasData(),
                                    statistics.getAvg()));
                }

                if (isSumEnabled(variable)) {
                    row.put(
                            "sum",
                            formatStatistic(
                                    statistics.isHasData(),
                                    statistics.getSum()));
                }

                if (isFirstEnabled(variable)) {
                    row.put(
                            "first",
                            formatStatistic(
                                    statistics.isHasData(),
                                    statistics.getFirst()));
                }

                if (isLastEnabled(variable)) {
                    row.put(
                            "last",
                            formatStatistic(
                                    statistics.isHasData(),
                                    statistics.getLast()));
                }

                if (isDeltaEnabled(variable)) {
                    row.put(
                            "delta",
                            formatStatistic(
                                    statistics.isHasData(),
                                    statistics.getDelta()));
                }

                rows.add(row);
            }
        }

        return rows;
    }

    /**
     * Construye una tabla tradicional basada en ReportTableQuery.
     */
    private ReportTable buildTableForSection(
            TenantId tenantId,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities,
            ReportSectionConfig section,
            List<ReportTableQuery> queries) {

        if (entities == null
                || entities.isEmpty()
                || queries == null
                || queries.isEmpty()) {
            return null;
        }

        ReportTable table = new ReportTable();

        table.setKey(
                section.getKey());

        table.setTitle(
                section.getTitle());

        table.setColumns(
                buildColumns(
                        queries));

        table.setRows(
                buildRows(
                        tenantId,
                        request,
                        entities,
                        queries));

        return table;
    }

    private List<ReportTableColumn> buildColumns(
            List<ReportTableQuery> queries) {

        List<ReportTableColumn> columns = new ArrayList<>();

        columns.add(
                column(
                        ENTITY_COLUMN_KEY,
                        ENTITY_COLUMN_LABEL,
                        "left"));

        for (ReportTableQuery query : queries) {
            if (query == null
                    || query.getKey() == null
                    || query.getKey().isBlank()) {
                continue;
            }

            ReportVariableMetadata metadata = variableMetadataService.resolve(
                    query.getKey(),
                    query.getLabel(),
                    query.getUnit());

            String label = metadata.getLabel();

            if (hasText(metadata.getUnit())) {
                label = label
                        + " ("
                        + metadata.getUnit()
                        + ")";
            }

            columns.add(
                    column(
                            resolveColumnKey(query),
                            label,
                            hasText(query.getAlign())
                                    ? query.getAlign()
                                    : "right"));
        }

        return columns;
    }

    private List<Map<String, Object>> buildRows(
            TenantId tenantId,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities,
            List<ReportTableQuery> queries) {

        List<Map<String, Object>> rows = new ArrayList<>();

        if (entities == null
                || entities.isEmpty()
                || queries == null
                || queries.isEmpty()) {
            return rows;
        }

        for (ReportTargetEntity entity : entities) {
            Map<String, Object> row = new LinkedHashMap<>();

            row.put(
                    ENTITY_COLUMN_KEY,
                    entity.getName());

            for (ReportTableQuery query : queries) {
                if (query == null
                        || query.getKey() == null
                        || query.getKey().isBlank()) {
                    continue;
                }

                ReportTelemetryQuery telemetryQuery = buildTelemetryQuery(
                        query,
                        request);

                ReportTimeSeries series = reportTelemetryService.findSeries(
                        tenantId,
                        entity,
                        telemetryQuery);

                List<ReportMetricPoint> points = series != null
                        && series.getPoints() != null
                                ? series.getPoints()
                                : List.of();

                Double value = calculationSupport.calculate(
                        points,
                        query.getAggregation());

                String formattedValue = value != null
                        ? calculationSupport.format(value)
                        : null;

                ReportVariableMetadata metadata = variableMetadataService.resolve(
                        query.getKey(),
                        query.getLabel(),
                        query.getUnit());

                if (formattedValue != null
                        && hasText(metadata.getUnit())) {
                    formattedValue = formattedValue
                            + " "
                            + metadata.getUnit();
                }

                row.put(
                        resolveColumnKey(query),
                        formattedValue != null
                                ? formattedValue
                                : "-");
            }

            rows.add(row);
        }

        return rows;
    }

    /**
     * Construye únicamente las consultas tradicionales
     * basadas en ReportTableQuery.
     *
     * Las variables del revamp utilizan ReportVariableSeriesService.
     */
    private ReportTelemetryQuery buildTelemetryQuery(
            ReportTableQuery query,
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

        /*
         * La agregación se calcula localmente sobre los puntos
         * crudos mediante ReportKpiCalculationSupport.
         */
        telemetryQuery.setAggregation(
                ReportAggregationType.NONE);

        telemetryQuery.setOrderBy("ASC");

        return telemetryQuery;
    }

    private String formatStatistic(
            boolean hasData,
            Double value) {

        if (!hasData
                || value == null
                || !Double.isFinite(value)) {
            return "-";
        }

        String formatted = calculationSupport.format(value);

        return formatted != null
                ? formatted
                : "-";
    }

    private boolean isCountEnabled(
            ReportVariableConfig variable) {

        return variable.getStats() == null
                || !Boolean.FALSE.equals(
                        variable.getStats().getCount());
    }

    private boolean isMinEnabled(
            ReportVariableConfig variable) {

        return variable.getStats() == null
                || !Boolean.FALSE.equals(
                        variable.getStats().getMin());
    }

    private boolean isMaxEnabled(
            ReportVariableConfig variable) {

        return variable.getStats() == null
                || !Boolean.FALSE.equals(
                        variable.getStats().getMax());
    }

    private boolean isAvgEnabled(
            ReportVariableConfig variable) {

        return variable.getStats() == null
                || !Boolean.FALSE.equals(
                        variable.getStats().getAvg());
    }

    private boolean isSumEnabled(
            ReportVariableConfig variable) {

        return variable.getStats() != null
                && Boolean.TRUE.equals(
                        variable.getStats().getSum());
    }

    private boolean isFirstEnabled(
            ReportVariableConfig variable) {

        return variable.getStats() != null
                && Boolean.TRUE.equals(
                        variable.getStats().getFirst());
    }

    private boolean isLastEnabled(
            ReportVariableConfig variable) {

        return variable.getStats() != null
                && Boolean.TRUE.equals(
                        variable.getStats().getLast());
    }

    private boolean isDeltaEnabled(
            ReportVariableConfig variable) {

        return variable.getStats() != null
                && Boolean.TRUE.equals(
                        variable.getStats().getDelta());
    }

    private ReportTableColumn column(
            String key,
            String label,
            String align) {

        ReportTableColumn column = new ReportTableColumn();

        column.setKey(key);
        column.setLabel(label);
        column.setAlign(align);

        return column;
    }

    private String resolveColumnKey(
            ReportTableQuery query) {

        if (hasText(query.getColumnKey())) {
            return query.getColumnKey();
        }

        return query.getKey();
    }

    private List<ReportTableQuery> extractTableQueries(
            JsonNode config) {

        List<ReportTableQuery> result = new ArrayList<>();

        if (config == null
                || config.isNull()) {
            return result;
        }

        JsonNode columnsNode = config.get("columns");

        if (columnsNode == null
                || !columnsNode.isArray()) {
            return result;
        }

        for (JsonNode itemNode : columnsNode) {
            if (itemNode == null
                    || itemNode.isNull()) {
                continue;
            }

            try {
                ReportTableQuery query =
                        objectMapper.convertValue(
                                itemNode,
                                ReportTableQuery.class);

                if (query != null) {
                    result.add(query);
                }
            } catch (IllegalArgumentException exception) {
                throw new ReportServiceException(
                        org.thingsboard.server.common.data.report.ReportErrorCode.PAYLOAD_BUILD_FAILED,
                        "Invalid table report configuration",
                        exception);
            }
        }

        return result;
    }

    private boolean isStatisticsSection(
            ReportSectionConfig section) {

        if (section == null
                || section.getType() == null) {
            return false;
        }

        return section.getType() == ReportSectionType.TABLE
                || section.getType() == ReportSectionType.GENERAL_STATISTICS;
    }

    private boolean hasText(
            String value) {

        return value != null
                && !value.isBlank();
    }
}
