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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportAlarmItem;
import org.thingsboard.server.common.data.report.ReportDataResult;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportKpi;
import org.thingsboard.server.common.data.report.ReportKpiAggregationType;
import org.thingsboard.server.common.data.report.ReportMetricPoint;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportSectionType;
import org.thingsboard.server.common.data.report.ReportTable;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportTimeSeries;
import org.thingsboard.server.common.data.report.ReportVariableConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportCompositionServiceTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final ReportSeriesStatisticsService statisticsService =
            new DefaultReportSeriesStatisticsService();

    private final ReportKpiCalculationSupport calculationSupport =
            new ReportKpiCalculationSupport(
                    statisticsService);

    private final ReportVariableMetadataService metadataService =
            new DefaultReportVariableMetadataService();

    @Test
    void calculationDefaultsToAverageAndSupportsExtendedAggregations() {
        List<ReportMetricPoint> points =
                List.of(
                        point(20L, 3.0),
                        point(10L, 1.0));

        assertEquals(
                2.0,
                calculationSupport.calculate(
                        points,
                        null),
                0.000001);

        assertEquals(
                1.0,
                calculationSupport.calculate(
                        points,
                        ReportKpiAggregationType.FIRST),
                0.000001);

        assertEquals(
                2.0,
                calculationSupport.calculate(
                        points,
                        ReportKpiAggregationType.DELTA),
                0.000001);

        assertEquals(
                "1,234.568",
                calculationSupport.format(
                        1234.5678));
    }

    @Test
    void explicitKpiPreservesFirstAggregation() {
        ReportTelemetryService telemetryService =
                mock(ReportTelemetryService.class);

        ReportVariableConfigService variableConfigService =
                mock(ReportVariableConfigService.class);

        ReportVariableSeriesService variableSeriesService =
                mock(ReportVariableSeriesService.class);

        DefaultReportKpiService service =
                new DefaultReportKpiService(
                        telemetryService,
                        calculationSupport,
                        objectMapper,
                        metadataService,
                        variableConfigService,
                        variableSeriesService);

        TenantId tenantId =
                TenantId.fromUUID(UUID.randomUUID());

        ReportTargetEntity entity =
                target();

        ObjectNode config =
                objectMapper.createObjectNode();

        config.putArray("items")
                .addObject()
                .put("key", "temperature")
                .put("aggregation", "FIRST")
                .put("combineEntities", false);

        ReportSectionConfig section =
                section(
                        ReportSectionType.KPI_GRID,
                        "kpis",
                        1,
                        config);

        ReportTemplate template =
                template(
                        tenantId,
                        List.of(section));

        ReportTimeSeries series =
                series(
                        entity,
                        "temperature",
                        List.of(
                                point(20L, 20.0),
                                point(10L, 10.0)));

        when(telemetryService.findSeries(
                eq(tenantId),
                same(entity),
                any()))
                .thenReturn(series);

        List<ReportKpi> result =
                service.buildKpis(
                        template,
                        request(1L, 100L),
                        List.of(entity));

        assertEquals(1, result.size());
        assertEquals(10.0, result.get(0).getValue(), 0.000001);
        assertEquals(
                ReportKpiAggregationType.FIRST,
                result.get(0).getAggregation());
    }

    @Test
    void variableStatisticsTableKeepsFirstAndLastSeparately() {
        ReportTelemetryService telemetryService =
                mock(ReportTelemetryService.class);

        ReportVariableConfigService variableConfigService =
                mock(ReportVariableConfigService.class);

        ReportVariableSeriesService variableSeriesService =
                mock(ReportVariableSeriesService.class);

        DefaultReportTableService service =
                new DefaultReportTableService(
                        telemetryService,
                        calculationSupport,
                        objectMapper,
                        metadataService,
                        variableConfigService,
                        variableSeriesService,
                        statisticsService);

        TenantId tenantId =
                TenantId.fromUUID(UUID.randomUUID());

        ReportTargetEntity entity =
                target();

        GenerateReportRequest request =
                request(1L, 100L);

        ReportVariableConfig variable =
                new ReportVariableConfig();

        variable.setKey("temperature");
        variable.getStats().setFirst(true);
        variable.getStats().setLast(true);

        ObjectNode config =
                objectMapper.createObjectNode();

        ReportSectionConfig section =
                section(
                        ReportSectionType.GENERAL_STATISTICS,
                        "statistics",
                        1,
                        config);

        when(variableConfigService.extractVariables(config))
                .thenReturn(List.of(variable));

        when(variableSeriesService.findSeries(
                tenantId,
                entity,
                variable,
                request))
                .thenReturn(
                        series(
                                entity,
                                "temperature",
                                List.of(
                                        point(20L, 9.0),
                                        point(10L, 1.0))));

        List<ReportTable> result =
                service.buildTables(
                        template(
                                tenantId,
                                List.of(section)),
                        request,
                        List.of(entity));

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getRows().size());

        Map<String, Object> row =
                result.get(0).getRows().get(0);

        assertEquals("1", row.get("first"));
        assertEquals("9", row.get("last"));
        assertTrue(row.containsKey("first"));
        assertTrue(row.containsKey("last"));
    }

    @Test
    void chartAttachesPreviousPeriodWhenRequested() {
        ReportTelemetryService telemetryService =
                mock(ReportTelemetryService.class);

        ReportVariableConfigService variableConfigService =
                mock(ReportVariableConfigService.class);

        ReportVariableSeriesService variableSeriesService =
                mock(ReportVariableSeriesService.class);

        DefaultReportChartService service =
                new DefaultReportChartService(
                        telemetryService,
                        objectMapper,
                        metadataService,
                        variableConfigService,
                        variableSeriesService);

        TenantId tenantId =
                TenantId.fromUUID(UUID.randomUUID());

        ReportTargetEntity entity =
                target();

        GenerateReportRequest request =
                request(200L, 300L);

        ReportVariableConfig variable =
                new ReportVariableConfig();

        variable.setKey("temperature");
        variable.getAnalysis().setEnabled(true);
        variable.getAnalysis().setComparePreviousPeriod(true);

        ObjectNode config =
                objectMapper.createObjectNode();

        ReportSectionConfig section =
                section(
                        ReportSectionType.TIME_SERIES_CHART,
                        "series",
                        1,
                        config);

        when(variableConfigService.extractVariables(config))
                .thenReturn(List.of(variable));

        ReportTimeSeries current =
                series(
                        entity,
                        "temperature",
                        List.of(point(250L, 20.0)));

        ReportTimeSeries previous =
                series(
                        entity,
                        "temperature",
                        List.of(point(150L, 15.0)));

        when(variableSeriesService.findSeries(
                tenantId,
                entity,
                variable,
                request))
                .thenReturn(current);

        when(variableSeriesService.findSeries(
                tenantId,
                entity,
                variable,
                100L,
                199L))
                .thenReturn(previous);

        List<ReportTimeSeries> result =
                service.buildTimeSeries(
                        template(
                                tenantId,
                                List.of(section)),
                        request,
                        List.of(entity));

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getPreviousStartTs());
        assertEquals(199L, result.get(0).getPreviousEndTs());
        assertEquals(
                15.0,
                result.get(0)
                        .getPreviousPoints()
                        .get(0)
                        .getValue(),
                0.000001);
    }

    @Test
    void dataServiceComposesResultsAndClosesTelemetryScope() {
        ReportEntityResolverService resolver =
                mock(ReportEntityResolverService.class);

        ReportKpiService kpiService =
                mock(ReportKpiService.class);

        ReportChartService chartService =
                mock(ReportChartService.class);

        ReportTableService tableService =
                mock(ReportTableService.class);

        ReportAlarmService alarmService =
                mock(ReportAlarmService.class);

        ReportTelemetryReadCache cache =
                new ReportTelemetryReadCache();

        DefaultReportDataService service =
                new DefaultReportDataService(
                        resolver,
                        kpiService,
                        chartService,
                        tableService,
                        alarmService,
                        cache);

        TenantId tenantId =
                TenantId.fromUUID(UUID.randomUUID());

        ReportTemplate template =
                template(
                        tenantId,
                        List.of());

        GenerateReportRequest request =
                request(1L, 100L);

        List<ReportTargetEntity> entities =
                List.of(target());

        List<ReportKpi> kpis =
                List.of(new ReportKpi());

        List<ReportTimeSeries> series =
                List.of(new ReportTimeSeries());

        List<ReportTable> tables =
                List.of(new ReportTable());

        List<ReportAlarmItem> alarms =
                List.of(new ReportAlarmItem());

        when(resolver.resolveEntities(template, request))
                .thenReturn(entities);

        when(kpiService.buildKpis(template, request, entities))
                .thenReturn(kpis);

        when(chartService.buildTimeSeries(template, request, entities))
                .thenReturn(series);

        when(tableService.buildTables(template, request, entities))
                .thenReturn(tables);

        when(alarmService.findAlarms(template, request, entities))
                .thenReturn(alarms);

        ReportDataResult result =
                service.collectReportData(
                        template,
                        request);

        assertSame(entities, result.getEntities());
        assertSame(kpis, result.getKpis());
        assertSame(series, result.getTimeSeries());
        assertSame(tables, result.getTables());
        assertSame(alarms, result.getAlarms());

        verify(resolver).resolveEntities(template, request);
        verify(kpiService).buildKpis(template, request, entities);
        verify(chartService).buildTimeSeries(template, request, entities);
        verify(tableService).buildTables(template, request, entities);
        verify(alarmService).findAlarms(template, request, entities);

        AtomicInteger loads =
                new AtomicInteger();

        Supplier<List<ReportMetricPoint>> loader = () -> {
            loads.incrementAndGet();
            return List.of(point(1L, 1.0));
        };

        DeviceId deviceId =
                new DeviceId(UUID.randomUUID());

        cache.getOrLoad(
                tenantId,
                deviceId,
                "temperature",
                1L,
                100L,
                null,
                null,
                null,
                null,
                loader);

        cache.getOrLoad(
                tenantId,
                deviceId,
                "temperature",
                1L,
                100L,
                null,
                null,
                null,
                null,
                loader);

        assertEquals(2, loads.get());
    }

    @Test
    void payloadContainsOrderedSectionsAndExtendedAggregation() {
        ReportDataService dataService =
                mock(ReportDataService.class);

        DefaultReportPayloadBuilderService service =
                new DefaultReportPayloadBuilderService(
                        objectMapper,
                        dataService,
                        metadataService);

        TenantId tenantId =
                TenantId.fromUUID(UUID.randomUUID());

        ReportSectionConfig second =
                section(
                        ReportSectionType.TABLE,
                        "second",
                        null,
                        objectMapper.createObjectNode());

        ReportSectionConfig first =
                section(
                        ReportSectionType.KPI_GRID,
                        "first",
                        1,
                        objectMapper.createObjectNode());

        ReportTemplate template =
                template(
                        tenantId,
                        Arrays.asList(
                                second,
                                null,
                                first));

        template.setName("Composition test");

        GenerateReportRequest request =
                request(1L, 100L);

        ReportKpi kpi =
                new ReportKpi();

        kpi.setKey("temperature");
        kpi.setValue(10.0);
        kpi.setAggregation(
                ReportKpiAggregationType.FIRST);

        ReportDataResult dataResult =
                new ReportDataResult();

        dataResult.setKpis(List.of(kpi));

        when(dataService.collectReportData(
                template,
                request))
                .thenReturn(dataResult);

        JsonNode payload =
                service.buildPayload(
                        template,
                        request);

        assertEquals(
                "Composition test",
                payload.path("meta")
                        .path("templateName")
                        .asText());

        assertEquals(
                1L,
                payload.path("period")
                        .path("startTs")
                        .asLong());

        assertEquals(2, payload.path("sections").size());

        assertEquals(
                "first",
                payload.path("sections")
                        .get(0)
                        .path("key")
                        .asText());

        assertEquals(
                "second",
                payload.path("sections")
                        .get(1)
                        .path("key")
                        .asText());

        assertEquals(
                "FIRST",
                payload.path("summary")
                        .path("kpis")
                        .get(0)
                        .path("aggregation")
                        .asText());
    }

    @Test
    void payloadRejectsMissingTemplateAndInvalidRange() {
        ReportDataService dataService =
                mock(ReportDataService.class);

        DefaultReportPayloadBuilderService service =
                new DefaultReportPayloadBuilderService(
                        objectMapper,
                        dataService,
                        metadataService);

        ReportServiceException missingTemplate =
                assertThrows(
                        ReportServiceException.class,
                        () -> service.buildPayload(
                                null,
                                request(1L, 100L)));

        assertEquals(
                ReportErrorCode.TEMPLATE_NOT_FOUND,
                missingTemplate.getErrorCode());

        ReportServiceException invalidRange =
                assertThrows(
                        ReportServiceException.class,
                        () -> service.buildPayload(
                                template(
                                        TenantId.fromUUID(
                                                UUID.randomUUID()),
                                        List.of()),
                                request(100L, 1L)));

        assertEquals(
                ReportErrorCode.INVALID_TIME_RANGE,
                invalidRange.getErrorCode());

        verifyNoInteractions(dataService);
    }

    @Test
    void malformedCompositionConfigurationProducesTypedErrors() {
        ReportTelemetryService telemetryService =
                mock(ReportTelemetryService.class);

        ReportVariableConfigService variableConfigService =
                mock(ReportVariableConfigService.class);

        ReportVariableSeriesService variableSeriesService =
                mock(ReportVariableSeriesService.class);

        when(variableConfigService.extractVariables(
                any(JsonNode.class)))
                .thenReturn(List.of());

        TenantId tenantId =
                TenantId.fromUUID(UUID.randomUUID());

        List<ReportTargetEntity> entities =
                List.of(target());

        GenerateReportRequest request =
                request(1L, 100L);

        ObjectNode chartConfig =
                objectMapper.createObjectNode();

        chartConfig.putArray("items")
                .addObject()
                .put("key", "temperature")
                .put("aggregation", "INVALID");

        DefaultReportChartService chartService =
                new DefaultReportChartService(
                        telemetryService,
                        objectMapper,
                        metadataService,
                        variableConfigService,
                        variableSeriesService);

        ReportServiceException chartError =
                assertThrows(
                        ReportServiceException.class,
                        () -> chartService.buildTimeSeries(
                                template(
                                        tenantId,
                                        List.of(
                                                section(
                                                        ReportSectionType.CHART,
                                                        "chart",
                                                        1,
                                                        chartConfig))),
                                request,
                                entities));

        assertEquals(
                ReportErrorCode.PAYLOAD_BUILD_FAILED,
                chartError.getErrorCode());

        ObjectNode tableConfig =
                objectMapper.createObjectNode();

        tableConfig.putArray("columns")
                .addObject()
                .put("key", "temperature")
                .put("aggregation", "INVALID");

        DefaultReportTableService tableService =
                new DefaultReportTableService(
                        telemetryService,
                        calculationSupport,
                        objectMapper,
                        metadataService,
                        variableConfigService,
                        variableSeriesService,
                        statisticsService);

        ReportServiceException tableError =
                assertThrows(
                        ReportServiceException.class,
                        () -> tableService.buildTables(
                                template(
                                        tenantId,
                                        List.of(
                                                section(
                                                        ReportSectionType.TABLE,
                                                        "table",
                                                        1,
                                                        tableConfig))),
                                request,
                                entities));

        assertEquals(
                ReportErrorCode.PAYLOAD_BUILD_FAILED,
                tableError.getErrorCode());

        ObjectNode kpiConfig =
                objectMapper.createObjectNode();

        kpiConfig.putArray("items")
                .addObject()
                .put("key", "temperature")
                .put("aggregation", "INVALID");

        DefaultReportKpiService kpiService =
                new DefaultReportKpiService(
                        telemetryService,
                        calculationSupport,
                        objectMapper,
                        metadataService,
                        variableConfigService,
                        variableSeriesService);

        ReportServiceException kpiError =
                assertThrows(
                        ReportServiceException.class,
                        () -> kpiService.buildKpis(
                                template(
                                        tenantId,
                                        List.of(
                                                section(
                                                        ReportSectionType.KPI_GRID,
                                                        "kpi",
                                                        1,
                                                        kpiConfig))),
                                request,
                                entities));

        assertEquals(
                ReportErrorCode.PAYLOAD_BUILD_FAILED,
                kpiError.getErrorCode());
    }

    private ReportMetricPoint point(
            Long timestamp,
            Double value) {
        return new ReportMetricPoint(
                timestamp,
                value);
    }

    private ReportTargetEntity target() {
        ReportTargetEntity target =
                new ReportTargetEntity();

        target.setEntityId(UUID.randomUUID());
        target.setEntityType("DEVICE");
        target.setName("Device");

        return target;
    }

    private GenerateReportRequest request(
            long startTs,
            long endTs) {
        GenerateReportRequest request =
                new GenerateReportRequest();

        request.setStartTs(startTs);
        request.setEndTs(endTs);

        return request;
    }

    private ReportSectionConfig section(
            ReportSectionType type,
            String key,
            Integer order,
            JsonNode config) {
        ReportSectionConfig section =
                new ReportSectionConfig();

        section.setType(type);
        section.setKey(key);
        section.setTitle(key);
        section.setOrder(order);
        section.setVisible(true);
        section.setConfig(config);

        return section;
    }

    private ReportTemplate template(
            TenantId tenantId,
            List<ReportSectionConfig> sections) {
        ReportTemplate template =
                new ReportTemplate();

        template.setTenantId(tenantId);
        template.setSections(sections);

        return template;
    }

    private ReportTimeSeries series(
            ReportTargetEntity entity,
            String key,
            List<ReportMetricPoint> points) {
        ReportTimeSeries series =
                new ReportTimeSeries();

        series.setEntityId(entity.getEntityId());
        series.setEntityType(entity.getEntityType());
        series.setEntityName(entity.getName());
        series.setKey(key);
        series.setLabel(key);
        series.setPoints(points);

        return series;
    }
}
