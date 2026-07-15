/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportDataResult;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportSectionConfig;

import java.util.ArrayList;
import java.util.List;

@Slf4j
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

                        List<String> sectionSummary = template.getSections() == null
                                        ? List.of()
                                        : template.getSections()
                                                        .stream()
                                                        .map(this::summarizeSection)
                                                        .toList();

                        log.info(
                                        "Report data collection started: " +
                                                        "templateId={}, templateName={}, " +
                                                        "sections={}, entities={}",
                                        template.getId(),
                                        template.getName(),
                                        sectionSummary,
                                        entities.size());

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

                        log.info(
                                        "Report data collection result: " +
                                                        "kpis={}, series={}, tables={}, alarms={}",
                                        result.getKpis() != null
                                                        ? result.getKpis().size()
                                                        : 0,
                                        result.getTimeSeries() != null
                                                        ? result.getTimeSeries().size()
                                                        : 0,
                                        result.getTables() != null
                                                        ? result.getTables().size()
                                                        : 0,
                                        result.getAlarms() != null
                                                        ? result.getAlarms().size()
                                                        : 0);

                        return result;
                }
        }
        private String summarizeSection(
                        ReportSectionConfig section) {

                if (section == null) {
                        return "null";
                }

                List<String> configFields = new ArrayList<>();

                if (section.getConfig() != null
                                && section.getConfig().isObject()) {

                        section.getConfig()
                                        .fieldNames()
                                        .forEachRemaining(
                                                        configFields::add);
                }

                return String.format(
                                "%s:%s:visible=%s:fields=%s",
                                section.getType(),
                                section.getKey(),
                                section.getVisible(),
                                configFields);
        }

}