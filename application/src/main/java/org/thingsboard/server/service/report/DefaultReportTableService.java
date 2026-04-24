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
            if (section.getType() != ReportSectionType.TABLE || !Boolean.TRUE.equals(section.getVisible())) {
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
            column.setLabel(query.getLabel());
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

                if (formattedValue != null && query.getUnit() != null && !query.getUnit().isBlank()) {
                    formattedValue = formattedValue + " " + query.getUnit();
                }

                row.put(resolveColumnKey(query), formattedValue);
            }

            rows.add(row);
        }

        return rows;
    }

    private ReportTelemetryQuery buildTelemetryQuery(ReportTableQuery query,
                                                     GenerateReportRequest request) {
        ReportTelemetryQuery telemetryQuery = new ReportTelemetryQuery();
        telemetryQuery.setKey(query.getKey());
        telemetryQuery.setLabel(query.getLabel());
        telemetryQuery.setUnit(query.getUnit());
        telemetryQuery.setStartTs(request.getStartTs());
        telemetryQuery.setEndTs(request.getEndTs());
        telemetryQuery.setAggregation(mapTelemetryAggregation(query.getAggregation()));
        telemetryQuery.setOrderBy("ASC");
        return telemetryQuery;
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
        if (aggregation == null) {
            return org.thingsboard.server.common.data.report.ReportAggregationType.NONE;
        }

        switch (aggregation) {
            case AVG:
                return org.thingsboard.server.common.data.report.ReportAggregationType.NONE;
            case MIN:
                return org.thingsboard.server.common.data.report.ReportAggregationType.NONE;
            case MAX:
                return org.thingsboard.server.common.data.report.ReportAggregationType.NONE;
            case SUM:
                return org.thingsboard.server.common.data.report.ReportAggregationType.NONE;
            case COUNT:
                return org.thingsboard.server.common.data.report.ReportAggregationType.NONE;
            case FIRST:
                return org.thingsboard.server.common.data.report.ReportAggregationType.NONE;
            case LAST:
                return org.thingsboard.server.common.data.report.ReportAggregationType.NONE;
            case DELTA:
                return org.thingsboard.server.common.data.report.ReportAggregationType.NONE;
            default:
                return org.thingsboard.server.common.data.report.ReportAggregationType.NONE;
        }
    }
}