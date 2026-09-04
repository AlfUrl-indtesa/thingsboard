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
package org.thingsboard.server.dao.sql.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thingsboard.server.common.data.report.ReportTemplateStatus;
import org.thingsboard.server.common.data.report.ReportType;
import org.thingsboard.server.dao.model.sql.ReportTemplateEntity;

import java.util.List;
import java.util.UUID;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplateEntity, UUID> {

    Page<ReportTemplateEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<ReportTemplateEntity> findByTenantIdAndStatus(UUID tenantId, ReportTemplateStatus status, Pageable pageable);

    Page<ReportTemplateEntity> findByTenantIdAndType(UUID tenantId, ReportType type, Pageable pageable);

    List<ReportTemplateEntity> findByTenantIdAndStatus(UUID tenantId, ReportTemplateStatus status);

    Page<ReportTemplateEntity> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId, Pageable pageable);
}