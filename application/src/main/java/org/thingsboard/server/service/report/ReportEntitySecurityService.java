/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportVariableConfig;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReportEntitySecurityService {

    private final DeviceService deviceService;
    private final AssetService assetService;
    private final ReportVariableConfigService reportVariableConfigService;

    /**
     * Used when a template is created or updated.
     *
     * A customer-scoped template cannot contain references to
     * entities belonging to another customer.
     */
    public void validateTemplateDefinition(
            TenantId tenantId,
            ReportTemplate template) {

        String violation = findTemplateViolation(
                tenantId,
                template,
                null);

        if (violation != null) {
            throw new AccessDeniedException(
                    violation);
        }
    }

    /**
     * Synchronous validation before a report execution is
     * inserted into the persistent queue.
     */
    public void validateUserGenerationAccess(
            SecurityUser user,
            ReportTemplate template,
            GenerateReportRequest request) {

        if (user == null
                || template == null
                || template.getTenantId() == null
                || !Objects.equals(
                        user.getTenantId(),
                        template.getTenantId())) {
            throw new AccessDeniedException(
                    "Report entity access denied.");
        }

        if (Authority.CUSTOMER_USER.equals(
                user.getAuthority())) {
            if (user.getCustomerId() == null
                    || template.getCustomerId() == null
                    || !Objects.equals(
                            user.getCustomerId(),
                            template.getCustomerId())) {
                throw new AccessDeniedException(
                        "Report entity access denied.");
            }

        } else if (!Authority.TENANT_ADMIN.equals(
                user.getAuthority())) {
            throw new AccessDeniedException(
                    "Report entity access denied.");
        }

        String violation = findTemplateViolation(
                template.getTenantId(),
                template,
                request);

        if (violation != null) {
            throw new AccessDeniedException(
                    violation);
        }
    }

    /**
     * Mandatory server-side validation inside the generation
     * pipeline.
     *
     * This is intentionally independent from SecurityUser so
     * recovered asynchronous jobs are protected too.
     */
    public void validateExecutionScope(
            ReportTemplate template,
            GenerateReportRequest request) {

        if (template == null
                || template.getTenantId() == null) {
            throw new ReportServiceException(
                    ReportErrorCode.ACCESS_DENIED,
                    "Report entity scope is invalid");
        }

        String violation = findTemplateViolation(
                template.getTenantId(),
                template,
                request);

        if (violation != null) {
            throw new ReportServiceException(
                    ReportErrorCode.ACCESS_DENIED,
                    violation);
        }
    }

    private String findTemplateViolation(
            TenantId tenantId,
            ReportTemplate template,
            GenerateReportRequest request) {

        if (tenantId == null
                || template == null) {
            return "Report entity scope is invalid.";
        }

        CustomerId expectedCustomerId = template.getCustomerId();

        ReportEntityFilter filter = template.getEntityFilter();

        /*
         * A customer-specific template may not declare a
         * different customer inside the filter.
         */
        if (expectedCustomerId != null
                && filter != null
                && filter.getCustomerId() != null
                && !Objects.equals(
                        expectedCustomerId,
                        filter.getCustomerId())) {
            return "Report template contains an invalid customer scope.";
        }

        /*
         * Validate the template's own fixed entities even if
         * the runtime request overrides them.
         */
        if (filter != null) {
            String violation = validateEntityIds(
                    tenantId,
                    expectedCustomerId,
                    filter.getEntityIds());

            if (violation != null) {
                return violation;
            }
        }

        /*
         * Validate entity IDs supplied at generation time.
         */
        if (request != null) {
            String violation = validateEntityIds(
                    tenantId,
                    expectedCustomerId,
                    request.getEntityIds());

            if (violation != null) {
                return violation;
            }
        }

        /*
         * ReportVariableConfig contains its own EntityId.
         * Validate every entity reference stored inside the
         * section JSON as well.
         */
        if (template.getSections() != null) {
            for (ReportSectionConfig section : template.getSections()) {

                if (section == null
                        || section.getConfig() == null
                        || section.getConfig().isNull()) {
                    continue;
                }

                List<ReportVariableConfig> variables = reportVariableConfigService
                        .extractVariables(
                                section.getConfig());

                if (variables == null
                        || variables.isEmpty()) {
                    continue;
                }

                for (ReportVariableConfig variable : variables) {

                    if (variable == null
                            || variable.getEntityId() == null) {
                        continue;
                    }

                    String violation = validateEntity(
                            tenantId,
                            expectedCustomerId,
                            variable.getEntityId());

                    if (violation != null) {
                        return violation;
                    }
                }
            }
        }

        return null;
    }

    private String validateEntityIds(
            TenantId tenantId,
            CustomerId expectedCustomerId,
            List<EntityId> entityIds) {

        if (entityIds == null
                || entityIds.isEmpty()) {
            return null;
        }

        for (EntityId entityId : entityIds) {
            String violation = validateEntity(
                    tenantId,
                    expectedCustomerId,
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
            EntityId entityId) {

        if (entityId == null
                || entityId.getId() == null
                || entityId.getEntityType() == null) {
            return "Report contains an invalid entity reference.";
        }

        switch (entityId.getEntityType()) {

            case DEVICE:
                Device device = deviceService.findDeviceById(
                        tenantId,
                        new DeviceId(
                                entityId.getId()));

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

            case ASSET:
                Asset asset = assetService.findAssetById(
                        tenantId,
                        new AssetId(
                                entityId.getId()));

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

            default:
                return "Entity type is not allowed for report generation.";
        }
    }
}