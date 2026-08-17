/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0
 */

package org.thingsboard.server.service.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.report.ReportSelectableEntity;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.model.SecurityUser;

import org.springframework.security.access.AccessDeniedException;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.security.Authority;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@TbCoreComponent
@RequiredArgsConstructor
public class DefaultReportEntitySelectionService implements ReportEntitySelectionService {

    private final DeviceService deviceService;
    private final AssetService assetService;
    private final TimeseriesService timeseriesService;

    @Override
    public PageData<ReportSelectableEntity> findSelectableEntities(SecurityUser user,
            EntityType entityType,
            CustomerId customerId,
            String textSearch,
            int page,
            int pageSize) {
        TenantId tenantId = user.getTenantId();
        PageLink pageLink = new PageLink(pageSize, page, textSearch);

        if (entityType == EntityType.DEVICE) {
            return findDevices(user, tenantId, customerId, pageLink);
        }

        if (entityType == EntityType.ASSET) {
            return findAssets(user, tenantId, customerId, pageLink);
        }

        throw new IllegalArgumentException("Unsupported entity type for reports: " + entityType);
    }

    private PageData<ReportSelectableEntity> findDevices(SecurityUser user,
            TenantId tenantId,
            CustomerId requestedCustomerId,
            PageLink pageLink) {
        PageData<Device> devices;

        switch (user.getAuthority()) {
            case TENANT_ADMIN:
                if (requestedCustomerId != null) {
                    devices = deviceService.findDevicesByTenantIdAndCustomerId(tenantId, requestedCustomerId, pageLink);
                } else {
                    devices = deviceService.findDevicesByTenantId(tenantId, pageLink);
                }
                break;

            case CUSTOMER_USER:
                CustomerId currentCustomerId = user.getCustomerId();
                devices = deviceService.findDevicesByTenantIdAndCustomerId(tenantId, currentCustomerId, pageLink);
                break;

            default:
                throw new IllegalArgumentException("User authority is not allowed to select report devices.");
        }

        return devices.mapData(device -> new ReportSelectableEntity(
                device.getId(),
                device.getName(),
                device.getLabel(),
                device.getType(),
                device.getCustomerId()));
    }

    private PageData<ReportSelectableEntity> findAssets(SecurityUser user,
            TenantId tenantId,
            CustomerId requestedCustomerId,
            PageLink pageLink) {
        PageData<Asset> assets;

        switch (user.getAuthority()) {
            case TENANT_ADMIN:
                if (requestedCustomerId != null) {
                    assets = assetService.findAssetsByTenantIdAndCustomerId(tenantId, requestedCustomerId, pageLink);
                } else {
                    assets = assetService.findAssetsByTenantId(tenantId, pageLink);
                }
                break;

            case CUSTOMER_USER:
                CustomerId currentCustomerId = user.getCustomerId();
                assets = assetService.findAssetsByTenantIdAndCustomerId(tenantId, currentCustomerId, pageLink);
                break;

            default:
                throw new IllegalArgumentException("User authority is not allowed to select report assets.");
        }

        return assets.mapData(asset -> new ReportSelectableEntity(
                asset.getId(),
                asset.getName(),
                asset.getLabel(),
                asset.getType(),
                asset.getCustomerId()));
    }

    @Override
    public List<String> findSelectableEntityKeys(SecurityUser user, EntityId entityId) {
        if (entityId == null) {
            return Collections.emptyList();
        }
        validateEntityAccess(
                user,
                entityId);

        try {
            return timeseriesService.findAllLatest(user.getTenantId(), entityId).get().stream()
                    .map(tsKvEntry -> tsKvEntry.getKey())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load selectable telemetry keys for entity: " + entityId, e);
        }
    }

    private void validateEntityAccess(
            SecurityUser user,
            EntityId entityId) {

        if (user == null
                || entityId == null) {
            throw new AccessDeniedException(
                    "Report entity access denied.");
        }

        TenantId tenantId = user.getTenantId();

        switch (entityId.getEntityType()) {

            case DEVICE:
                Device device = deviceService.findDeviceById(
                        tenantId,
                        new DeviceId(
                                entityId.getId()));

                validateCustomerOwnership(
                        user,
                        device != null
                                ? device.getCustomerId()
                                : null,
                        device != null);

                return;

            case ASSET:
                Asset asset = assetService.findAssetById(
                        tenantId,
                        new AssetId(
                                entityId.getId()));

                validateCustomerOwnership(
                        user,
                        asset != null
                                ? asset.getCustomerId()
                                : null,
                        asset != null);

                return;

            default:
                throw new AccessDeniedException(
                        "Entity type is not allowed for reports.");
        }
    }

    private void validateCustomerOwnership(
            SecurityUser user,
            CustomerId entityCustomerId,
            boolean entityExists) {

        if (!entityExists) {
            throw new AccessDeniedException(
                    "Report entity access denied.");
        }

        if (Authority.TENANT_ADMIN.equals(
                user.getAuthority())) {
            return;
        }

        if (Authority.CUSTOMER_USER.equals(
                user.getAuthority())
                && user.getCustomerId() != null
                && user.getCustomerId().equals(
                        entityCustomerId)) {
            return;
        }

        throw new AccessDeniedException(
                "Report entity access denied.");
    }
}