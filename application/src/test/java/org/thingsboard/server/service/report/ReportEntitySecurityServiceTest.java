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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
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
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportEntitySecurityServiceTest {

    private final TenantId tenantId =
            TenantId.fromUUID(UUID.randomUUID());

    @Mock
    private DeviceService deviceService;

    @Mock
    private AssetService assetService;

    @Mock
    private ReportVariableConfigService
            reportVariableConfigService;

    @Mock
    private ReportAccessService reportAccessService;

    @InjectMocks
    private ReportEntitySecurityService service;

    @Test
    void acceptsDeviceInsideFixedCustomerScope() {
        CustomerId customerId =
                customerId();

        UUID deviceUuid = UUID.randomUUID();

        ReportTemplate template = template(
                ReportScopeType.FIXED_ENTITIES,
                customerId,
                null,
                "DEVICE",
                List.of(new DeviceId(deviceUuid)));

        allowDevice(deviceUuid, customerId);
        stubNoVariables(template);

        assertDoesNotThrow(() ->
                service.validateTemplateDefinition(
                        tenantId,
                        template));

        verify(deviceService).findDeviceById(
                tenantId,
                new DeviceId(deviceUuid));
    }

    @Test
    void acceptsAssetInsideFixedCustomerScope() {
        CustomerId customerId =
                customerId();

        UUID assetUuid = UUID.randomUUID();

        ReportTemplate template = template(
                ReportScopeType.FIXED_ENTITIES,
                customerId,
                null,
                "ASSET",
                List.of(new AssetId(assetUuid)));

        allowAsset(assetUuid, customerId);
        stubNoVariables(template);

        assertDoesNotThrow(() ->
                service.validateTemplateDefinition(
                        tenantId,
                        template));

        verify(assetService).findAssetById(
                tenantId,
                new AssetId(assetUuid));
    }

    @Test
    void rejectsTemplateFromAnotherTenant() {
        ReportTemplate template = template(
                ReportScopeType.FIXED_ENTITIES,
                null,
                null,
                "DEVICE",
                List.of());

        template.setTenantId(
                TenantId.fromUUID(UUID.randomUUID()));

        assertThrows(
                AccessDeniedException.class,
                () -> service.validateTemplateDefinition(
                        tenantId,
                        template));

        verifyNoInteractions(
                deviceService,
                assetService,
                reportVariableConfigService);
    }

    @Test
    void rejectsMismatchedTemplateAndFilterScopes() {
        ReportTemplate template = template(
                ReportScopeType.FIXED_ENTITIES,
                null,
                null,
                "DEVICE",
                List.of());

        template.getEntityFilter().setScopeType(
                ReportScopeType.TENANT_ENTITIES);

        assertThrows(
                AccessDeniedException.class,
                () -> service.validateTemplateDefinition(
                        tenantId,
                        template));
    }

    @Test
    void rejectsMismatchedCustomerScopes() {
        ReportTemplate template = template(
                ReportScopeType.CUSTOMER_ENTITIES,
                customerId(),
                customerId(),
                "DEVICE",
                List.of());

        assertThrows(
                AccessDeniedException.class,
                () -> service.validateTemplateDefinition(
                        tenantId,
                        template));

        verifyNoInteractions(
                deviceService,
                assetService,
                reportVariableConfigService);
    }

    @Test
    void customerScopeUsesFilterCustomerWhenTemplateCustomerIsNull() {
        CustomerId filterCustomerId =
                customerId();

        CustomerId otherCustomerId =
                customerId();

        UUID deviceUuid = UUID.randomUUID();

        ReportTemplate template = template(
                ReportScopeType.CUSTOMER_ENTITIES,
                null,
                filterCustomerId,
                "DEVICE",
                List.of());

        ReportVariableConfig variable =
                variable(new DeviceId(deviceUuid));

        when(reportVariableConfigService.extractVariables(
                template.getSections()))
                .thenReturn(List.of(variable));

        allowDevice(deviceUuid, otherCustomerId);

        assertThrows(
                AccessDeniedException.class,
                () -> service.validateTemplateDefinition(
                        tenantId,
                        template));
    }

    @Test
    void tenantScopeAcceptsEntityFromAnyCustomerInTenant() {
        UUID deviceUuid = UUID.randomUUID();

        ReportTemplate template = template(
                ReportScopeType.TENANT_ENTITIES,
                null,
                null,
                "DEVICE",
                List.of());

        ReportVariableConfig variable =
                variable(new DeviceId(deviceUuid));

        when(reportVariableConfigService.extractVariables(
                template.getSections()))
                .thenReturn(List.of(variable));

        Device device = mock(Device.class);

        when(deviceService.findDeviceById(
                tenantId,
                new DeviceId(deviceUuid)))
                .thenReturn(device);

        assertDoesNotThrow(() ->
                service.validateExecutionScope(
                        template,
                        null));
    }

    @Test
    void currentCustomerScopeUsesPersistedTemplateCustomer() {
        CustomerId templateCustomerId =
                customerId();

        CustomerId otherCustomerId =
                customerId();

        UUID deviceUuid = UUID.randomUUID();

        ReportTemplate template = template(
                ReportScopeType.CURRENT_CUSTOMER_ENTITIES,
                templateCustomerId,
                null,
                "DEVICE",
                List.of());

        ReportVariableConfig variable =
                variable(new DeviceId(deviceUuid));

        when(reportVariableConfigService.extractVariables(
                template.getSections()))
                .thenReturn(List.of(variable));

        allowDevice(deviceUuid, otherCustomerId);

        assertExecutionDenied(() ->
                service.validateExecutionScope(
                        template,
                        null));
    }

    @Test
    void rejectsFixedEntityWithWrongType() {
        ReportTemplate template = template(
                ReportScopeType.FIXED_ENTITIES,
                null,
                null,
                "DEVICE",
                List.of(new AssetId(UUID.randomUUID())));

        assertThrows(
                AccessDeniedException.class,
                () -> service.validateTemplateDefinition(
                        tenantId,
                        template));

        verifyNoInteractions(
                deviceService,
                assetService,
                reportVariableConfigService);
    }

    @Test
    void rejectsEntityThatDoesNotExist() {
        UUID deviceUuid = UUID.randomUUID();

        ReportTemplate template = template(
                ReportScopeType.FIXED_ENTITIES,
                null,
                null,
                "DEVICE",
                List.of(new DeviceId(deviceUuid)));

        assertThrows(
                AccessDeniedException.class,
                () -> service.validateTemplateDefinition(
                        tenantId,
                        template));

        verify(deviceService).findDeviceById(
                tenantId,
                new DeviceId(deviceUuid));

        verifyNoInteractions(
                assetService,
                reportVariableConfigService);
    }

    @Test
    void acceptsRuntimeOverrideForFixedScope() {
        CustomerId customerId =
                customerId();

        UUID deviceUuid = UUID.randomUUID();

        ReportTemplate template = template(
                ReportScopeType.FIXED_ENTITIES,
                customerId,
                null,
                "DEVICE",
                List.of());

        GenerateReportRequest request =
                new GenerateReportRequest();

        request.setEntityIds(
                List.of(new DeviceId(deviceUuid)));

        allowDevice(deviceUuid, customerId);
        stubNoVariables(template);

        assertDoesNotThrow(() ->
                service.validateExecutionScope(
                        template,
                        request));
    }

    @Test
    void rejectsRuntimeOverrideForDynamicScope() {
        ReportTemplate template = template(
                ReportScopeType.TENANT_ENTITIES,
                null,
                null,
                "DEVICE",
                List.of());

        GenerateReportRequest request =
                new GenerateReportRequest();

        request.setEntityIds(
                List.of(
                        new DeviceId(UUID.randomUUID())));

        assertExecutionDenied(() ->
                service.validateExecutionScope(
                        template,
                        request));

        verifyNoInteractions(
                deviceService,
                assetService,
                reportVariableConfigService);
    }

    @Test
    void rejectsMalformedVariableConfigurationFailClosed() {
        ReportTemplate template = template(
                ReportScopeType.FIXED_ENTITIES,
                null,
                null,
                "DEVICE",
                List.of());

        when(reportVariableConfigService.extractVariables(
                template.getSections()))
                .thenThrow(new ReportServiceException(
                        ReportErrorCode.INVALID_ENTITY_SCOPE,
                        "invalid variables"));

        assertThrows(
                AccessDeniedException.class,
                () -> service.validateTemplateDefinition(
                        tenantId,
                        template));
    }

    @Test
    void authenticatedValidationDelegatesToAccessService() {
        CustomerId customerId =
                customerId();

        UUID deviceUuid = UUID.randomUUID();

        ReportTemplate template = template(
                ReportScopeType.FIXED_ENTITIES,
                customerId,
                null,
                "DEVICE",
                List.of(new DeviceId(deviceUuid)));

        SecurityUser user =
                mock(SecurityUser.class);

        allowDevice(deviceUuid, customerId);
        stubNoVariables(template);

        assertDoesNotThrow(() ->
                service.validateUserGenerationAccess(
                        user,
                        template,
                        null));

        verify(reportAccessService)
                .checkTemplateRead(user, template);
    }

    @Test
    void authenticatedValidationPropagatesAccessDenial() {
        ReportTemplate template = template(
                ReportScopeType.FIXED_ENTITIES,
                null,
                null,
                "DEVICE",
                List.of());

        SecurityUser user =
                mock(SecurityUser.class);

        doThrow(new AccessDeniedException("denied"))
                .when(reportAccessService)
                .checkTemplateRead(user, template);

        assertThrows(
                AccessDeniedException.class,
                () -> service.validateUserGenerationAccess(
                        user,
                        template,
                        null));

        verifyNoInteractions(
                deviceService,
                assetService,
                reportVariableConfigService);
    }

    @Test
    void invalidExecutionProducesTypedAccessDeniedError() {
        ReportServiceException exception =
                assertThrows(
                        ReportServiceException.class,
                        () -> service.validateExecutionScope(
                                null,
                                null));

        assertThat(exception.getErrorCode())
                .isEqualTo(ReportErrorCode.ACCESS_DENIED);
    }

    private CustomerId customerId() {
        return new CustomerId(UUID.randomUUID());
    }

    private ReportTemplate template(
            ReportScopeType scopeType,
            CustomerId templateCustomerId,
            CustomerId filterCustomerId,
            String entityType,
            List<EntityId> entityIds) {

        ReportEntityFilter filter =
                new ReportEntityFilter();

        filter.setScopeType(scopeType);
        filter.setCustomerId(filterCustomerId);
        filter.setEntityType(entityType);
        filter.setEntityIds(entityIds);

        ReportTemplate template =
                new ReportTemplate();

        template.setTenantId(tenantId);
        template.setCustomerId(templateCustomerId);
        template.setScopeType(scopeType);
        template.setEntityFilter(filter);
        template.setSections(List.of());

        return template;
    }

    private ReportVariableConfig variable(
            EntityId entityId) {

        ReportVariableConfig variable =
                new ReportVariableConfig();

        variable.setEntityId(entityId);
        variable.setKey("temperature");

        return variable;
    }

    private void allowDevice(
            UUID deviceUuid,
            CustomerId customerId) {

        Device device = mock(Device.class);

        when(device.getCustomerId())
                .thenReturn(customerId);

        when(deviceService.findDeviceById(
                tenantId,
                new DeviceId(deviceUuid)))
                .thenReturn(device);
    }

    private void allowAsset(
            UUID assetUuid,
            CustomerId customerId) {

        Asset asset = mock(Asset.class);

        when(asset.getCustomerId())
                .thenReturn(customerId);

        when(assetService.findAssetById(
                tenantId,
                new AssetId(assetUuid)))
                .thenReturn(asset);
    }

    private void stubNoVariables(
            ReportTemplate template) {

        when(reportVariableConfigService.extractVariables(
                template.getSections()))
                .thenReturn(List.of());
    }

    private void assertExecutionDenied(
            Executable executable) {

        ReportServiceException exception =
                assertThrows(
                        ReportServiceException.class,
                        executable);

        assertThat(exception.getErrorCode())
                .isEqualTo(ReportErrorCode.ACCESS_DENIED);
    }

}
