/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTelemetryQuery;
import org.thingsboard.server.common.data.report.ReportTimeSeries;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultReportTelemetryService
        implements ReportTelemetryService {

    private final TbTelemetryReader tbTelemetryReader;

    private final ReportEntityIdFactory reportEntityIdFactory;

    private final ReportTelemetryReadCache reportTelemetryReadCache;

    @Override
    public ReportTimeSeries findSeries(
            TenantId tenantId,
            ReportTargetEntity entity,
            ReportTelemetryQuery query) {

        EntityId entityId = reportEntityIdFactory.toEntityId(
                entity);

        ReportTimeSeries series = new ReportTimeSeries();

        series.setEntityId(
                entity.getEntityId());

        series.setEntityType(
                entity.getEntityType());

        series.setEntityName(
                entity.getName());

        series.setKey(
                query.getKey());

        series.setLabel(
                query.getLabel() != null
                        ? query.getLabel()
                        : query.getKey());

        series.setUnit(
                query.getUnit());

        series.setAggregation(
                query.getAggregation());

        series.setStartTs(
                query.getStartTs());

        series.setEndTs(
                query.getEndTs());

        series.setPoints(
                reportTelemetryReadCache.getOrLoad(
                        tenantId,
                        entityId,
                        query.getKey(),
                        query.getStartTs(),
                        query.getEndTs(),
                        query.getInterval(),
                        query.getLimit(),
                        query.getAggregation(),
                        query.getOrderBy(),
                        () -> tbTelemetryReader.readTimeseries(
                                tenantId,
                                entityId,
                                query.getKey(),
                                query.getStartTs(),
                                query.getEndTs(),
                                query.getInterval(),
                                query.getLimit(),
                                query.getAggregation(),
                                query.getOrderBy())));

        return series;
    }

    @Override
    public List<ReportTimeSeries> findSeries(
            TenantId tenantId,
            List<ReportTargetEntity> entities,
            List<ReportTelemetryQuery> queries) {

        List<ReportTimeSeries> result = new ArrayList<>();

        if (entities == null
                || entities.isEmpty()
                || queries == null
                || queries.isEmpty()) {
            return result;
        }

        for (ReportTargetEntity entity : entities) {
            for (ReportTelemetryQuery query : queries) {
                result.add(
                        findSeries(
                                tenantId,
                                entity,
                                query));
            }
        }

        return result;
    }
}