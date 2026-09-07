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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultReportEntityResolverServiceTest {

    @Mock
    private DeviceService deviceService;

    @Mock
    private AssetService assetService;

    private DefaultReportEntityResolverService service;

    @BeforeEach
    void setUp() {
        service = new DefaultReportEntityResolverService(
                deviceService,
                assetService);
    }

    @Test
    void fixedEntitiesUseFilterAndRemoveDuplicates() {
        TenantId tenantId = tenantId();
        DeviceId firstId = deviceId();
        DeviceId secondId = deviceId();

        ReportTemplate template = template(
                tenantId,
                ReportScopeType.FIXED_ENTITIES,
                "DEVICE");

        template.getEntityFilter().setEntityIds(
                List.of(firstId, firstId, secondId));

        Device first = device(
                tenantId,
                firstId,
                null,
                "First",
                "");

        Device second = device(
                tenantId,
                secondId,
                null,
                null,
                "Second label");

        when(deviceService.findDeviceById(
                tenantId,
                firstId))
                .thenReturn(first);

        when(deviceService.findDeviceById(
                tenantId,
                secondId))
                .thenReturn(second);

        List<ReportTargetEntity> result =
                service.resolveEntities(
                        template,
                        new GenerateReportRequest());

        assertEquals(2, result.size());
        assertEquals(firstId.getId(), result.get(0).getEntityId());
        assertEquals("DEVICE", result.get(0).getLabel());
        assertEquals(secondId.getId().toString(), result.get(1).getName());

        verify(deviceService, times(1))
                .findDeviceById(tenantId, firstId);

        verify(deviceService, times(1))
                .findDeviceById(tenantId, secondId);
    }

    @Test
    void requestOverridesFixedTemplateEntities() {
        TenantId tenantId = tenantId();
        DeviceId templateDeviceId = deviceId();
        DeviceId requestedDeviceId = deviceId();

        ReportTemplate template = template(
                tenantId,
                ReportScopeType.FIXED_ENTITIES,
                "DEVICE");

        template.getEntityFilter().setEntityIds(
                List.of(templateDeviceId));

        GenerateReportRequest request =
                new GenerateReportRequest();

        request.setEntityIds(
                List.of(requestedDeviceId));

        when(deviceService.findDeviceById(
                tenantId,
                requestedDeviceId))
                .thenReturn(
                        device(
                                tenantId,
                                requestedDeviceId,
                                null,
                                "Requested",
                                "Requested"));

        List<ReportTargetEntity> result =
                service.resolveEntities(template, request);

        assertEquals(1, result.size());
        assertEquals(
                requestedDeviceId.getId(),
                result.getFirst().getEntityId());

        verify(deviceService, never())
                .findDeviceById(
                        tenantId,
                        templateDeviceId);
    }

    @Test
    void rejectsFixedEntityTypeMismatch() {
        TenantId tenantId = tenantId();

        ReportTemplate template = template(
                tenantId,
                ReportScopeType.FIXED_ENTITIES,
                "DEVICE");

        template.getEntityFilter().setEntityIds(
                List.of(new AssetId(UUID.randomUUID())));

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.resolveEntities(
                        template,
                        new GenerateReportRequest()));

        assertEquals(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                exception.getErrorCode());

        verifyNoInteractions(
                deviceService,
                assetService);
    }

    @Test
    void rejectsFixedEntityOutsideTemplateCustomer() {
        TenantId tenantId = tenantId();
        CustomerId allowedCustomer = customerId();
        DeviceId deviceId = deviceId();

        ReportTemplate template = template(
                tenantId,
                ReportScopeType.FIXED_ENTITIES,
                "DEVICE");

        template.setCustomerId(allowedCustomer);
        template.getEntityFilter().setEntityIds(
                List.of(deviceId));

        when(deviceService.findDeviceById(
                tenantId,
                deviceId))
                .thenReturn(
                        device(
                                tenantId,
                                deviceId,
                                customerId(),
                                "Outside",
                                "Outside"));

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.resolveEntities(
                        template,
                        new GenerateReportRequest()));

        assertEquals(
                ReportErrorCode.ACCESS_DENIED,
                exception.getErrorCode());
    }

    @Test
    void tenantDevicesResolveAcrossPagesAndSortByName() {
        TenantId tenantId = tenantId();

        Device zulu = device(
                tenantId,
                deviceId(),
                null,
                "Zulu",
                "Zulu");

        Device alpha = device(
                tenantId,
                deviceId(),
                null,
                "Alpha",
                "Alpha");

        PageData<Device> firstPage = new PageData<>(
                List.of(zulu),
                2,
                2,
                true);

        PageData<Device> secondPage = new PageData<>(
                List.of(alpha),
                2,
                2,
                false);

        when(deviceService.findDevicesByTenantId(
                eq(tenantId),
                any(PageLink.class)))
                .thenReturn(firstPage, secondPage);

        ReportTemplate template = template(
                tenantId,
                ReportScopeType.TENANT_ENTITIES,
                "DEVICE");

        List<ReportTargetEntity> result =
                service.resolveEntities(
                        template,
                        new GenerateReportRequest());

        assertEquals(2, result.size());
        assertEquals("Alpha", result.get(0).getName());
        assertEquals("Zulu", result.get(1).getName());

        verify(deviceService, times(2))
                .findDevicesByTenantId(
                        eq(tenantId),
                        any(PageLink.class));
    }

    @Test
    void tenantAssetsAreResolved() {
        TenantId tenantId = tenantId();
        AssetId assetId = new AssetId(UUID.randomUUID());

        Asset asset = mock(Asset.class);

        when(asset.getId()).thenReturn(assetId);
        when(asset.getName()).thenReturn("Receiver tank");
        when(asset.getLabel()).thenReturn("Tank");

        when(assetService.findAssetsByTenantId(
                eq(tenantId),
                any(PageLink.class)))
                .thenReturn(
                        new PageData<>(
                                List.of(asset),
                                1,
                                1,
                                false));

        ReportTemplate template = template(
                tenantId,
                ReportScopeType.TENANT_ENTITIES,
                "ASSET");

        List<ReportTargetEntity> result =
                service.resolveEntities(
                        template,
                        new GenerateReportRequest());

        assertEquals(1, result.size());
        assertEquals("ASSET", result.getFirst().getEntityType());
        assertEquals(assetId.getId(), result.getFirst().getEntityId());
    }

    @Test
    void customerScopeUsesFilterCustomer() {
        TenantId tenantId = tenantId();
        CustomerId customerId = customerId();

        ReportTemplate template = template(
                tenantId,
                ReportScopeType.CUSTOMER_ENTITIES,
                "DEVICE");

        template.getEntityFilter().setCustomerId(customerId);

        Device device = device(
                tenantId,
                deviceId(),
                customerId,
                "Customer device",
                "Customer device");

        when(deviceService.findDevicesByTenantIdAndCustomerId(
                eq(tenantId),
                eq(customerId),
                any(PageLink.class)))
                .thenReturn(
                        new PageData<>(
                                List.of(device),
                                1,
                                1,
                                false));

        List<ReportTargetEntity> result =
                service.resolveEntities(
                        template,
                        new GenerateReportRequest());

        assertEquals(1, result.size());

        verify(deviceService)
                .findDevicesByTenantIdAndCustomerId(
                        eq(tenantId),
                        eq(customerId),
                        any(PageLink.class));
    }

    @Test
    void currentCustomerScopeUsesTemplateCustomer() {
        TenantId tenantId = tenantId();
        CustomerId customerId = customerId();

        ReportTemplate template = template(
                tenantId,
                ReportScopeType.CURRENT_CUSTOMER_ENTITIES,
                "DEVICE");

        template.setCustomerId(customerId);

        Device device = device(
                tenantId,
                deviceId(),
                customerId,
                "Current customer device",
                "Current customer device");

        when(deviceService.findDevicesByTenantIdAndCustomerId(
                eq(tenantId),
                eq(customerId),
                any(PageLink.class)))
                .thenReturn(
                        new PageData<>(
                                List.of(device),
                                1,
                                1,
                                false));

        List<ReportTargetEntity> result =
                service.resolveEntities(
                        template,
                        new GenerateReportRequest());

        assertEquals(1, result.size());

        verify(deviceService)
                .findDevicesByTenantIdAndCustomerId(
                        eq(tenantId),
                        eq(customerId),
                        any(PageLink.class));
    }

    @Test
    void dynamicScopesRejectRuntimeEntityOverride() {
        TenantId tenantId = tenantId();

        ReportTemplate template = template(
                tenantId,
                ReportScopeType.TENANT_ENTITIES,
                "DEVICE");

        GenerateReportRequest request =
                new GenerateReportRequest();

        request.setEntityIds(
                List.of(deviceId()));

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.resolveEntities(
                        template,
                        request));

        assertEquals(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                exception.getErrorCode());

        verifyNoInteractions(
                deviceService,
                assetService);
    }

    @Test
    void customerScopeRejectsUnexpectedDaoEntity() {
        TenantId tenantId = tenantId();
        CustomerId expectedCustomer = customerId();

        ReportTemplate template = template(
                tenantId,
                ReportScopeType.CUSTOMER_ENTITIES,
                "DEVICE");

        template.getEntityFilter().setCustomerId(
                expectedCustomer);

        Device outsideDevice = device(
                tenantId,
                deviceId(),
                customerId(),
                "Outside customer",
                "Outside customer");

        when(deviceService.findDevicesByTenantIdAndCustomerId(
                eq(tenantId),
                eq(expectedCustomer),
                any(PageLink.class)))
                .thenReturn(
                        new PageData<>(
                                List.of(outsideDevice),
                                1,
                                1,
                                false));

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.resolveEntities(
                        template,
                        new GenerateReportRequest()));

        assertEquals(
                ReportErrorCode.ACCESS_DENIED,
                exception.getErrorCode());
    }

    @Test
    void rejectsEmptyResolution() {
        TenantId tenantId = tenantId();

        ReportTemplate template = template(
                tenantId,
                ReportScopeType.TENANT_ENTITIES,
                "DEVICE");

        when(deviceService.findDevicesByTenantId(
                eq(tenantId),
                any(PageLink.class)))
                .thenReturn(PageData.emptyPageData());

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.resolveEntities(
                        template,
                        new GenerateReportRequest()));

        assertEquals(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                exception.getErrorCode());
    }

    @Test
    void enforcesMaximumEntityLimit() {
        TenantId tenantId = tenantId();

        ReportTemplate template = template(
                tenantId,
                ReportScopeType.TENANT_ENTITIES,
                "DEVICE");

        Device repeatedDevice = device(
                tenantId,
                deviceId(),
                null,
                "Repeated",
                "Repeated");

        List<Device> tooManyEntities = Collections.nCopies(
                DefaultReportEntityResolverService
                        .MAX_RESOLVED_ENTITIES + 1,
                repeatedDevice);

        when(deviceService.findDevicesByTenantId(
                eq(tenantId),
                any(PageLink.class)))
                .thenReturn(
                        new PageData<>(
                                tooManyEntities,
                                1,
                                tooManyEntities.size(),
                                false));

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.resolveEntities(
                        template,
                        new GenerateReportRequest()));

        assertEquals(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                exception.getErrorCode());
    }

    @Test
    void rejectsUnsupportedEntityType() {
        TenantId tenantId = tenantId();

        ReportTemplate template = template(
                tenantId,
                ReportScopeType.TENANT_ENTITIES,
                "TENANT");

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.resolveEntities(
                        template,
                        new GenerateReportRequest()));

        assertEquals(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                exception.getErrorCode());

        verifyNoInteractions(
                deviceService,
                assetService);
    }

    private ReportTemplate template(
            TenantId tenantId,
            ReportScopeType scopeType,
            String entityType) {

        ReportEntityFilter filter =
                new ReportEntityFilter();

        filter.setScopeType(scopeType);
        filter.setEntityType(entityType);

        ReportTemplate template =
                new ReportTemplate();

        template.setTenantId(tenantId);
        template.setScopeType(scopeType);
        template.setEntityFilter(filter);

        return template;
    }

    private Device device(
            TenantId tenantId,
            DeviceId deviceId,
            CustomerId customerId,
            String name,
            String label) {

        Device device = new Device(deviceId);
        device.setTenantId(tenantId);
        device.setCustomerId(customerId);
        device.setName(name);
        device.setLabel(label);
        device.setType("default");

        return device;
    }

    private TenantId tenantId() {
        return TenantId.fromUUID(
                UUID.randomUUID());
    }

    private DeviceId deviceId() {
        return new DeviceId(
                UUID.randomUUID());
    }

    private CustomerId customerId() {
        return new CustomerId(
                UUID.randomUUID());
    }

}
