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
import org.springframework.security.access.AccessDeniedException;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportAccessServiceTest {

    private final ReportAccessService service =
            new ReportAccessService();

    @Test
    void allowsTenantAdminInsideOwnTenant() {
        TenantId tenantId = TenantId.fromUUID(UUID.randomUUID());
        SecurityUser user = user(
                Authority.TENANT_ADMIN,
                tenantId,
                null,
                UUID.randomUUID());

        ReportTemplate template = new ReportTemplate();
        template.setTenantId(tenantId);

        assertDoesNotThrow(
                () -> service.checkTemplateRead(user, template));
    }

    @Test
    void rejectsResourceFromAnotherTenant() {
        SecurityUser user = user(
                Authority.TENANT_ADMIN,
                TenantId.fromUUID(UUID.randomUUID()),
                null,
                UUID.randomUUID());

        ReportTemplate template = new ReportTemplate();
        template.setTenantId(
                TenantId.fromUUID(UUID.randomUUID()));

        assertThrows(
                AccessDeniedException.class,
                () -> service.checkTemplateRead(user, template));
    }

    @Test
    void customerCanReadOnlyOwnCustomerTemplate() {
        TenantId tenantId = TenantId.fromUUID(UUID.randomUUID());
        CustomerId customerId = new CustomerId(UUID.randomUUID());

        SecurityUser user = user(
                Authority.CUSTOMER_USER,
                tenantId,
                customerId,
                UUID.randomUUID());

        ReportTemplate template = new ReportTemplate();
        template.setTenantId(tenantId);
        template.setCustomerId(customerId);

        assertDoesNotThrow(
                () -> service.checkTemplateRead(user, template));

        template.setCustomerId(
                new CustomerId(UUID.randomUUID()));

        assertThrows(
                AccessDeniedException.class,
                () -> service.checkTemplateRead(user, template));
    }

    @Test
    void customerCanReadOnlyOwnExecution() {
        TenantId tenantId = TenantId.fromUUID(UUID.randomUUID());
        CustomerId customerId = new CustomerId(UUID.randomUUID());
        UUID userId = UUID.randomUUID();

        SecurityUser user = user(
                Authority.CUSTOMER_USER,
                tenantId,
                customerId,
                userId);

        ReportExecution execution = new ReportExecution();
        execution.setTenantId(tenantId);
        execution.setCustomerId(customerId);
        execution.setRequestedBy(userId);

        assertDoesNotThrow(
                () -> service.checkExecutionRead(user, execution));

        execution.setRequestedBy(UUID.randomUUID());

        assertThrows(
                AccessDeniedException.class,
                () -> service.checkExecutionRead(user, execution));
    }

    @Test
    void rejectsNullUserAndNullResources() {
        ReportTemplate template = new ReportTemplate();

        assertThrows(
                AccessDeniedException.class,
                () -> service.checkTemplateRead(null, template));

        SecurityUser user = user(
                Authority.TENANT_ADMIN,
                TenantId.fromUUID(UUID.randomUUID()),
                null,
                UUID.randomUUID());

        assertThrows(
                AccessDeniedException.class,
                () -> service.checkTemplateRead(user, null));

        assertThrows(
                AccessDeniedException.class,
                () -> service.checkExecutionRead(user, null));
    }

    private SecurityUser user(
            Authority authority,
            TenantId tenantId,
            CustomerId customerId,
            UUID userUuid) {

        SecurityUser user = mock(SecurityUser.class);

        when(user.getAuthority()).thenReturn(authority);
        when(user.getTenantId()).thenReturn(tenantId);
        when(user.getCustomerId()).thenReturn(customerId);
        when(user.getId()).thenReturn(new UserId(userUuid));

        return user;
    }
}
