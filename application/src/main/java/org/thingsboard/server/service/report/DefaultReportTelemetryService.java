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
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTelemetryQuery;
import org.thingsboard.server.common.data.report.ReportErrorCode;
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

        validate(tenantId, entity, query);

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

    private void validate(
            TenantId tenantId,
            ReportTargetEntity entity,
            ReportTelemetryQuery query) {

        if (tenantId == null) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "TenantId is required for telemetry query");
        }

        if (entity == null
                || entity.getEntityId() == null
                || entity.getEntityType() == null
                || entity.getEntityType().isBlank()) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_ENTITY_SCOPE,
                    "Valid target entity is required for telemetry query");
        }

        if (query == null
                || query.getKey() == null
                || query.getKey().isBlank()) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "Telemetry query and key are required");
        }

        if (query.getStartTs() == null
                || query.getEndTs() == null
                || query.getStartTs() >= query.getEndTs()) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_TIME_RANGE,
                    "Invalid telemetry time range");
        }
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