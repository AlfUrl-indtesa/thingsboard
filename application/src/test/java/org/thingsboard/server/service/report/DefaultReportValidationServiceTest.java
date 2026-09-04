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
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportEntityFilter;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportScopeType;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportSectionType;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportTemplateStatus;
import org.thingsboard.server.common.data.report.ReportType;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultReportValidationServiceTest {

    private DefaultReportValidationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultReportValidationService();
    }

    @Test
    void validatesEverySupportedScope() {
        for (ReportScopeType scopeType : ReportScopeType.values()) {
            ReportTemplate template = validTemplate(scopeType);

            assertDoesNotThrow(
                    () -> service.validateTemplateForSave(template),
                    "Scope should be valid: " + scopeType);
        }
    }

    @Test
    void rejectsMismatchedScopeTypes() {
        ReportTemplate template =
                validTemplate(ReportScopeType.FIXED_ENTITIES);

        template.getEntityFilter().setScopeType(
                ReportScopeType.TENANT_ENTITIES);

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.validateTemplateForSave(template));

        assertEquals(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                exception.getErrorCode());
    }

    @Test
    void rejectsFixedScopeWithoutEntities() {
        ReportTemplate template =
                validTemplate(ReportScopeType.FIXED_ENTITIES);

        template.getEntityFilter().setEntityIds(List.of());

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.validateTemplateForSave(template));

        assertEquals(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                exception.getErrorCode());
    }

    @Test
    void rejectsCustomerScopeWithoutCustomerId() {
        ReportTemplate template =
                validTemplate(ReportScopeType.CUSTOMER_ENTITIES);

        template.getEntityFilter().setCustomerId(null);

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.validateTemplateForSave(template));

        assertEquals(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                exception.getErrorCode());
    }

    @Test
    void rejectsUnsupportedEntityType() {
        ReportTemplate template =
                validTemplate(ReportScopeType.TENANT_ENTITIES);

        template.getEntityFilter().setEntityType("ENTITY_GROUP");

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.validateTemplateForSave(template));

        assertEquals(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                exception.getErrorCode());
    }

    @Test
    void requiresActiveTemplateForExecution() {
        ReportTemplate template =
                validTemplate(ReportScopeType.FIXED_ENTITIES);

        template.setStatus(ReportTemplateStatus.DRAFT);

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.validateTemplateForExecution(template));

        assertEquals(
                ReportErrorCode.TEMPLATE_DISABLED,
                exception.getErrorCode());

        template.setStatus(ReportTemplateStatus.ACTIVE);

        assertDoesNotThrow(
                () -> service.validateTemplateForExecution(template));
    }

    @Test
    void validatesGenerateRequestTimeRange() {
        GenerateReportRequest request = new GenerateReportRequest();
        request.setStartTs(100L);
        request.setEndTs(200L);

        assertDoesNotThrow(
                () -> service.validateGenerateRequest(request));

        request.setEndTs(100L);

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.validateGenerateRequest(request));

        assertEquals(
                ReportErrorCode.INVALID_TIME_RANGE,
                exception.getErrorCode());
    }

    @Test
    void requiresAtLeastOneVisibleSection() {
        ReportTemplate template =
                validTemplate(ReportScopeType.FIXED_ENTITIES);

        template.getSections().getFirst().setVisible(false);

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.validateTemplateForSave(template));

        assertEquals(
                ReportErrorCode.UNKNOWN_ERROR,
                exception.getErrorCode());
    }

    private ReportTemplate validTemplate(ReportScopeType scopeType) {
        ReportEntityFilter entityFilter = new ReportEntityFilter();
        entityFilter.setScopeType(scopeType);
        entityFilter.setEntityType("DEVICE");

        if (scopeType == ReportScopeType.FIXED_ENTITIES) {
            entityFilter.setEntityIds(List.of(
                    new DeviceId(UUID.randomUUID())));
        }

        if (scopeType == ReportScopeType.CUSTOMER_ENTITIES) {
            entityFilter.setCustomerId(
                    new CustomerId(UUID.randomUUID()));
        }

        ReportSectionConfig section = new ReportSectionConfig();
        section.setKey("summary");
        section.setTitle("Summary");
        section.setType(ReportSectionType.EXECUTIVE_SUMMARY);
        section.setOrder(0);
        section.setVisible(true);

        ReportTemplate template = new ReportTemplate();
        template.setName("Validation template");
        template.setType(ReportType.CUSTOM);
        template.setScopeType(scopeType);
        template.setEntityFilter(entityFilter);
        template.setSections(List.of(section));

        return template;
    }
}
