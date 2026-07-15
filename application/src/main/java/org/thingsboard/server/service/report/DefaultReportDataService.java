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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DefaultReportDataService implements ReportDataService {

    //Limite omitido, util para reducir load de creado de reportes, pero reduce precisión

    // private static final int DEFAULT_SERIES_LIMIT = 1000;
    private static final long DEFAULT_INTERVAL_MS = 60000L;

    private final ReportEntityResolverService reportEntityResolverService;
    private final ReportTelemetryService reportTelemetryService;
    private final ReportKpiService reportKpiService;
    private final ReportChartService reportChartService;
    private final ReportTableService reportTableService;
    private final ReportAlarmService reportAlarmService;

    @Override
    public ReportDataResult collectReportData(ReportTemplate template, GenerateReportRequest request) {
        List<ReportTargetEntity> entities = reportEntityResolverService.resolveEntities(template, request);

        ReportDataResult result = new ReportDataResult();
        result.setEntities(entities);

        result.setKpis(reportKpiService.buildKpis(template, request, entities));
        result.setTimeSeries(reportChartService.buildTimeSeries(template, request, entities));
        result.setTables(reportTableService.buildTables(template, request, entities));
        result.setAlarms(reportAlarmService.findAlarms(template, request, entities));

        enrichWithAnalyticalSections(template, request, entities, result);

        return result;
    }

    private void enrichWithAnalyticalSections(ReportTemplate template,
            GenerateReportRequest request,
            List<ReportTargetEntity> entities,
            ReportDataResult result) {
        if (template == null || template.getSections() == null || template.getSections().isEmpty()) {
            return;
        }

        Set<String> keys = extractAnalyticalKeys(template);

        if (keys.isEmpty()) {
            result.getObservations().add("No se seleccionaron claves de telemetría para el análisis del reporte.");
            return;
        }

        if (entities == null || entities.isEmpty()) {
            result.getObservations().add("No se encontraron entidades para el alcance configurado del reporte.");
            return;
        }

        TenantId tenantId = template.getTenantId();

        if (tenantId == null) {
            result.getObservations().add("No fue posible consultar telemetría porque el reporte no contiene tenantId.");
            return;
        }

        List<Map<String, Object>> statisticRows = new ArrayList<>();

        for (ReportTargetEntity entity : entities) {
            for (String key : keys) {
                ReportTelemetryQuery query = buildTelemetryQuery(key, request);
                ReportTimeSeries series = reportTelemetryService.findSeries(tenantId, entity, query);

                if (series == null) {
                    addEmptyStatisticRow(statisticRows, entity, key);
                    result.getObservations()
                            .add("No se obtuvieron datos para " + displayEntityName(entity) + " / " + key + ".");
                    continue;
                }

                normalizeSeries(series, entity, key, request);
                result.getTimeSeries().add(series);

                List<ReportMetricPoint> points = series.getPoints() != null ? series.getPoints() : List.of();

                if (points.isEmpty()) {
                    addEmptyStatisticRow(statisticRows, entity, key);
                    result.getObservations().add("La variable " + key + " no registró muestras para "
                            + displayEntityName(entity) + " en el periodo analizado.");
                    continue;
                }

                Stat stat = calculateStat(points);

                statisticRows.add(buildStatisticRow(entity, key, stat));
                result.getKpis().add(buildAverageKpi(entity, key, stat));
                result.getObservations().add(
                        "La variable " + key + " registró " + stat.count + " muestras para " + displayEntityName(entity)
                                + ". Promedio: " + formatDouble(stat.avg)
                                + ", mínimo: " + formatDouble(stat.min)
                                + ", máximo: " + formatDouble(stat.max) + ".");
            }
        }

        if (!statisticRows.isEmpty()) {
            result.getTables().add(buildGeneralStatisticsTable(statisticRows));
        }
    }

    private Set<String> extractAnalyticalKeys(ReportTemplate template) {
        Set<String> keys = new LinkedHashSet<>();

        if (template.getSections() == null) {
            return keys;
        }

        for (ReportSectionConfig section : template.getSections()) {
            if (section == null || section.getType() == null || section.getConfig() == null) {
                continue;
            }

            if (!isAnalyticalSection(section.getType())) {
                continue;
            }

            JsonNode keysNode = section.getConfig().get("keys");

            if (keysNode == null || !keysNode.isArray()) {
                continue;
            }

            keysNode.forEach(keyNode -> {
                if (keyNode != null && keyNode.isTextual() && !keyNode.asText().isBlank()) {
                    keys.add(keyNode.asText());
                }
            });
        }

        return keys;
    }

    private boolean isAnalyticalSection(ReportSectionType type) {
        return type == ReportSectionType.DATA_QUALITY
                || type == ReportSectionType.GENERAL_STATISTICS
                || type == ReportSectionType.TIME_SERIES_CHART
                || type == ReportSectionType.DAILY_PERFORMANCE
                || type == ReportSectionType.DAILY_CHARTS;
    }

    private ReportTelemetryQuery buildTelemetryQuery(String key, GenerateReportRequest request) {
        ReportTelemetryQuery query = new ReportTelemetryQuery();
        query.setKey(key);
        query.setLabel(key);
        query.setStartTs(request.getStartTs());
        query.setEndTs(request.getEndTs());
        query.setAggregation(ReportAggregationType.NONE);
        query.setInterval(DEFAULT_INTERVAL_MS);
        query.setLimit(null);
        query.setOrderBy("ASC");
        return query;
    }

    private void normalizeSeries(ReportTimeSeries series,
            ReportTargetEntity entity,
            String key,
            GenerateReportRequest request) {
        series.setEntityId(entity.getEntityId());
        series.setEntityType(entity.getEntityType());
        series.setEntityName(displayEntityName(entity));
        series.setKey(key);
        series.setLabel(key);
        series.setAggregation(ReportAggregationType.NONE);
        series.setStartTs(request.getStartTs());
        series.setEndTs(request.getEndTs());
    }

    private ReportKpi buildAverageKpi(ReportTargetEntity entity, String key, Stat stat) {
        ReportKpi kpi = new ReportKpi();
        kpi.setKey(displayEntityName(entity) + "." + key + ".avg");
        kpi.setLabel("Promedio " + key);
        kpi.setValue(stat.avg);
        kpi.setFormattedValue(formatDouble(stat.avg));
        kpi.setUnit("");
        kpi.setStatus("NORMAL");
        return kpi;
    }

    private ReportTable buildGeneralStatisticsTable(List<Map<String, Object>> rows) {
        ReportTable table = new ReportTable();
        table.setKey("general-statistics");
        table.setTitle("Estadística general del periodo");

        table.getColumns().add(column("entity", "Entidad", "left"));
        table.getColumns().add(column("key", "Variable", "left"));
        table.getColumns().add(column("samples", "Muestras", "right"));
        table.getColumns().add(column("min", "Mínimo", "right"));
        table.getColumns().add(column("max", "Máximo", "right"));
        table.getColumns().add(column("avg", "Promedio", "right"));
        table.getColumns().add(column("firstTs", "Primera muestra", "right"));
        table.getColumns().add(column("lastTs", "Última muestra", "right"));

        table.setRows(rows);

        return table;
    }

    private ReportTableColumn column(String key, String label, String align) {
        ReportTableColumn column = new ReportTableColumn();
        column.setKey(key);
        column.setLabel(label);
        column.setAlign(align);
        return column;
    }

    private Map<String, Object> buildStatisticRow(ReportTargetEntity entity, String key, Stat stat) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("entity", displayEntityName(entity));
        row.put("key", key);
        row.put("samples", stat.count);
        row.put("min", formatDouble(stat.min));
        row.put("max", formatDouble(stat.max));
        row.put("avg", formatDouble(stat.avg));
        row.put("firstTs", stat.firstTs);
        row.put("lastTs", stat.lastTs);
        return row;
    }

    private void addEmptyStatisticRow(List<Map<String, Object>> rows,
            ReportTargetEntity entity,
            String key) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("entity", displayEntityName(entity));
        row.put("key", key);
        row.put("samples", 0);
        row.put("min", "-");
        row.put("max", "-");
        row.put("avg", "-");
        row.put("firstTs", "-");
        row.put("lastTs", "-");
        rows.add(row);
    }

    private Stat calculateStat(List<ReportMetricPoint> points) {
        Stat stat = new Stat();
        stat.count = points.size();

        double sum = 0D;

        for (ReportMetricPoint point : points) {
            if (point == null || point.getValue() == null) {
                continue;
            }

            double value = point.getValue();

            if (stat.min == null || value < stat.min) {
                stat.min = value;
            }

            if (stat.max == null || value > stat.max) {
                stat.max = value;
            }

            if (stat.firstTs == null || point.getTs() < stat.firstTs) {
                stat.firstTs = point.getTs();
            }

            if (stat.lastTs == null || point.getTs() > stat.lastTs) {
                stat.lastTs = point.getTs();
            }

            sum += value;
        }

        stat.avg = stat.count > 0 ? sum / stat.count : null;

        return stat;
    }

    private String displayEntityName(ReportTargetEntity entity) {
        if (entity == null) {
            return "Entidad";
        }

        if (entity.getName() != null && !entity.getName().isBlank()) {
            return entity.getName();
        }

        if (entity.getLabel() != null && !entity.getLabel().isBlank()) {
            return entity.getLabel();
        }

        return entity.getEntityId() != null ? entity.getEntityId().toString() : "Entidad";
    }

    private String formatDouble(Double value) {
        if (value == null) {
            return "-";
        }

        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private static class Stat {
        private int count;
        private Double min;
        private Double max;
        private Double avg;
        private Long firstTs;
        private Long lastTs;
    }
}