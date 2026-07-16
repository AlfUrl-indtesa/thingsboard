/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportAggregationType;
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
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultReportChartService implements ReportChartService {

        private final ReportTelemetryService reportTelemetryService;
        private final ObjectMapper objectMapper;
        private final ReportVariableMetadataService variableMetadataService;
        private final ReportVariableConfigService variableConfigService;

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
                                        || Boolean.FALSE.equals(section.getVisible())) {
                                continue;
                        }

                        List<ReportVariableConfig> variables = variableConfigService.extractVariables(
                                        section.getConfig());

                        List<ReportVariableConfig> chartVariables = variables.stream()
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

                                continue;
                        }

                        List<ReportChartQuery> queries = extractChartQueries(section.getConfig());

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
                                if (!matchesEntity(variable, entity)) {
                                        continue;
                                }

                                ReportTelemetryQuery telemetryQuery = buildTelemetryQuery(
                                                variable,
                                                request);

                                ReportTimeSeries series = reportTelemetryService.findSeries(
                                                tenantId,
                                                entity,
                                                telemetryQuery);

                                if (series == null) {
                                        continue;
                                }

                                applyVariableMetadata(
                                                series,
                                                variable);

                                List<ReportMetricPoint> currentPoints = convertPoints(
                                                series.getPoints(),
                                                variable);

                                series.setPoints(currentPoints);

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
                                        "Unable to calculate previous report period. " +
                                                        "entityId={}, key={}, startTs={}, endTs={}",
                                        entity != null
                                                        ? entity.getEntityId()
                                                        : null,
                                        variable.getKey(),
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

                ReportTelemetryQuery previousQuery = buildTelemetryQuery(
                                variable,
                                previousPeriod.startTs,
                                previousPeriod.endTs);

                try {
                        ReportTimeSeries previousSeries = reportTelemetryService.findSeries(
                                        tenantId,
                                        entity,
                                        previousQuery);

                        List<ReportMetricPoint> previousPoints = previousSeries == null
                                        ? new ArrayList<>()
                                        : convertPoints(
                                                        previousSeries.getPoints(),
                                                        variable);

                        currentSeries.setPreviousPoints(
                                        previousPoints);

                        log.info(
                                        "Report previous-period comparison: " +
                                                        "entityId={}, key={}, " +
                                                        "currentRange=[{},{}], " +
                                                        "previousRange=[{},{}], " +
                                                        "currentPoints={}, previousPoints={}",
                                        entity.getEntityId(),
                                        variable.getKey(),
                                        request.getStartTs(),
                                        request.getEndTs(),
                                        previousPeriod.startTs,
                                        previousPeriod.endTs,
                                        sizeOf(currentSeries.getPoints()),
                                        sizeOf(previousPoints));
                } catch (RuntimeException exception) {
                        /*
                         * Una falla en el periodo anterior no debe impedir
                         * la generación del reporte actual.
                         */
                        currentSeries.setPreviousPoints(
                                        new ArrayList<>());

                        log.warn(
                                        "Failed to read previous report period. " +
                                                        "entityId={}, key={}, previousRange=[{},{}]",
                                        entity != null
                                                        ? entity.getEntityId()
                                                        : null,
                                        variable.getKey(),
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
                 * La consulta adicional sólo se realiza cuando
                 * el análisis avanzado está habilitado.
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
                 * Periodo anterior adyacente:
                 *
                 * actual:
                 * [currentStartTs, currentEndTs]
                 *
                 * anterior:
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

        private List<ReportTimeSeries> buildSeriesForQuery(
                        TenantId tenantId,
                        GenerateReportRequest request,
                        List<ReportTargetEntity> entities,
                        ReportChartQuery query) {

                List<ReportTimeSeries> result = new ArrayList<>();

                if (entities == null
                                || entities.isEmpty()) {
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

        private ReportTelemetryQuery buildTelemetryQuery(
                        ReportVariableConfig variable,
                        GenerateReportRequest request) {

                Long startTs = request != null
                                ? request.getStartTs()
                                : null;

                Long endTs = request != null
                                ? request.getEndTs()
                                : null;

                return buildTelemetryQuery(
                                variable,
                                startTs,
                                endTs);
        }

        private ReportTelemetryQuery buildTelemetryQuery(
                        ReportVariableConfig variable,
                        Long startTs,
                        Long endTs) {

                ReportVariableMetadata metadata = variableMetadataService.resolve(
                                variable.getKey(),
                                variable.getLabel(),
                                variable.getUnit());

                ReportTelemetryQuery telemetryQuery = new ReportTelemetryQuery();

                telemetryQuery.setKey(
                                variable.getKey());

                telemetryQuery.setLabel(
                                metadata.getLabel());

                telemetryQuery.setUnit(
                                metadata.getUnit());

                telemetryQuery.setStartTs(startTs);
                telemetryQuery.setEndTs(endTs);

                telemetryQuery.setAggregation(
                                ReportAggregationType.NONE);

                telemetryQuery.setOrderBy("ASC");

                return telemetryQuery;
        }

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

                telemetryQuery.setStartTs(
                                request.getStartTs());

                telemetryQuery.setEndTs(
                                request.getEndTs());

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

        private void applyVariableMetadata(
                        ReportTimeSeries series,
                        ReportVariableConfig variable) {

                ReportVariableMetadata metadata = variableMetadataService.resolve(
                                variable.getKey(),
                                variable.getLabel(),
                                variable.getUnit());

                series.setKey(
                                variable.getKey());

                series.setLabel(
                                metadata.getLabel());

                series.setUnit(
                                metadata.getUnit());

                series.setGranularity(
                                variable.getGranularity() != null
                                                && !variable.getGranularity().isBlank()
                                                                ? variable.getGranularity()
                                                                                .toUpperCase()
                                                                : "FULL");

                if (variable.getEntityName() != null
                                && !variable.getEntityName().isBlank()) {
                        series.setEntityName(
                                        variable.getEntityName());
                }
        }

        private List<ReportMetricPoint> convertPoints(
                        List<ReportMetricPoint> points,
                        ReportVariableConfig variable) {

                List<ReportMetricPoint> converted = new ArrayList<>();

                if (points == null
                                || points.isEmpty()) {
                        return converted;
                }

                for (ReportMetricPoint point : points) {
                        if (point == null
                                        || point.getValue() == null) {
                                continue;
                        }

                        converted.add(
                                        new ReportMetricPoint(
                                                        point.getTs(),
                                                        variableConfigService
                                                                        .applyConversion(
                                                                                        variable,
                                                                                        point.getValue())));
                }

                return converted;
        }

        private boolean matchesEntity(
                        ReportVariableConfig variable,
                        ReportTargetEntity entity) {

                if (variable == null
                                || variable.getEntityId() == null
                                || variable.getEntityId()
                                                .getEntityType() == null
                                || entity == null
                                || entity.getEntityId() == null
                                || entity.getEntityType() == null) {
                        return false;
                }

                UUID variableUuid = variable.getEntityId().getId();

                UUID entityUuid = entity.getEntityId();

                String variableType = variable.getEntityId()
                                .getEntityType()
                                .name();

                String entityType = entity.getEntityType();

                return Objects.equals(
                                variableUuid,
                                entityUuid)
                                && Objects.equals(
                                                variableType,
                                                entityType);
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
                        ReportChartQuery query = objectMapper.convertValue(
                                        itemNode,
                                        ReportChartQuery.class);

                        result.add(query);
                }

                return result;
        }

        private int sizeOf(
                        List<?> values) {

                return values != null
                                ? values.size()
                                : 0;
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

        private boolean isChartSection(
                        ReportSectionConfig section) {

                if (section == null
                                || section.getType() == null) {
                        return false;
                }

                return section.getType() == ReportSectionType.CHART
                                || section.getType() == ReportSectionType.TIME_SERIES_CHART;
        }
}