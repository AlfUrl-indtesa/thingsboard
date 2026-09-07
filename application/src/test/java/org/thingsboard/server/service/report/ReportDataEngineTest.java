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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportAggregationType;
import org.thingsboard.server.common.data.report.ReportAlarmItem;
import org.thingsboard.server.common.data.report.ReportAlarmQuery;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportKpiAggregationType;
import org.thingsboard.server.common.data.report.ReportMetricPoint;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportSectionType;
import org.thingsboard.server.common.data.report.ReportSeriesStatistics;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTelemetryQuery;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportTimeSeries;
import org.thingsboard.server.common.data.report.ReportVariableConfig;
import org.thingsboard.server.common.data.report.ReportVariableMetadata;
import org.thingsboard.server.dao.alarm.AlarmService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportDataEngineTest {

    @Test
    void calculatesStatisticsChronologicallyAndIgnoresInvalidPoints() {
        DefaultReportSeriesStatisticsService service =
                new DefaultReportSeriesStatisticsService();

        ReportSeriesStatistics statistics = service.calculate(
                Arrays.asList(
                        point(30L, 3.0),
                        null,
                        point(40L, Double.NaN),
                        point(10L, 1.0),
                        point(20L, 2.0)));

        assertTrue(statistics.isHasData());
        assertEquals(5, statistics.getTotalPointCount());
        assertEquals(3, statistics.getValidPointCount());
        assertEquals(2, statistics.getInvalidPointCount());
        assertEquals(1.0, statistics.getMin(), 0.000001);
        assertEquals(3.0, statistics.getMax(), 0.000001);
        assertEquals(2.0, statistics.getAvg(), 0.000001);
        assertEquals(6.0, statistics.getSum(), 0.000001);
        assertEquals(1.0, statistics.getFirst(), 0.000001);
        assertEquals(3.0, statistics.getLast(), 0.000001);
        assertEquals(2.0, statistics.getDelta(), 0.000001);
        assertEquals(
                3.0,
                service.resolveValue(
                        statistics,
                        ReportKpiAggregationType.COUNT),
                0.000001);
    }

    @Test
    void returnsNoStatisticsWhenEveryPointIsInvalid() {
        DefaultReportSeriesStatisticsService service =
                new DefaultReportSeriesStatisticsService();

        ReportSeriesStatistics statistics = service.calculate(
                Arrays.asList(
                        null,
                        point(1L, null),
                        point(2L, Double.POSITIVE_INFINITY)));

        assertFalse(statistics.isHasData());
        assertEquals(0, statistics.getValidPointCount());
        assertEquals(3, statistics.getInvalidPointCount());
        assertNull(
                service.resolveValue(
                        statistics,
                        ReportKpiAggregationType.AVG));
    }

    @Test
    void cachesEquivalentQueriesInsideScopeAndReturnsDefensiveCopies() {
        ReportTelemetryReadCache cache =
                new ReportTelemetryReadCache();

        TenantId tenantId =
                TenantId.fromUUID(UUID.randomUUID());

        DeviceId deviceId =
                new DeviceId(UUID.randomUUID());

        AtomicInteger loadCount =
                new AtomicInteger();

        Supplier<List<ReportMetricPoint>> loader = () -> {
            loadCount.incrementAndGet();
            return List.of(point(1L, 10.0));
        };

        try (ReportTelemetryReadCache.Scope ignored =
                     cache.openScope()) {

            List<ReportMetricPoint> first =
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

            first.get(0).setValue(999.0);

            List<ReportMetricPoint> second =
                    cache.getOrLoad(
                            tenantId,
                            deviceId,
                            " temperature ",
                            1L,
                            100L,
                            null,
                            null,
                            ReportAggregationType.NONE,
                            " asc ",
                            loader);

            assertEquals(1, loadCount.get());
            assertEquals(
                    10.0,
                    second.get(0).getValue(),
                    0.000001);
        }

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

        assertEquals(2, loadCount.get());
    }

    @Test
    void resolvesKnownAndCleanedVariableMetadata() {
        DefaultReportVariableMetadataService service =
                new DefaultReportVariableMetadataService();

        ReportVariableMetadata pressure =
                service.resolve(
                        "Ch_A1_A1a_psi",
                        null,
                        null);

        assertEquals("Presión", pressure.getLabel());
        assertEquals("psi", pressure.getUnit());

        ReportVariableMetadata temperature =
                service.resolve(
                        "temperature_avg",
                        "Promedio de Temperatura",
                        null);

        assertEquals("Temperatura", temperature.getLabel());
        assertEquals("°C", temperature.getUnit());
    }

    @Test
    void readersEnforceFiniteValuesAndAlarmCaps() {
        DefaultTbTelemetryReader telemetryReader =
                new DefaultTbTelemetryReader(
                        mock(TimeseriesService.class));

        Double finite =
                ReflectionTestUtils.invokeMethod(
                        telemetryReader,
                        "toDouble",
                        "12.5");

        Double nan =
                ReflectionTestUtils.invokeMethod(
                        telemetryReader,
                        "toDouble",
                        "NaN");

        Double infinity =
                ReflectionTestUtils.invokeMethod(
                        telemetryReader,
                        "toDouble",
                        "Infinity");

        assertEquals(12.5, finite, 0.000001);
        assertNull(nan);
        assertNull(infinity);

        DefaultTbAlarmReader alarmReader =
                new DefaultTbAlarmReader(
                        mock(AlarmService.class),
                        mock(ReportEntityIdFactory.class));

        Integer cappedLimit =
                ReflectionTestUtils.invokeMethod(
                        alarmReader,
                        "resolveLimit",
                        Integer.MAX_VALUE);

        Integer defaultLimit =
                ReflectionTestUtils.invokeMethod(
                        alarmReader,
                        "resolveLimit",
                        -1);

        assertEquals(10_000, cappedLimit);
        assertEquals(100, defaultLimit);
    }

    @Test
    void telemetryServiceRejectsMissingQueryAndInvalidRange() {
        TbTelemetryReader reader =
                mock(TbTelemetryReader.class);

        ReportEntityIdFactory factory =
                mock(ReportEntityIdFactory.class);

        DefaultReportTelemetryService service =
                new DefaultReportTelemetryService(
                        reader,
                        factory,
                        new ReportTelemetryReadCache());

        TenantId tenantId =
                TenantId.fromUUID(UUID.randomUUID());

        ReportTargetEntity target =
                target();

        ReportServiceException missingQuery =
                assertThrows(
                        ReportServiceException.class,
                        () -> service.findSeries(
                                tenantId,
                                target,
                                null));

        assertEquals(
                ReportErrorCode.DATA_COLLECTION_FAILED,
                missingQuery.getErrorCode());

        ReportTelemetryQuery invalidRange =
                new ReportTelemetryQuery();

        invalidRange.setKey("temperature");
        invalidRange.setStartTs(20L);
        invalidRange.setEndTs(10L);

        ReportServiceException rangeError =
                assertThrows(
                        ReportServiceException.class,
                        () -> service.findSeries(
                                tenantId,
                                target,
                                invalidRange));

        assertEquals(
                ReportErrorCode.INVALID_TIME_RANGE,
                rangeError.getErrorCode());

        verifyNoInteractions(factory, reader);
    }

    @Test
    void variableSeriesAppliesMetadataConversionAndGranularity() {
        ReportTelemetryService telemetryService =
                mock(ReportTelemetryService.class);

        ReportEntityIdFactory factory =
                mock(ReportEntityIdFactory.class);

        ReportVariableMetadataService metadataService =
                new DefaultReportVariableMetadataService();

        ReportVariableConfigService configService =
                new DefaultReportVariableConfigService(
                        new ObjectMapper());

        ReportVariableSeriesService service =
                new ReportVariableSeriesService(
                        telemetryService,
                        factory,
                        metadataService,
                        configService);

        TenantId tenantId =
                TenantId.fromUUID(UUID.randomUUID());

        DeviceId deviceId =
                new DeviceId(UUID.randomUUID());

        ReportTargetEntity target =
                target();

        target.setEntityId(deviceId.getId());

        ReportVariableConfig variable =
                new ReportVariableConfig();

        variable.setEntityId(deviceId);
        variable.setEntityName("Compresor principal");
        variable.setKey("Ch_A1_A1a_psi");
        variable.setScale(2.0);
        variable.setOffset(1.0);
        variable.setGranularity(" raw ");

        ReportTimeSeries raw =
                new ReportTimeSeries();

        raw.setPoints(List.of(point(10L, 5.0)));

        when(factory.toEntityId(target))
                .thenReturn(deviceId);

        when(telemetryService.findSeries(
                eq(tenantId),
                same(target),
                any(ReportTelemetryQuery.class)))
                .thenReturn(raw);

        ReportTimeSeries result =
                service.findSeries(
                        tenantId,
                        target,
                        variable,
                        1L,
                        100L);

        assertEquals("Presión", result.getLabel());
        assertEquals("psi", result.getUnit());
        assertEquals("RAW", result.getGranularity());
        assertEquals(
                "Compresor principal",
                result.getEntityName());
        assertEquals(
                11.0,
                result.getPoints().get(0).getValue(),
                0.000001);
    }

    @Test
    void alarmServiceSkipsInvalidItemsAndSortsNullTimestampsLast() {
        TbAlarmReader reader =
                mock(TbAlarmReader.class);

        ObjectMapper objectMapper =
                mock(ObjectMapper.class);

        DefaultReportAlarmService service =
                new DefaultReportAlarmService(
                        reader,
                        objectMapper);

        TenantId tenantId =
                TenantId.fromUUID(UUID.randomUUID());

        ReportTargetEntity target =
                target();

        ReportSectionConfig hidden =
                alarmSection(false);

        ReportSectionConfig visible =
                alarmSection(true);

        ReportTemplate template =
                new ReportTemplate();

        template.setTenantId(tenantId);
        template.setSections(
                Arrays.asList(
                        null,
                        hidden,
                        visible));

        GenerateReportRequest request =
                request(10L, 20L);

        ReportAlarmQuery query =
                new ReportAlarmQuery();

        ReportAlarmItem older =
                alarm(10L, "Older");

        ReportAlarmItem newer =
                alarm(30L, "Newer");

        ReportAlarmItem withoutTimestamp =
                alarm(null, "No timestamp");

        when(objectMapper.convertValue(
                visible.getConfig(),
                ReportAlarmQuery.class))
                .thenReturn(query);

        when(reader.readAlarms(
                tenantId,
                target,
                10L,
                20L,
                query))
                .thenReturn(
                        Arrays.asList(
                                older,
                                null,
                                withoutTimestamp,
                                newer));

        List<ReportAlarmItem> result =
                service.findAlarms(
                        template,
                        request,
                        List.of(target));

        assertEquals(3, result.size());
        assertSame(newer, result.get(0));
        assertSame(older, result.get(1));
        assertSame(withoutTimestamp, result.get(2));
    }

    @Test
    void alarmServiceReportsInvalidRequestAndConfiguration() {
        TbAlarmReader reader =
                mock(TbAlarmReader.class);

        ObjectMapper objectMapper =
                mock(ObjectMapper.class);

        DefaultReportAlarmService service =
                new DefaultReportAlarmService(
                        reader,
                        objectMapper);

        TenantId tenantId =
                TenantId.fromUUID(UUID.randomUUID());

        ReportSectionConfig section =
                alarmSection(true);

        ReportTemplate template =
                new ReportTemplate();

        template.setTenantId(tenantId);
        template.setSections(List.of(section));

        ReportTargetEntity target =
                target();

        ReportServiceException requestError =
                assertThrows(
                        ReportServiceException.class,
                        () -> service.findAlarms(
                                template,
                                null,
                                List.of(target)));

        assertEquals(
                ReportErrorCode.INVALID_TIME_RANGE,
                requestError.getErrorCode());

        when(objectMapper.convertValue(
                section.getConfig(),
                ReportAlarmQuery.class))
                .thenThrow(
                        new IllegalArgumentException(
                                "invalid config"));

        ReportServiceException configError =
                assertThrows(
                        ReportServiceException.class,
                        () -> service.findAlarms(
                                template,
                                request(10L, 20L),
                                List.of(target)));

        assertEquals(
                ReportErrorCode.DATA_COLLECTION_FAILED,
                configError.getErrorCode());

        verifyNoInteractions(reader);
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

    private ReportSectionConfig alarmSection(
            boolean visible) {
        ReportSectionConfig section =
                new ReportSectionConfig();

        section.setType(
                ReportSectionType.ALARM_LIST);

        section.setVisible(visible);

        section.setConfig(
                JsonNodeFactory.instance
                        .objectNode());

        return section;
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

    private ReportAlarmItem alarm(
            Long timestamp,
            String name) {
        ReportAlarmItem alarm =
                new ReportAlarmItem();

        alarm.setTimestamp(timestamp);
        alarm.setName(name);

        return alarm;
    }
}
