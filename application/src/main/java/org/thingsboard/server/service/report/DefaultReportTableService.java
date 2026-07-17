/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

                Stats stats = calculateStats(
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
                            stats.count);
                }

                if (isMinEnabled(variable)) {
                    row.put(
                            "min",
                            formatStatistic(
                                    stats.hasData,
                                    stats.min));
                }

                if (isMaxEnabled(variable)) {
                    row.put(
                            "max",
                            formatStatistic(
                                    stats.hasData,
                                    stats.max));
                }

                if (isAvgEnabled(variable)) {
                    row.put(
                            "avg",
                            formatStatistic(
                                    stats.hasData,
                                    stats.avg));
                }

                if (isSumEnabled(variable)) {
                    row.put(
                            "sum",
                            formatStatistic(
                                    stats.hasData,
                                    stats.sum));
                }

                if (isFirstEnabled(variable)) {
                    row.put(
                            "first",
                            formatStatistic(
                                    stats.hasData,
                                    stats.first));
                }

                if (isLastEnabled(variable)) {
                    row.put(
                            "last",
                            formatStatistic(
                                    stats.hasData,
                                    stats.last));
                }

                if (isDeltaEnabled(variable)) {
                    row.put(
                            "delta",
                            formatStatistic(
                                    stats.hasData,
                                    stats.last - stats.first));
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

    /**
     * Calcula las estadísticas sobre los puntos ya convertidos.
     *
     * La lista se considera cronológica porque las consultas del
     * servicio central utilizan orderBy ASC.
     */
    private Stats calculateStats(
            List<ReportMetricPoint> points) {

        Stats stats = new Stats();

        if (points == null
                || points.isEmpty()) {
            return stats;
        }

        for (ReportMetricPoint point : points) {
            if (point == null
                    || point.getValue() == null) {
                continue;
            }

            double value = point.getValue();

            if (!stats.hasData) {
                stats.min = value;
                stats.max = value;
                stats.first = value;
                stats.last = value;
                stats.hasData = true;
            }

            stats.count++;
            stats.sum += value;

            stats.min = Math.min(
                    stats.min,
                    value);

            stats.max = Math.max(
                    stats.max,
                    value);

            stats.last = value;
        }

        if (stats.count > 0) {
            stats.avg = stats.sum / stats.count;
        }

        return stats;
    }

    private String formatStatistic(
            boolean hasData,
            double value) {

        return hasData
                ? calculationSupport.format(value)
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

            ReportTableQuery query = objectMapper.convertValue(
                    itemNode,
                    ReportTableQuery.class);

            if (query != null) {
                result.add(query);
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

    private static final class Stats {

        private boolean hasData;

        private int count;

        private double min;
        private double max;
        private double avg;
        private double sum;

        private double first;
        private double last;
    }
}