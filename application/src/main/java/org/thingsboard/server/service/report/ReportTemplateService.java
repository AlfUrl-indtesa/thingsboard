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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportTemplate;

import java.util.UUID;
import org.thingsboard.server.common.data.id.CustomerId;

public interface ReportTemplateService {

    ReportTemplate save(TenantId tenantId, UUID userId, ReportTemplate reportTemplate);

    ReportTemplate findById(TenantId tenantId, UUID templateId);

    Page<ReportTemplate> findByTenantId(TenantId tenantId, Pageable pageable);

    Page<ReportTemplate> findByTenantIdAndCustomerId(
            TenantId tenantId,
            CustomerId customerId,
            Pageable pageable);

    void delete(TenantId tenantId, UUID templateId);
}
