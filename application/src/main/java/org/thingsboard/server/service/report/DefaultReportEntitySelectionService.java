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
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportSelectableEntity;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@TbCoreComponent
@RequiredArgsConstructor
public class DefaultReportEntitySelectionService
        implements ReportEntitySelectionService {

    private static final long TELEMETRY_TIMEOUT_SECONDS = 30;

    private final DeviceService deviceService;
    private final AssetService assetService;
    private final TimeseriesService timeseriesService;

    @Override
    public PageData<ReportSelectableEntity> findSelectableEntities(
            SecurityUser user,
            EntityType entityType,
            CustomerId requestedCustomerId,
            String textSearch,
            int page,
            int pageSize) {

        validateUser(user);
        validatePagination(page, pageSize);

        PageLink pageLink = new PageLink(
                pageSize,
                page,
                textSearch);

        if (EntityType.DEVICE.equals(entityType)) {
            return findDevices(
                    user,
                    requestedCustomerId,
                    pageLink);
        }

        if (EntityType.ASSET.equals(entityType)) {
            return findAssets(
                    user,
                    requestedCustomerId,
                    pageLink);
        }

        throw new ReportServiceException(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                "Only DEVICE and ASSET entities are supported for reports");
    }

    private PageData<ReportSelectableEntity> findDevices(
            SecurityUser user,
            CustomerId requestedCustomerId,
            PageLink pageLink) {

        TenantId tenantId = user.getTenantId();
        CustomerId effectiveCustomerId = resolveCustomerScope(
                user,
                requestedCustomerId);

        PageData<Device> devices;

        if (effectiveCustomerId == null) {
            devices = deviceService.findDevicesByTenantId(
                    tenantId,
                    pageLink);
        } else {
            devices = deviceService.findDevicesByTenantIdAndCustomerId(
                    tenantId,
                    effectiveCustomerId,
                    pageLink);
        }

        return devices.mapData(device -> new ReportSelectableEntity(
                device.getId(),
                device.getName(),
                device.getLabel(),
                device.getType(),
                device.getCustomerId()));
    }

    private PageData<ReportSelectableEntity> findAssets(
            SecurityUser user,
            CustomerId requestedCustomerId,
            PageLink pageLink) {

        TenantId tenantId = user.getTenantId();
        CustomerId effectiveCustomerId = resolveCustomerScope(
                user,
                requestedCustomerId);

        PageData<Asset> assets;

        if (effectiveCustomerId == null) {
            assets = assetService.findAssetsByTenantId(
                    tenantId,
                    pageLink);
        } else {
            assets = assetService.findAssetsByTenantIdAndCustomerId(
                    tenantId,
                    effectiveCustomerId,
                    pageLink);
        }

        return assets.mapData(asset -> new ReportSelectableEntity(
                asset.getId(),
                asset.getName(),
                asset.getLabel(),
                asset.getType(),
                asset.getCustomerId()));
    }

    private CustomerId resolveCustomerScope(
            SecurityUser user,
            CustomerId requestedCustomerId) {

        if (Authority.TENANT_ADMIN.equals(user.getAuthority())) {
            return requestedCustomerId;
        }

        if (Authority.CUSTOMER_USER.equals(user.getAuthority())) {
            CustomerId currentCustomerId = user.getCustomerId();

            if (requestedCustomerId != null
                    && !Objects.equals(
                            currentCustomerId,
                            requestedCustomerId)) {
                throw accessDenied();
            }

            return currentCustomerId;
        }

        throw accessDenied();
    }

    @Override
    public List<String> findSelectableEntityKeys(
            SecurityUser user,
            EntityId entityId) {

        validateUser(user);

        if (entityId == null
                || entityId.getId() == null
                || entityId.getEntityType() == null) {
            throw accessDenied();
        }

        validateEntityAccess(
                user,
                entityId);

        try {
            return timeseriesService
                    .findAllLatest(
                            user.getTenantId(),
                            entityId)
                    .get(
                            TELEMETRY_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS)
                    .stream()
                    .map(entry -> entry.getKey())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "Telemetry key lookup was interrupted",
                    e);

        } catch (ExecutionException | TimeoutException e) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "Failed to load selectable telemetry keys",
                    e);
        }
    }

    private void validateEntityAccess(
            SecurityUser user,
            EntityId entityId) {

        TenantId tenantId = user.getTenantId();

        if (EntityType.DEVICE.equals(entityId.getEntityType())) {
            Device device = deviceService.findDeviceById(
                    tenantId,
                    new DeviceId(entityId.getId()));

            validateCustomerOwnership(
                    user,
                    device != null ? device.getCustomerId() : null,
                    device != null);

            return;
        }

        if (EntityType.ASSET.equals(entityId.getEntityType())) {
            Asset asset = assetService.findAssetById(
                    tenantId,
                    new AssetId(entityId.getId()));

            validateCustomerOwnership(
                    user,
                    asset != null ? asset.getCustomerId() : null,
                    asset != null);

            return;
        }

        throw accessDenied();
    }

    private void validateCustomerOwnership(
            SecurityUser user,
            CustomerId entityCustomerId,
            boolean entityExists) {

        if (!entityExists) {
            throw accessDenied();
        }

        if (Authority.TENANT_ADMIN.equals(user.getAuthority())) {
            return;
        }

        if (Authority.CUSTOMER_USER.equals(user.getAuthority())
                && Objects.equals(
                        user.getCustomerId(),
                        entityCustomerId)) {
            return;
        }

        throw accessDenied();
    }

    private void validateUser(SecurityUser user) {
        if (user == null
                || user.getTenantId() == null
                || user.getAuthority() == null) {
            throw accessDenied();
        }

        if (Authority.TENANT_ADMIN.equals(user.getAuthority())) {
            return;
        }

        if (Authority.CUSTOMER_USER.equals(user.getAuthority())
                && user.getCustomerId() != null) {
            return;
        }

        throw accessDenied();
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 0 || pageSize <= 0) {
            throw new IllegalArgumentException(
                    "Page index must be non-negative and page size must be positive");
        }
    }

    private AccessDeniedException accessDenied() {
        return new AccessDeniedException(
                "Report entity access denied.");
    }

}
