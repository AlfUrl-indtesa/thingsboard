/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportDataResult;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultReportDataService
                implements ReportDataService {

        private final ReportEntityResolverService reportEntityResolverService;

        private final ReportKpiService reportKpiService;

        private final ReportChartService reportChartService;

        private final ReportTableService reportTableService;

        private final ReportAlarmService reportAlarmService;

        private final ReportTelemetryReadCache reportTelemetryReadCache;

        @Override
        public ReportDataResult collectReportData(
                        ReportTemplate template,
                        GenerateReportRequest request) {

                /*
                 * La caché existe solamente durante esta generación.
                 * try-with-resources garantiza su liberación incluso si
                 * alguno de los servicios produce una excepción.
                 */
                try (
                                ReportTelemetryReadCache.Scope ignored = reportTelemetryReadCache.openScope()) {
                        List<ReportTargetEntity> entities = reportEntityResolverService.resolveEntities(
                                        template,
                                        request);

                        ReportDataResult result = new ReportDataResult();

                        result.setEntities(entities);

                        result.setKpis(
                                        reportKpiService.buildKpis(
                                                        template,
                                                        request,
                                                        entities));

                        result.setTimeSeries(
                                        reportChartService.buildTimeSeries(
                                                        template,
                                                        request,
                                                        entities));

                        result.setTables(
                                        reportTableService.buildTables(
                                                        template,
                                                        request,
                                                        entities));

                        result.setAlarms(
                                        reportAlarmService.findAlarms(
                                                        template,
                                                        request,
                                                        entities));

                        return result;
                }
        }
}