package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportKpiAggregationType;
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

    private final ReportTelemetryService reportTelemetryService;
    private final ReportKpiCalculationSupport calculationSupport;
    private final ObjectMapper objectMapper;
    private final ReportVariableMetadataService variableMetadataService;
    private final ReportVariableConfigService variableConfigService;
    private final ReportVariableSeriesService reportVariableSeriesService;

    @Override
    public List<ReportTable> buildTables(ReportTemplate template,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities) {
        List<ReportTable> result = new ArrayList<>();

        if (template == null || template.getSections() == null || template.getSections().isEmpty()) {
            return result;
        }

        TenantId tenantId = template.getTenantId();

        for (ReportSectionConfig section : template.getSections()) {
            if (!isStatisticsSection(section)
                    || Boolean.FALSE.equals(section.getVisible())) {
                continue;
            }

            List<ReportVariableConfig> variables = variableConfigService.extractVariables(section.getConfig());
            List<ReportVariableConfig> tableVariables = variables.stream()
                    .filter(variable -> !Boolean.FALSE.equals(variable.getEnabled()))
                    .filter(variable -> !Boolean.FALSE.equals(variable.getTableEnabled()))
                    .toList();

            if (!tableVariables.isEmpty()) {
                ReportTable table = buildVariableStatsTable(tenantId, request, entities, section, tableVariables);
                if (table != null) {
                    result.add(table);
                }
                continue;
            }

            List<ReportTableQuery> queries = extractTableQueries(section.getConfig());
            if (queries.isEmpty()) {
                continue;
            }

            ReportTable table = buildTableForSection(tenantId, request, entities, section, queries);
            if (table != null) {
                result.add(table);
            }
        }

        return result;
    }

    private ReportTable buildVariableStatsTable(TenantId tenantId,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities,
            ReportSectionConfig section,
            List<ReportVariableConfig> variables) {
        if (entities == null || entities.isEmpty()) {
            return null;
        }

        ReportTable table = new ReportTable();
        table.setKey(section.getKey());
        table.setTitle(section.getTitle());
        table.setColumns(buildVariableStatsColumns(variables));
        table.setRows(buildVariableStatsRows(tenantId, request, entities, variables));

        return table;
    }

    private List<ReportTableColumn> buildVariableStatsColumns(List<ReportVariableConfig> variables) {
        List<ReportTableColumn> columns = new ArrayList<>();

        columns.add(column("entity", "Equipo", "left"));
        columns.add(column("variable", "Variable", "left"));
        columns.add(column("unit", "Unidad", "left"));

        boolean includeCount = variables.stream()
                .anyMatch(v -> v.getStats() == null || !Boolean.FALSE.equals(v.getStats().getCount()));
        boolean includeMin = variables.stream()
                .anyMatch(v -> v.getStats() == null || !Boolean.FALSE.equals(v.getStats().getMin()));
        boolean includeMax = variables.stream()
                .anyMatch(v -> v.getStats() == null || !Boolean.FALSE.equals(v.getStats().getMax()));
        boolean includeAvg = variables.stream()
                .anyMatch(v -> v.getStats() == null || !Boolean.FALSE.equals(v.getStats().getAvg()));
        boolean includeSum = variables.stream()
                .anyMatch(v -> v.getStats() != null && Boolean.TRUE.equals(v.getStats().getSum()));
        boolean includeFirst = variables.stream()
                .anyMatch(v -> v.getStats() != null && Boolean.TRUE.equals(v.getStats().getFirst()));
        boolean includeLast = variables.stream()
                .anyMatch(v -> v.getStats() != null && Boolean.TRUE.equals(v.getStats().getLast()));
        boolean includeDelta = variables.stream()
                .anyMatch(v -> v.getStats() != null && Boolean.TRUE.equals(v.getStats().getDelta()));

        if (includeCount) {
            columns.add(column("count", "Muestras", "right"));
        }
        if (includeMin) {
            columns.add(column("min", "Mínimo", "right"));
        }
        if (includeMax) {
            columns.add(column("max", "Máximo", "right"));
        }
        if (includeAvg) {
            columns.add(column("avg", "Promedio", "right"));
        }
        if (includeSum) {
            columns.add(column("sum", "Suma", "right"));
        }
        if (includeFirst) {
            columns.add(column("first", "Primero", "right"));
        }
        if (includeLast) {
            columns.add(column("last", "Último", "right"));
        }
        if (includeDelta) {
            columns.add(column("delta", "Delta", "right"));
        }

        return columns;
    }

    private List<Map<String, Object>> buildVariableStatsRows(
            TenantId tenantId,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities,
            List<ReportVariableConfig> variables) {

        List<Map<String, Object>> rows = new ArrayList<>();

        for (ReportVariableConfig variable : variables) {
            for (ReportTargetEntity entity : entities) {
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
                        "entity",
                        series.getEntityName());

                row.put(
                        "variable",
                        series.getLabel());

                row.put(
                        "unit",
                        series.getUnit() != null
                                && !series.getUnit().isBlank()
                                        ? series.getUnit()
                                        : "-");

                if (variable.getStats() == null
                        || !Boolean.FALSE.equals(
                                variable.getStats().getCount())) {
                    row.put(
                            "count",
                            stats.count);
                }

                if (variable.getStats() == null
                        || !Boolean.FALSE.equals(
                                variable.getStats().getMin())) {
                    row.put(
                            "min",
                            stats.hasData
                                    ? calculationSupport.format(stats.min)
                                    : "-");
                }

                if (variable.getStats() == null
                        || !Boolean.FALSE.equals(
                                variable.getStats().getMax())) {
                    row.put(
                            "max",
                            stats.hasData
                                    ? calculationSupport.format(stats.max)
                                    : "-");
                }

                if (variable.getStats() == null
                        || !Boolean.FALSE.equals(
                                variable.getStats().getAvg())) {
                    row.put(
                            "avg",
                            stats.hasData
                                    ? calculationSupport.format(stats.avg)
                                    : "-");
                }

                if (variable.getStats() != null
                        && Boolean.TRUE.equals(
                                variable.getStats().getSum())) {
                    row.put(
                            "sum",
                            stats.hasData
                                    ? calculationSupport.format(stats.sum)
                                    : "-");
                }

                if (variable.getStats() != null
                        && Boolean.TRUE.equals(
                                variable.getStats().getFirst())) {
                    row.put(
                            "first",
                            stats.hasData
                                    ? calculationSupport.format(stats.first)
                                    : "-");
                }

                if (variable.getStats() != null
                        && Boolean.TRUE.equals(
                                variable.getStats().getLast())) {
                    row.put(
                            "last",
                            stats.hasData
                                    ? calculationSupport.format(stats.last)
                                    : "-");
                }

                if (variable.getStats() != null
                        && Boolean.TRUE.equals(
                                variable.getStats().getDelta())) {
                    row.put(
                            "delta",
                            stats.hasData
                                    ? calculationSupport.format(
                                            stats.last - stats.first)
                                    : "-");
                }

                rows.add(row);
            }
        }

        return rows;
    }

    private ReportTable buildTableForSection(TenantId tenantId,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities,
            ReportSectionConfig section,
            List<ReportTableQuery> queries) {
        if (entities == null || entities.isEmpty()) {
            return null;
        }

        ReportTable table = new ReportTable();
        table.setKey(section.getKey());
        table.setTitle(section.getTitle());
        table.setColumns(buildColumns(queries));
        table.setRows(buildRows(tenantId, request, entities, queries));

        return table;
    }

    private List<ReportTableColumn> buildColumns(List<ReportTableQuery> queries) {
        List<ReportTableColumn> columns = new ArrayList<>();

        ReportTableColumn entityColumn = new ReportTableColumn();
        entityColumn.setKey(ENTITY_COLUMN_KEY);
        entityColumn.setLabel(ENTITY_COLUMN_LABEL);
        entityColumn.setAlign("left");
        columns.add(entityColumn);

        for (ReportTableQuery query : queries) {
            ReportTableColumn column = new ReportTableColumn();
            column.setKey(resolveColumnKey(query));
            ReportVariableMetadata metadata = variableMetadataService.resolve(
                    query.getKey(),
                    query.getLabel(),
                    query.getUnit());

            String label = metadata.getLabel();
            if (metadata.getUnit() != null && !metadata.getUnit().isBlank()) {
                label = label + " (" + metadata.getUnit() + ")";
            }

            column.setLabel(label);
            column.setAlign(query.getAlign() != null ? query.getAlign() : "right");
            columns.add(column);
        }

        return columns;
    }

    private List<Map<String, Object>> buildRows(TenantId tenantId,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities,
            List<ReportTableQuery> queries) {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (ReportTargetEntity entity : entities) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(ENTITY_COLUMN_KEY, entity.getName());

            for (ReportTableQuery query : queries) {
                ReportTelemetryQuery telemetryQuery = buildTelemetryQuery(query, request);
                ReportTimeSeries series = reportTelemetryService.findSeries(tenantId, entity, telemetryQuery);

                Double value = calculationSupport.calculate(series.getPoints(), query.getAggregation());
                String formattedValue = value != null ? calculationSupport.format(value) : null;

                ReportVariableMetadata metadata = variableMetadataService.resolve(
                        query.getKey(),
                        query.getLabel(),
                        query.getUnit());

                if (formattedValue != null && metadata.getUnit() != null && !metadata.getUnit().isBlank()) {
                    formattedValue = formattedValue + " " + metadata.getUnit();
                }

                row.put(resolveColumnKey(query), formattedValue);
            }

            rows.add(row);
        }

        return rows;
    }


    private ReportTelemetryQuery buildTelemetryQuery(ReportTableQuery query,
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
        telemetryQuery.setAggregation(mapTelemetryAggregation(query.getAggregation()));
        telemetryQuery.setOrderBy("ASC");
        return telemetryQuery;
    }

    private Stats calculateStats(List<ReportMetricPoint> points) {
        Stats stats = new Stats();

        if (points == null || points.isEmpty()) {
            return stats;
        }

        for (ReportMetricPoint point : points) {
            if (point == null || point.getValue() == null) {
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
            stats.min = Math.min(stats.min, value);
            stats.max = Math.max(stats.max, value);
            stats.last = value;
        }

        if (stats.count > 0) {
            stats.avg = stats.sum / stats.count;
        }

        return stats;
    }

    private ReportTableColumn column(String key, String label, String align) {
        ReportTableColumn column = new ReportTableColumn();
        column.setKey(key);
        column.setLabel(label);
        column.setAlign(align);
        return column;
    }

    private String resolveColumnKey(ReportTableQuery query) {
        if (query.getColumnKey() != null && !query.getColumnKey().isBlank()) {
            return query.getColumnKey();
        }
        return query.getKey();
    }

    private List<ReportTableQuery> extractTableQueries(JsonNode config) {
        List<ReportTableQuery> result = new ArrayList<>();

        if (config == null || config.isNull()) {
            return result;
        }

        JsonNode columnsNode = config.get("columns");
        if (columnsNode == null || !columnsNode.isArray()) {
            return result;
        }

        for (JsonNode itemNode : columnsNode) {
            ReportTableQuery query = objectMapper.convertValue(itemNode, ReportTableQuery.class);
            result.add(query);
        }

        return result;
    }

    private org.thingsboard.server.common.data.report.ReportAggregationType mapTelemetryAggregation(
            ReportKpiAggregationType aggregation) {
        return org.thingsboard.server.common.data.report.ReportAggregationType.NONE;
    }

    private static class Stats {
        boolean hasData = false;
        int count = 0;
        double min = 0;
        double max = 0;
        double avg = 0;
        double sum = 0;
        double first = 0;
        double last = 0;
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
}