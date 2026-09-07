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

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportEntityFilter;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportTemplateStatus;
import org.thingsboard.server.common.data.report.ReportTimeRangeConfig;

import java.util.Objects;

@Service
public class DefaultReportValidationService implements ReportValidationService {

    @Override
    public void validateTemplateForSave(ReportTemplate reportTemplate) {
        if (reportTemplate == null) {
            throw new ReportServiceException(ReportErrorCode.UNKNOWN_ERROR, "Report template is null");
        }

        if (!StringUtils.hasText(reportTemplate.getName())) {
            throw new ReportServiceException(ReportErrorCode.UNKNOWN_ERROR, "Report template name is required");
        }

        if (reportTemplate.getType() == null) {
            throw new ReportServiceException(ReportErrorCode.UNKNOWN_ERROR, "Report template type is required");
        }

        if (reportTemplate.getScopeType() == null) {
            throw new ReportServiceException(ReportErrorCode.UNKNOWN_ERROR, "Report scope type is required");
        }

        if (reportTemplate.getEntityFilter() == null) {
            throw new ReportServiceException(ReportErrorCode.INVALID_ENTITY_SCOPE, "Entity filter is required");
        }

        if (CollectionUtils.isEmpty(reportTemplate.getSections())) {
            throw new ReportServiceException(ReportErrorCode.UNKNOWN_ERROR, "At least one report section is required");
        }

        validateEntityFilter(reportTemplate);
        validateSections(reportTemplate);
        validateTimeRangeConfig(reportTemplate.getDefaultTimeRange());
    }

    @Override
    public void validateTemplateForExecution(ReportTemplate reportTemplate) {
        validateTemplateForSave(reportTemplate);

        if (reportTemplate.getStatus() != ReportTemplateStatus.ACTIVE) {
            throw new ReportServiceException(
                    ReportErrorCode.TEMPLATE_DISABLED,
                    "Report template is not active");
        }
    }

    @Override
    public void validateGenerateRequest(GenerateReportRequest request) {
        if (request == null) {
            throw new ReportServiceException(ReportErrorCode.UNKNOWN_ERROR, "Generate report request is null");
        }

        if (request.getStartTs() == null || request.getEndTs() == null) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_TIME_RANGE,
                    "Start and end timestamps are required");
        }

        if (request.getStartTs() >= request.getEndTs()) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_TIME_RANGE,
                    "Start timestamp must be earlier than end timestamp");
        }
    }

    private void validateEntityFilter(ReportTemplate reportTemplate) {
        ReportEntityFilter entityFilter = reportTemplate.getEntityFilter();

        if (entityFilter.getScopeType() == null) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_ENTITY_SCOPE,
                    "Entity filter scope type is required");
        }

        if (reportTemplate.getScopeType() != entityFilter.getScopeType()) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_ENTITY_SCOPE,
                    "Template and entity filter scope types must match");
        }

        if (!StringUtils.hasText(entityFilter.getEntityType())) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_ENTITY_SCOPE,
                    "Report entity type is required");
        }

        if (!"DEVICE".equals(entityFilter.getEntityType())
                && !"ASSET".equals(entityFilter.getEntityType())) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_ENTITY_SCOPE,
                    "Only DEVICE and ASSET entities are currently supported");
        }

        switch (entityFilter.getScopeType()) {
            case FIXED_ENTITIES:
                validateFixedEntities(entityFilter);
                break;

            case CUSTOMER_ENTITIES:
                if (entityFilter.getCustomerId() == null) {
                    throw new ReportServiceException(
                            ReportErrorCode.INVALID_ENTITY_SCOPE,
                            "Customer entity scope requires a customerId");
                }

                if (reportTemplate.getCustomerId() != null
                        && !Objects.equals(
                                reportTemplate.getCustomerId(),
                                entityFilter.getCustomerId())) {
                    throw new ReportServiceException(
                            ReportErrorCode.INVALID_ENTITY_SCOPE,
                            "Template and entity filter customer scopes must match");
                }
                break;

            case TENANT_ENTITIES:
                if (reportTemplate.getCustomerId() != null
                        || entityFilter.getCustomerId() != null) {
                    throw new ReportServiceException(
                            ReportErrorCode.INVALID_ENTITY_SCOPE,
                            "Tenant entity scope cannot be restricted to a customer");
                }
                break;

            case CURRENT_CUSTOMER_ENTITIES:
                if (reportTemplate.getCustomerId() == null) {
                    throw new ReportServiceException(
                            ReportErrorCode.INVALID_ENTITY_SCOPE,
                            "Current customer scope requires a template customerId");
                }

                if (entityFilter.getCustomerId() != null
                        && !Objects.equals(
                                reportTemplate.getCustomerId(),
                                entityFilter.getCustomerId())) {
                    throw new ReportServiceException(
                            ReportErrorCode.INVALID_ENTITY_SCOPE,
                            "Template and entity filter customer scopes must match");
                }
                break;

            default:
                throw new ReportServiceException(
                        ReportErrorCode.INVALID_ENTITY_SCOPE,
                        "Unsupported report scope type");
        }
    }

    private void validateFixedEntities(ReportEntityFilter entityFilter) {
        if (CollectionUtils.isEmpty(entityFilter.getEntityIds())) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_ENTITY_SCOPE,
                    "Fixed entity scope requires entityIds");
        }

        for (EntityId entityId : entityFilter.getEntityIds()) {
            if (entityId == null
                    || entityId.getEntityType() == null
                    || !entityFilter.getEntityType().equals(
                            entityId.getEntityType().name())) {
                throw new ReportServiceException(
                        ReportErrorCode.INVALID_ENTITY_SCOPE,
                        "Every selected entity must match the report entity type");
            }
        }
    }

    private void validateSections(ReportTemplate reportTemplate) {
        boolean hasVisibleSection = false;

        for (ReportSectionConfig section : reportTemplate.getSections()) {
            if (!StringUtils.hasText(section.getKey())) {
                throw new ReportServiceException(
                        ReportErrorCode.UNKNOWN_ERROR,
                        "Section key is required");
            }
            if (section.getType() == null) {
                throw new ReportServiceException(
                        ReportErrorCode.UNKNOWN_ERROR,
                        "Section type is required");
            }
            if (!StringUtils.hasText(section.getTitle())) {
                throw new ReportServiceException(
                        ReportErrorCode.UNKNOWN_ERROR,
                        "Section title is required");
            }
            if (section.getOrder() == null || section.getOrder() < 0) {
                throw new ReportServiceException(
                        ReportErrorCode.UNKNOWN_ERROR,
                        "Section order must be greater than or equal to zero");
            }
            if (Boolean.TRUE.equals(section.getVisible())) {
                hasVisibleSection = true;
            }
        }

        if (!hasVisibleSection) {
            throw new ReportServiceException(
                    ReportErrorCode.UNKNOWN_ERROR,
                    "At least one visible report section is required");
        }
    }

    private void validateTimeRangeConfig(ReportTimeRangeConfig config) {
        if (config == null) {
            return;
        }

        if (config.getMode() == null) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_TIME_RANGE,
                    "Report time range mode is required");
        }

        switch (config.getMode()) {
            case RELATIVE:
                if (config.getLastValue() == null || config.getLastValue() <= 0 || config.getLastUnit() == null) {
                    throw new ReportServiceException(
                            ReportErrorCode.INVALID_TIME_RANGE,
                            "Relative time range requires lastValue > 0 and lastUnit");
                }
                break;
            case ABSOLUTE:
                if (config.getDefaultStartTs() == null || config.getDefaultEndTs() == null ||
                        config.getDefaultStartTs() >= config.getDefaultEndTs()) {
                    throw new ReportServiceException(
                            ReportErrorCode.INVALID_TIME_RANGE,
                            "Absolute time range requires valid defaultStartTs and defaultEndTs");
                }
                break;
            default:
                throw new ReportServiceException(
                        ReportErrorCode.INVALID_TIME_RANGE,
                        "Unsupported time range mode");
        }
    }
}
