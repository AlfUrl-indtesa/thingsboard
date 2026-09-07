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
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportEntityFilter;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportScopeType;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.queue.util.TbCoreComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@TbCoreComponent
@RequiredArgsConstructor
public class DefaultReportEntityResolverService
        implements ReportEntityResolverService {

    static final int RESOLUTION_PAGE_SIZE = 500;
    static final int MAX_RESOLVED_ENTITIES = 10_000;
    static final int MAX_RESOLUTION_PAGES = 100;

    private final DeviceService deviceService;
    private final AssetService assetService;

    @Override
    public List<ReportTargetEntity> resolveEntities(
            ReportTemplate template,
            GenerateReportRequest request) {

        ReportEntityFilter filter =
                validateAndGetFilter(template);

        EntityType entityType =
                resolveEntityType(filter.getEntityType());

        ReportScopeType scopeType =
                template.getScopeType();

        switch (scopeType) {
            case FIXED_ENTITIES:
                return resolveFixedEntities(
                        template,
                        request,
                        filter,
                        entityType);

            case TENANT_ENTITIES:
                rejectRuntimeEntityOverride(request);

                return resolveAllEntities(
                        template.getTenantId(),
                        null,
                        entityType);

            case CUSTOMER_ENTITIES:
                rejectRuntimeEntityOverride(request);

                return resolveAllEntities(
                        template.getTenantId(),
                        filter.getCustomerId(),
                        entityType);

            case CURRENT_CUSTOMER_ENTITIES:
                rejectRuntimeEntityOverride(request);

                return resolveAllEntities(
                        template.getTenantId(),
                        template.getCustomerId(),
                        entityType);

            default:
                throw invalidScope(
                        "Unsupported report scope type");
        }
    }

    private ReportEntityFilter validateAndGetFilter(
            ReportTemplate template) {

        if (template == null
                || template.getTenantId() == null
                || template.getScopeType() == null
                || template.getEntityFilter() == null) {
            throw invalidScope(
                    "Report template entity scope is incomplete");
        }

        ReportEntityFilter filter =
                template.getEntityFilter();

        if (filter.getScopeType() == null
                || filter.getScopeType()
                != template.getScopeType()) {
            throw invalidScope(
                    "Template and entity filter scope types must match");
        }

        switch (template.getScopeType()) {
            case FIXED_ENTITIES:
                break;

            case TENANT_ENTITIES:
                if (template.getCustomerId() != null
                        || filter.getCustomerId() != null) {
                    throw invalidScope(
                            "Tenant entity scope cannot be restricted to a customer");
                }
                break;

            case CUSTOMER_ENTITIES:
                if (filter.getCustomerId() == null) {
                    throw invalidScope(
                            "Customer entity scope requires a customerId");
                }

                if (template.getCustomerId() != null
                        && !Objects.equals(
                                template.getCustomerId(),
                                filter.getCustomerId())) {
                    throw invalidScope(
                            "Template and entity filter customer scopes must match");
                }
                break;

            case CURRENT_CUSTOMER_ENTITIES:
                if (template.getCustomerId() == null) {
                    throw invalidScope(
                            "Current customer scope requires a template customerId");
                }

                if (filter.getCustomerId() != null
                        && !Objects.equals(
                                template.getCustomerId(),
                                filter.getCustomerId())) {
                    throw invalidScope(
                            "Template and entity filter customer scopes must match");
                }
                break;

            default:
                throw invalidScope(
                        "Unsupported report scope type");
        }

        return filter;
    }

    private EntityType resolveEntityType(String value) {
        if (value == null || value.isBlank()) {
            throw invalidScope(
                    "Report entity type is required");
        }

        EntityType entityType;

        try {
            entityType = EntityType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_ENTITY_SCOPE,
                    "Invalid report entity type",
                    e);
        }

        if (entityType != EntityType.DEVICE
                && entityType != EntityType.ASSET) {
            throw invalidScope(
                    "Only DEVICE and ASSET entities are supported");
        }

        return entityType;
    }

    private List<ReportTargetEntity> resolveFixedEntities(
            ReportTemplate template,
            GenerateReportRequest request,
            ReportEntityFilter filter,
            EntityType expectedEntityType) {

        List<EntityId> entityIds =
                request != null
                        && request.getEntityIds() != null
                        && !request.getEntityIds().isEmpty()
                        ? request.getEntityIds()
                        : filter.getEntityIds();

        if (entityIds == null || entityIds.isEmpty()) {
            throw invalidScope(
                    "Fixed entity scope requires at least one entity");
        }

        Map<String, ReportTargetEntity> uniqueTargets =
                new LinkedHashMap<>();

        for (EntityId entityId : entityIds) {
            validateFixedEntityId(
                    entityId,
                    expectedEntityType);

            String key = entityId.getEntityType().name()
                    + ":"
                    + entityId.getId();

            if (uniqueTargets.containsKey(key)) {
                continue;
            }

            ensureCapacity(uniqueTargets.size());

            ReportTargetEntity target = resolveTargetEntity(
                    template.getTenantId(),
                    template.getCustomerId(),
                    entityId);

            uniqueTargets.put(key, target);
        }

        return requireEntities(
                new ArrayList<>(uniqueTargets.values()));
    }

    private void validateFixedEntityId(
            EntityId entityId,
            EntityType expectedEntityType) {

        if (entityId == null
                || entityId.getId() == null
                || entityId.getEntityType() == null) {
            throw invalidScope(
                    "Invalid report entity reference");
        }

        if (entityId.getEntityType() != expectedEntityType) {
            throw invalidScope(
                    "Every selected entity must match the report entity type");
        }
    }

    private List<ReportTargetEntity> resolveAllEntities(
            TenantId tenantId,
            CustomerId customerId,
            EntityType entityType) {

        List<ReportTargetEntity> targets =
                new ArrayList<>();

        PageLink pageLink =
                new PageLink(RESOLUTION_PAGE_SIZE);

        int pageCount = 0;
        boolean hasNext;

        do {
            pageCount++;

            if (pageCount > MAX_RESOLUTION_PAGES) {
                throw limitExceeded();
            }

            if (entityType == EntityType.DEVICE) {
                PageData<Device> page =
                        customerId == null
                                ? deviceService
                                        .findDevicesByTenantId(
                                                tenantId,
                                                pageLink)
                                : deviceService
                                        .findDevicesByTenantIdAndCustomerId(
                                                tenantId,
                                                customerId,
                                                pageLink);

                if (page == null) {
                    throw invalidScope(
                            "Device resolution returned no page");
                }

                if (page.getData() != null) {
                    for (Device device : page.getData()) {
                        ensureCapacity(targets.size());
                        validateResolvedCustomer(
                                customerId,
                                device.getCustomerId());

                        targets.add(
                                targetFromDevice(device));
                    }
                }

                hasNext = page.hasNext();

            } else {
                PageData<Asset> page =
                        customerId == null
                                ? assetService
                                        .findAssetsByTenantId(
                                                tenantId,
                                                pageLink)
                                : assetService
                                        .findAssetsByTenantIdAndCustomerId(
                                                tenantId,
                                                customerId,
                                                pageLink);

                if (page == null) {
                    throw invalidScope(
                            "Asset resolution returned no page");
                }

                if (page.getData() != null) {
                    for (Asset asset : page.getData()) {
                        ensureCapacity(targets.size());
                        validateResolvedCustomer(
                                customerId,
                                asset.getCustomerId());

                        targets.add(
                                targetFromAsset(asset));
                    }
                }

                hasNext = page.hasNext();
            }

            if (hasNext
                    && targets.size()
                    >= MAX_RESOLVED_ENTITIES) {
                throw limitExceeded();
            }

            if (hasNext) {
                pageLink = pageLink.nextPageLink();
            }

        } while (hasNext);

        targets.sort(
                Comparator
                        .comparing(
                                (ReportTargetEntity target) ->
                                        normalizedName(
                                                target.getName()))
                        .thenComparing(
                                target ->
                                        target.getEntityId().toString()));

        return requireEntities(targets);
    }

    private ReportTargetEntity resolveTargetEntity(
            TenantId tenantId,
            CustomerId expectedCustomerId,
            EntityId entityId) {

        if (entityId.getEntityType() == EntityType.DEVICE) {
            Device device = deviceService.findDeviceById(
                    tenantId,
                    new DeviceId(entityId.getId()));

            if (device == null) {
                throw invalidScope(
                        "Report device was not found");
            }

            validateResolvedCustomer(
                    expectedCustomerId,
                    device.getCustomerId());

            return targetFromDevice(device);
        }

        if (entityId.getEntityType() == EntityType.ASSET) {
            Asset asset = assetService.findAssetById(
                    tenantId,
                    new AssetId(entityId.getId()));

            if (asset == null) {
                throw invalidScope(
                        "Report asset was not found");
            }

            validateResolvedCustomer(
                    expectedCustomerId,
                    asset.getCustomerId());

            return targetFromAsset(asset);
        }

        throw invalidScope(
                "Unsupported report entity type");
    }

    private ReportTargetEntity targetFromDevice(Device device) {
        if (device == null || device.getId() == null) {
            throw invalidScope(
                    "Resolved report device is invalid");
        }

        return createTarget(
                device.getId(),
                device.getName(),
                device.getLabel());
    }

    private ReportTargetEntity targetFromAsset(Asset asset) {
        if (asset == null || asset.getId() == null) {
            throw invalidScope(
                    "Resolved report asset is invalid");
        }

        return createTarget(
                asset.getId(),
                asset.getName(),
                asset.getLabel());
    }

    private ReportTargetEntity createTarget(
            EntityId entityId,
            String name,
            String label) {

        ReportTargetEntity target =
                new ReportTargetEntity();

        target.setEntityId(entityId.getId());
        target.setEntityType(
                entityId.getEntityType().name());

        target.setName(
                notBlank(name)
                        ? name
                        : entityId.getId().toString());

        target.setLabel(
                notBlank(label)
                        ? label
                        : target.getEntityType());

        return target;
    }

    private void validateResolvedCustomer(
            CustomerId expectedCustomerId,
            CustomerId actualCustomerId) {

        if (expectedCustomerId != null
                && !Objects.equals(
                        expectedCustomerId,
                        actualCustomerId)) {
            throw new ReportServiceException(
                    ReportErrorCode.ACCESS_DENIED,
                    "Resolved entity is outside the allowed customer scope");
        }
    }

    private void rejectRuntimeEntityOverride(
            GenerateReportRequest request) {

        if (request != null
                && request.getEntityIds() != null
                && !request.getEntityIds().isEmpty()) {
            throw invalidScope(
                    "Runtime entity overrides are only allowed for fixed entity scopes");
        }
    }

    private List<ReportTargetEntity> requireEntities(
            List<ReportTargetEntity> targets) {

        if (targets == null || targets.isEmpty()) {
            throw invalidScope(
                    "No entities were resolved for the report");
        }

        return targets;
    }

    private void ensureCapacity(int currentSize) {
        if (currentSize >= MAX_RESOLVED_ENTITIES) {
            throw limitExceeded();
        }
    }

    private ReportServiceException limitExceeded() {
        return invalidScope(
                "Report entity limit exceeded: "
                        + MAX_RESOLVED_ENTITIES);
    }

    private ReportServiceException invalidScope(
            String message) {

        return new ReportServiceException(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                message);
    }

    private String normalizedName(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

}
