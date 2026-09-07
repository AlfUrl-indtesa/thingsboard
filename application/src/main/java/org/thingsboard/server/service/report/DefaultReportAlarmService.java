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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportAlarmItem;
import org.thingsboard.server.common.data.report.ReportAlarmQuery;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportSectionType;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultReportAlarmService implements ReportAlarmService {

    private final TbAlarmReader tbAlarmReader;
    private final ObjectMapper objectMapper;

    @Override
    public List<ReportAlarmItem> findAlarms(ReportTemplate template,
                                            GenerateReportRequest request,
                                            List<ReportTargetEntity> entities) {
        List<ReportAlarmItem> result = new ArrayList<>();

        if (template == null || template.getSections() == null || template.getSections().isEmpty()) {
            return result;
        }

        if (entities == null || entities.isEmpty()) {
            return result;
        }

        TenantId tenantId = template.getTenantId();
        validateRequest(tenantId, request);

        for (ReportSectionConfig section : template.getSections()) {
            if (section == null
                    || section.getType() != ReportSectionType.ALARM_LIST
                    || !Boolean.TRUE.equals(section.getVisible())) {
                continue;
            }

            ReportAlarmQuery query = extractAlarmQuery(section.getConfig());

            for (ReportTargetEntity entity : entities) {
                List<ReportAlarmItem> alarms =
                        tbAlarmReader.readAlarms(
                                tenantId,
                                entity,
                                request.getStartTs(),
                                request.getEndTs(),
                                query);

                if (alarms != null && !alarms.isEmpty()) {
                    result.addAll(alarms);
                }
            }
        }

        result.removeIf(item -> item == null);

        result.sort(
                Comparator.comparing(
                        ReportAlarmItem::getTimestamp,
                        Comparator.nullsLast(
                                Comparator.reverseOrder())));

        return result;
    }

    private ReportAlarmQuery extractAlarmQuery(JsonNode config) {
        if (config == null || config.isNull()) {
            return new ReportAlarmQuery();
        }

        try {
            ReportAlarmQuery query =
                    objectMapper.convertValue(
                            config,
                            ReportAlarmQuery.class);

            return query != null
                    ? query
                    : new ReportAlarmQuery();
        } catch (IllegalArgumentException exception) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "Invalid report alarm configuration",
                    exception);
        }
    }

    private void validateRequest(
            TenantId tenantId,
            GenerateReportRequest request) {

        if (tenantId == null) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "TenantId is required for report alarm collection");
        }

        if (request == null
                || request.getStartTs() == null
                || request.getEndTs() == null
                || request.getStartTs() >= request.getEndTs()) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_TIME_RANGE,
                    "Invalid report alarm time range");
        }
    }
}