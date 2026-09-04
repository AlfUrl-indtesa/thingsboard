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

import com.google.common.util.concurrent.Futures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportSelectableEntity;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultReportEntitySelectionServiceTest {

    @Mock
    private DeviceService deviceService;

    @Mock
    private AssetService assetService;

    @Mock
    private TimeseriesService timeseriesService;

    private DefaultReportEntitySelectionService service;

    @BeforeEach
    void setUp() {
        service = new DefaultReportEntitySelectionService(
                deviceService,
                assetService,
                timeseriesService);
    }

    @Test
    void tenantAdminListsAllTenantDevicesAndMapsResults() {
        TenantId tenantId = tenantId();
        CustomerId customerId = customerId();
        DeviceId deviceId = new DeviceId(UUID.randomUUID());

        Device device = new Device(deviceId);
        device.setTenantId(tenantId);
        device.setCustomerId(customerId);
        device.setName("Compressor room");
        device.setLabel("Main compressor");
        device.setType("Compressor");

        PageData<Device> devicePage = new PageData<>(
                List.of(device),
                1,
                1,
                false);

        when(deviceService.findDevicesByTenantId(
                eq(tenantId),
                any(PageLink.class)))
                .thenReturn(devicePage);

        PageData<ReportSelectableEntity> result =
                service.findSelectableEntities(
                        user(
                                Authority.TENANT_ADMIN,
                                tenantId,
                                null),
                        EntityType.DEVICE,
                        null,
                        "compressor",
                        0,
                        25);

        assertEquals(1, result.getData().size());

        ReportSelectableEntity selectable =
                result.getData().get(0);

        assertEquals(deviceId, selectable.getId());
        assertEquals("Compressor room", selectable.getName());
        assertEquals("Main compressor", selectable.getLabel());
        assertEquals("Compressor", selectable.getType());
        assertEquals(customerId, selectable.getCustomerId());

        verify(deviceService).findDevicesByTenantId(
                eq(tenantId),
                any(PageLink.class));

        verify(deviceService, never())
                .findDevicesByTenantIdAndCustomerId(
                        any(),
                        any(),
                        any());
    }

    @Test
    void tenantAdminCanLimitAssetsToCustomer() {
        TenantId tenantId = tenantId();
        CustomerId requestedCustomerId = customerId();

        when(assetService.findAssetsByTenantIdAndCustomerId(
                eq(tenantId),
                eq(requestedCustomerId),
                any(PageLink.class)))
                .thenReturn(PageData.emptyPageData());

        PageData<ReportSelectableEntity> result =
                service.findSelectableEntities(
                        user(
                                Authority.TENANT_ADMIN,
                                tenantId,
                                null),
                        EntityType.ASSET,
                        requestedCustomerId,
                        null,
                        0,
                        25);

        assertTrue(result.getData().isEmpty());

        verify(assetService)
                .findAssetsByTenantIdAndCustomerId(
                        eq(tenantId),
                        eq(requestedCustomerId),
                        any(PageLink.class));

        verify(assetService, never())
                .findAssetsByTenantId(
                        any(),
                        any());
    }

    @Test
    void customerUserIsRestrictedToOwnCustomer() {
        TenantId tenantId = tenantId();
        CustomerId ownCustomerId = customerId();

        when(deviceService.findDevicesByTenantIdAndCustomerId(
                eq(tenantId),
                eq(ownCustomerId),
                any(PageLink.class)))
                .thenReturn(PageData.emptyPageData());

        service.findSelectableEntities(
                user(
                        Authority.CUSTOMER_USER,
                        tenantId,
                        ownCustomerId),
                EntityType.DEVICE,
                null,
                null,
                0,
                25);

        verify(deviceService)
                .findDevicesByTenantIdAndCustomerId(
                        eq(tenantId),
                        eq(ownCustomerId),
                        any(PageLink.class));

        verify(deviceService, never())
                .findDevicesByTenantId(
                        any(),
                        any());
    }

    @Test
    void customerUserCannotRequestAnotherCustomer() {
        TenantId tenantId = tenantId();

        SecurityUser user = user(
                Authority.CUSTOMER_USER,
                tenantId,
                customerId());

        assertThrows(
                AccessDeniedException.class,
                () -> service.findSelectableEntities(
                        user,
                        EntityType.DEVICE,
                        customerId(),
                        null,
                        0,
                        25));

        verifyNoInteractions(
                deviceService,
                assetService,
                timeseriesService);
    }

    @Test
    void rejectsUnsupportedAuthorityAndEntityType() {
        TenantId tenantId = tenantId();

        assertThrows(
                AccessDeniedException.class,
                () -> service.findSelectableEntities(
                        user(
                                Authority.SYS_ADMIN,
                                tenantId,
                                null),
                        EntityType.DEVICE,
                        null,
                        null,
                        0,
                        25));

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.findSelectableEntities(
                        user(
                                Authority.TENANT_ADMIN,
                                tenantId,
                                null),
                        EntityType.TENANT,
                        null,
                        null,
                        0,
                        25));

        assertEquals(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                exception.getErrorCode());
    }

    @Test
    void rejectsInvalidPagination() {
        SecurityUser user = user(
                Authority.TENANT_ADMIN,
                tenantId(),
                null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.findSelectableEntities(
                        user,
                        EntityType.DEVICE,
                        null,
                        null,
                        -1,
                        25));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.findSelectableEntities(
                        user,
                        EntityType.DEVICE,
                        null,
                        null,
                        0,
                        0));
    }

    @Test
    void returnsSortedDistinctTelemetryKeysForOwnedDevice() {
        TenantId tenantId = tenantId();
        CustomerId customerId = customerId();
        DeviceId deviceId = new DeviceId(UUID.randomUUID());

        Device device = new Device(deviceId);
        device.setTenantId(tenantId);
        device.setCustomerId(customerId);

        when(deviceService.findDeviceById(
                tenantId,
                deviceId))
                .thenReturn(device);

        List<TsKvEntry> telemetry = List.of(
                new BasicTsKvEntry(
                        1L,
                        new StringDataEntry(
                                "pressure",
                                "100")),
                new BasicTsKvEntry(
                        2L,
                        new StringDataEntry(
                                "flow",
                                "250")),
                new BasicTsKvEntry(
                        3L,
                        new StringDataEntry(
                                "pressure",
                                "101")));

        when(timeseriesService.findAllLatest(
                tenantId,
                deviceId))
                .thenReturn(
                        Futures.immediateFuture(
                                telemetry));

        List<String> keys =
                service.findSelectableEntityKeys(
                        user(
                                Authority.CUSTOMER_USER,
                                tenantId,
                                customerId),
                        deviceId);

        assertEquals(
                List.of("flow", "pressure"),
                keys);
    }

    @Test
    void customerCannotReadKeysFromAnotherCustomer() {
        TenantId tenantId = tenantId();
        CustomerId ownCustomerId = customerId();
        DeviceId deviceId = new DeviceId(UUID.randomUUID());

        Device device = new Device(deviceId);
        device.setTenantId(tenantId);
        device.setCustomerId(customerId());

        when(deviceService.findDeviceById(
                tenantId,
                deviceId))
                .thenReturn(device);

        assertThrows(
                AccessDeniedException.class,
                () -> service.findSelectableEntityKeys(
                        user(
                                Authority.CUSTOMER_USER,
                                tenantId,
                                ownCustomerId),
                        deviceId));

        verifyNoInteractions(timeseriesService);
    }

    @Test
    void tenantAdminCannotReadKeysFromMissingEntity() {
        TenantId tenantId = tenantId();
        DeviceId deviceId = new DeviceId(UUID.randomUUID());

        when(deviceService.findDeviceById(
                tenantId,
                deviceId))
                .thenReturn(null);

        assertThrows(
                AccessDeniedException.class,
                () -> service.findSelectableEntityKeys(
                        user(
                                Authority.TENANT_ADMIN,
                                tenantId,
                                null),
                        deviceId));

        verifyNoInteractions(timeseriesService);
    }

    @Test
    void customerCanReadKeysForOwnedAsset() {
        TenantId tenantId = tenantId();
        CustomerId customerId = customerId();
        AssetId assetId = new AssetId(UUID.randomUUID());

        Asset asset = mock(Asset.class);

        when(asset.getCustomerId())
                .thenReturn(customerId);

        when(assetService.findAssetById(
                tenantId,
                assetId))
                .thenReturn(asset);

        when(timeseriesService.findAllLatest(
                tenantId,
                assetId))
                .thenReturn(
                        Futures.immediateFuture(
                                Collections.<TsKvEntry>emptyList()));

        List<String> keys =
                service.findSelectableEntityKeys(
                        user(
                                Authority.CUSTOMER_USER,
                                tenantId,
                                customerId),
                        assetId);

        assertTrue(keys.isEmpty());
    }

    @Test
    void wrapsTelemetryFailuresInTypedException() {
        TenantId tenantId = tenantId();
        DeviceId deviceId = new DeviceId(UUID.randomUUID());

        Device device = new Device(deviceId);
        device.setTenantId(tenantId);
        device.setCustomerId(customerId());

        when(deviceService.findDeviceById(
                tenantId,
                deviceId))
                .thenReturn(device);

        when(timeseriesService.findAllLatest(
                tenantId,
                deviceId))
                .thenReturn(
                        Futures.<List<TsKvEntry>>immediateFailedFuture(
                                new IllegalStateException(
                                        "simulated failure")));

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.findSelectableEntityKeys(
                        user(
                                Authority.TENANT_ADMIN,
                                tenantId,
                                null),
                        deviceId));

        assertEquals(
                ReportErrorCode.DATA_COLLECTION_FAILED,
                exception.getErrorCode());
    }

    private SecurityUser user(
            Authority authority,
            TenantId tenantId,
            CustomerId customerId) {

        SecurityUser user = mock(SecurityUser.class);

        when(user.getAuthority()).thenReturn(authority);
        when(user.getTenantId()).thenReturn(tenantId);
        if (customerId != null) {
            when(user.getCustomerId()).thenReturn(customerId);
        }

        return user;
    }

    private TenantId tenantId() {
        return TenantId.fromUUID(
                UUID.randomUUID());
    }

    private CustomerId customerId() {
        return new CustomerId(
                UUID.randomUUID());
    }

}
