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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportEntityFilter;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportScopeType;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportVariableConfig;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.List;
import java.util.Objects;

@Service
@TbCoreComponent
@RequiredArgsConstructor
public class ReportEntitySecurityService {

    private static final String GENERIC_DENIAL =
            "Report entity access denied.";

    private final DeviceService deviceService;
    private final AssetService assetService;
    private final ReportVariableConfigService
            reportVariableConfigService;
    private final ReportAccessService reportAccessService;

    /**
     * Validates every entity reference before a template is
     * persisted. A template may not reference entities from
     * another tenant or customer.
     */
    public void validateTemplateDefinition(
            TenantId tenantId,
            ReportTemplate template) {

        String violation = findTemplateViolation(
                tenantId,
                template,
                null);

        if (violation != null) {
            throw new AccessDeniedException(violation);
        }
    }

    /**
     * Validates the authenticated user and every runtime
     * entity reference before an execution is queued.
     */
    public void validateUserGenerationAccess(
            SecurityUser user,
            ReportTemplate template,
            GenerateReportRequest request) {

        reportAccessService.checkTemplateRead(
                user,
                template);

        String violation = findTemplateViolation(
                template.getTenantId(),
                template,
                request);

        if (violation != null) {
            throw new AccessDeniedException(violation);
        }
    }

    /**
     * Repeats validation inside the generation pipeline.
     *
     * This method intentionally does not depend on
     * SecurityUser, so recovered and asynchronous jobs are
     * protected by the persisted tenant and customer scope.
     */
    public void validateExecutionScope(
            ReportTemplate template,
            GenerateReportRequest request) {

        if (template == null
                || template.getTenantId() == null) {
            throw accessDenied(
                    "Report entity scope is invalid");
        }

        String violation = findTemplateViolation(
                template.getTenantId(),
                template,
                request);

        if (violation != null) {
            throw accessDenied(violation);
        }
    }

    private String findTemplateViolation(
            TenantId tenantId,
            ReportTemplate template,
            GenerateReportRequest request) {

        if (tenantId == null || template == null) {
            return GENERIC_DENIAL;
        }

        if (template.getTenantId() != null
                && !Objects.equals(
                        tenantId,
                        template.getTenantId())) {
            return GENERIC_DENIAL;
        }

        if (template.getScopeType() == null
                || template.getEntityFilter() == null) {
            return "Report entity scope is incomplete.";
        }

        ReportEntityFilter filter =
                template.getEntityFilter();

        if (filter.getScopeType() == null
                || filter.getScopeType()
                != template.getScopeType()) {
            return "Template and entity filter scopes do not match.";
        }

        if (filter.getEntityType() == null
                || filter.getEntityType().isBlank()
                || (!"DEVICE".equals(filter.getEntityType())
                && !"ASSET".equals(filter.getEntityType()))) {
            return "Only DEVICE and ASSET report entities are allowed.";
        }

        String scopeViolation =
                validateCustomerScope(template, filter);

        if (scopeViolation != null) {
            return scopeViolation;
        }

        CustomerId expectedCustomerId =
                resolveExpectedCustomerId(
                        template,
                        filter);

        String filterViolation = validateEntityIds(
                tenantId,
                expectedCustomerId,
                filter.getEntityType(),
                filter.getEntityIds());

        if (filterViolation != null) {
            return filterViolation;
        }

        if (request != null
                && request.getEntityIds() != null
                && !request.getEntityIds().isEmpty()) {

            if (template.getScopeType()
                    != ReportScopeType.FIXED_ENTITIES) {
                return "Runtime entity override is only allowed for fixed reports.";
            }

            String requestViolation =
                    validateEntityIds(
                            tenantId,
                            expectedCustomerId,
                            filter.getEntityType(),
                            request.getEntityIds());

            if (requestViolation != null) {
                return requestViolation;
            }
        }

        List<ReportVariableConfig> variables;

        try {
            variables = reportVariableConfigService
                    .extractVariables(
                            template.getSections());
        } catch (ReportServiceException e) {
            return "Report variable configuration is invalid.";
        }

        for (ReportVariableConfig variable : variables) {
            String variableViolation = validateEntity(
                    tenantId,
                    expectedCustomerId,
                    null,
                    variable.getEntityId());

            if (variableViolation != null) {
                return variableViolation;
            }
        }

        return null;
    }

    private String validateCustomerScope(
            ReportTemplate template,
            ReportEntityFilter filter) {

        CustomerId templateCustomerId =
                template.getCustomerId();

        CustomerId filterCustomerId =
                filter.getCustomerId();

        switch (template.getScopeType()) {
            case FIXED_ENTITIES:
                if (templateCustomerId != null
                        && filterCustomerId != null
                        && !Objects.equals(
                                templateCustomerId,
                                filterCustomerId)) {
                    return "Template and entity filter customer scopes do not match.";
                }
                return null;

            case TENANT_ENTITIES:
                if (templateCustomerId != null
                        || filterCustomerId != null) {
                    return "Tenant entity scope cannot be restricted to a customer.";
                }
                return null;

            case CUSTOMER_ENTITIES:
                if (filterCustomerId == null) {
                    return "Customer entity scope requires a customerId.";
                }

                if (templateCustomerId != null
                        && !Objects.equals(
                                templateCustomerId,
                                filterCustomerId)) {
                    return "Template and entity filter customer scopes do not match.";
                }
                return null;

            case CURRENT_CUSTOMER_ENTITIES:
                if (templateCustomerId == null) {
                    return "Current customer scope requires a template customerId.";
                }

                if (filterCustomerId != null
                        && !Objects.equals(
                                templateCustomerId,
                                filterCustomerId)) {
                    return "Template and entity filter customer scopes do not match.";
                }
                return null;

            default:
                return "Unsupported report scope type.";
        }
    }

    private CustomerId resolveExpectedCustomerId(
            ReportTemplate template,
            ReportEntityFilter filter) {

        switch (template.getScopeType()) {
            case TENANT_ENTITIES:
                return null;

            case CUSTOMER_ENTITIES:
                return filter.getCustomerId();

            case CURRENT_CUSTOMER_ENTITIES:
                return template.getCustomerId();

            case FIXED_ENTITIES:
                return template.getCustomerId() != null
                        ? template.getCustomerId()
                        : filter.getCustomerId();

            default:
                return null;
        }
    }

    private String validateEntityIds(
            TenantId tenantId,
            CustomerId expectedCustomerId,
            String expectedEntityType,
            List<EntityId> entityIds) {

        if (entityIds == null || entityIds.isEmpty()) {
            return null;
        }

        for (EntityId entityId : entityIds) {
            String violation = validateEntity(
                    tenantId,
                    expectedCustomerId,
                    expectedEntityType,
                    entityId);

            if (violation != null) {
                return violation;
            }
        }

        return null;
    }

    private String validateEntity(
            TenantId tenantId,
            CustomerId expectedCustomerId,
            String expectedEntityType,
            EntityId entityId) {

        if (entityId == null
                || entityId.getEntityType() == null
                || entityId.getId() == null) {
            return "Report contains an invalid entity reference.";
        }

        if (expectedEntityType != null
                && !expectedEntityType.equals(
                        entityId.getEntityType().name())) {
            return "Report entity type does not match the configured filter.";
        }

        switch (entityId.getEntityType()) {
            case DEVICE:
                return validateDevice(
                        tenantId,
                        expectedCustomerId,
                        entityId);

            case ASSET:
                return validateAsset(
                        tenantId,
                        expectedCustomerId,
                        entityId);

            default:
                return "Entity type is not allowed for report generation.";
        }
    }

    private String validateDevice(
            TenantId tenantId,
            CustomerId expectedCustomerId,
            EntityId entityId) {

        Device device = deviceService.findDeviceById(
                tenantId,
                new DeviceId(entityId.getId()));

        if (device == null) {
            return "One or more report entities are not available.";
        }

        if (expectedCustomerId != null
                && !Objects.equals(
                        expectedCustomerId,
                        device.getCustomerId())) {
            return "One or more report entities are outside the allowed customer scope.";
        }

        return null;
    }

    private String validateAsset(
            TenantId tenantId,
            CustomerId expectedCustomerId,
            EntityId entityId) {

        Asset asset = assetService.findAssetById(
                tenantId,
                new AssetId(entityId.getId()));

        if (asset == null) {
            return "One or more report entities are not available.";
        }

        if (expectedCustomerId != null
                && !Objects.equals(
                        expectedCustomerId,
                        asset.getCustomerId())) {
            return "One or more report entities are outside the allowed customer scope.";
        }

        return null;
    }

    private ReportServiceException accessDenied(
            String message) {

        return new ReportServiceException(
                ReportErrorCode.ACCESS_DENIED,
                message);
    }

}
