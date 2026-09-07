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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportAggregationType;
import org.thingsboard.server.common.data.report.ReportMetricPoint;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTelemetryQuery;
import org.thingsboard.server.common.data.report.ReportTimeSeries;
import org.thingsboard.server.common.data.report.ReportVariableConfig;
import org.thingsboard.server.common.data.report.ReportVariableMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ReportVariableSeriesService {

    private final ReportTelemetryService reportTelemetryService;

    private final ReportEntityIdFactory reportEntityIdFactory;

    private final ReportVariableMetadataService variableMetadataService;

    private final ReportVariableConfigService variableConfigService;

    public boolean matchesEntity(
            ReportVariableConfig variable,
            ReportTargetEntity entity) {

        if (variable == null
                || variable.getEntityId() == null
                || entity == null) {
            return false;
        }

        EntityId entityId;

        try {
            entityId =
                    reportEntityIdFactory.toEntityId(entity);
        } catch (RuntimeException exception) {
            return false;
        }

        return variableConfigService.matchesEntity(
                variable,
                entityId
        );
    }

    public ReportTimeSeries findSeries(
            TenantId tenantId,
            ReportTargetEntity entity,
            ReportVariableConfig variable,
            GenerateReportRequest request) {

        Long startTs =
                request != null
                        ? request.getStartTs()
                        : null;

        Long endTs =
                request != null
                        ? request.getEndTs()
                        : null;

        return findSeries(
                tenantId,
                entity,
                variable,
                startTs,
                endTs
        );
    }

    public ReportTimeSeries findSeries(
            TenantId tenantId,
            ReportTargetEntity entity,
            ReportVariableConfig variable,
            Long startTs,
            Long endTs) {

        if (variable == null
                || variable.getKey() == null
                || variable.getKey().isBlank()
                || !matchesEntity(variable, entity)) {
            return null;
        }

        ReportVariableMetadata metadata =
                variableMetadataService.resolve(
                        variable.getKey(),
                        variable.getLabel(),
                        variable.getUnit()
                );

        ReportTelemetryQuery telemetryQuery =
                new ReportTelemetryQuery();

        telemetryQuery.setKey(
                variable.getKey()
        );

        telemetryQuery.setLabel(
                metadata.getLabel()
        );

        telemetryQuery.setUnit(
                metadata.getUnit()
        );

        telemetryQuery.setStartTs(startTs);
        telemetryQuery.setEndTs(endTs);

        telemetryQuery.setAggregation(
                ReportAggregationType.NONE
        );

        telemetryQuery.setOrderBy("ASC");

        ReportTimeSeries series =
                reportTelemetryService.findSeries(
                        tenantId,
                        entity,
                        telemetryQuery
                );

        if (series == null) {
            return null;
        }

        series.setKey(
                variable.getKey()
        );

        series.setLabel(
                metadata.getLabel()
        );

        series.setUnit(
                metadata.getUnit()
        );

        series.setGranularity(
                resolveGranularity(
                        variable.getGranularity()
                )
        );

        if (variable.getEntityName() != null
                && !variable.getEntityName().isBlank()) {
            series.setEntityName(
                    variable.getEntityName()
            );
        }

        series.setPoints(
                convertPoints(
                        series.getPoints(),
                        variable
                )
        );

        return series;
    }

    private List<ReportMetricPoint> convertPoints(
            List<ReportMetricPoint> points,
            ReportVariableConfig variable) {

        List<ReportMetricPoint> converted =
                new ArrayList<>();

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
                            variableConfigService.applyConversion(
                                    variable,
                                    point.getValue()
                            )
                    )
            );
        }

        return converted;
    }

    private String resolveGranularity(
            String granularity) {

        if (granularity == null
                || granularity.isBlank()) {
            return "FULL";
        }

        return granularity
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}