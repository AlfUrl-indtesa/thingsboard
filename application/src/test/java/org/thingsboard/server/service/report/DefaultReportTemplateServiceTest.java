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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.server.common.data.id.ReportTemplateId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportTemplateStatus;
import org.thingsboard.server.dao.report.ReportTemplateDao;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultReportTemplateServiceTest {

    private final TenantId tenantId =
            TenantId.fromUUID(UUID.randomUUID());

    private final UUID userId = UUID.randomUUID();

    @Mock
    private ReportTemplateDao reportTemplateDao;

    @Mock
    private ReportValidationService reportValidationService;

    @InjectMocks
    private DefaultReportTemplateService service;

    @Test
    void initializesOwnershipAndAuditFieldsForNewTemplate() {
        ReportTemplate template = new ReportTemplate();
        template.setStatus(null);

        when(reportTemplateDao.save(tenantId, template))
                .thenReturn(template);

        long beforeSave = System.currentTimeMillis();

        ReportTemplate result =
                service.save(tenantId, userId, template);

        long afterSave = System.currentTimeMillis();

        assertSame(template, result);
        assertSame(tenantId, result.getTenantId());
        assertEquals(ReportTemplateStatus.DRAFT, result.getStatus());
        assertEquals(userId, result.getCreatedBy());
        assertEquals(userId, result.getUpdatedBy());
        assertNotNull(result.getUpdatedTime());
        assertTrue(result.getCreatedTime() >= beforeSave);
        assertTrue(result.getCreatedTime() <= afterSave);
        assertEquals(result.getCreatedTime(), result.getUpdatedTime());

        verify(reportValidationService)
                .validateTemplateForSave(template);

        verify(reportTemplateDao)
                .save(tenantId, template);
    }

    @Test
    void preservesCreationAuditFieldsWhenUpdatingTemplate() {
        UUID templateUuid = UUID.randomUUID();
        UUID originalCreator = UUID.randomUUID();

        ReportTemplate existing = new ReportTemplate();
        existing.setId(new ReportTemplateId(templateUuid));
        existing.setCreatedTime(12345L);
        existing.setCreatedBy(originalCreator);

        ReportTemplate update = new ReportTemplate();
        update.setId(new ReportTemplateId(templateUuid));

        when(reportTemplateDao.findById(tenantId, templateUuid))
                .thenReturn(Optional.of(existing));

        when(reportTemplateDao.save(tenantId, update))
                .thenReturn(update);

        ReportTemplate result =
                service.save(tenantId, userId, update);

        assertEquals(12345L, result.getCreatedTime());
        assertEquals(originalCreator, result.getCreatedBy());
        assertEquals(userId, result.getUpdatedBy());
        assertSame(tenantId, result.getTenantId());

        verify(reportTemplateDao)
                .save(tenantId, update);
    }

    @Test
    void returnsTypedErrorWhenTemplateDoesNotExist() {
        UUID templateUuid = UUID.randomUUID();

        when(reportTemplateDao.findById(tenantId, templateUuid))
                .thenReturn(Optional.empty());

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.findById(tenantId, templateUuid));

        assertEquals(
                ReportErrorCode.TEMPLATE_NOT_FOUND,
                exception.getErrorCode());
    }

    @Test
    void preventsDeletingSystemTemplate() {
        UUID templateUuid = UUID.randomUUID();

        ReportTemplate template = new ReportTemplate();
        template.setId(new ReportTemplateId(templateUuid));
        template.setSystem(true);

        when(reportTemplateDao.findById(tenantId, templateUuid))
                .thenReturn(Optional.of(template));

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.delete(tenantId, templateUuid));

        assertEquals(
                ReportErrorCode.ACCESS_DENIED,
                exception.getErrorCode());

        verify(reportTemplateDao, never())
                .removeById(tenantId, templateUuid);
    }

    @Test
    void deletesNonSystemTemplate() {
        UUID templateUuid = UUID.randomUUID();

        ReportTemplate template = new ReportTemplate();
        template.setId(new ReportTemplateId(templateUuid));
        template.setSystem(false);

        when(reportTemplateDao.findById(tenantId, templateUuid))
                .thenReturn(Optional.of(template));

        service.delete(tenantId, templateUuid);

        verify(reportTemplateDao)
                .removeById(tenantId, templateUuid);
    }
}
