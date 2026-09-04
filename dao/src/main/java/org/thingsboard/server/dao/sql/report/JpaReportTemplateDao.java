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

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.dao.model.sql.ReportTemplateEntity;
import org.thingsboard.server.dao.report.ReportTemplateDao;
import org.thingsboard.server.dao.util.SqlDao;

import java.util.Optional;
import java.util.UUID;
import org.thingsboard.server.common.data.id.CustomerId;

@SqlDao
@Component
@RequiredArgsConstructor
public class JpaReportTemplateDao implements ReportTemplateDao {

    private final ReportTemplateRepository repository;

    @Override
    public ReportTemplate save(TenantId tenantId, ReportTemplate reportTemplate) {
        ReportTemplateEntity entity = new ReportTemplateEntity(reportTemplate);
        return repository.save(entity).toData();
    }

    @Override
    public Optional<ReportTemplate> findById(TenantId tenantId, UUID id) {
        return repository.findById(id)
                .filter(entity -> entity.getTenantId().equals(tenantId.getId()))
                .map(ReportTemplateEntity::toData);
    }

    @Override
    public Page<ReportTemplate> findByTenantId(TenantId tenantId, Pageable pageable) {
        return repository.findByTenantId(tenantId.getId(), pageable)
                .map(ReportTemplateEntity::toData);
    }

    @Override
    public Page<ReportTemplate> findByTenantIdAndCustomerId(
            TenantId tenantId,
            CustomerId customerId,
            Pageable pageable) {

        return repository
                .findByTenantIdAndCustomerId(
                        tenantId.getId(),
                        customerId.getId(),
                        pageable)
                .map(
                        ReportTemplateEntity::toData);
    }

    @Override
    public void removeById(TenantId tenantId, UUID id) {
        repository.findById(id)
                .filter(entity -> entity.getTenantId().equals(tenantId.getId()))
                .ifPresent(repository::delete);
    }
}