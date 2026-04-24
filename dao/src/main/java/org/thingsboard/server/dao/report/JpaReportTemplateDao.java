package org.thingsboard.server.dao.report;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.dao.model.sql.ReportTemplateEntity;

import java.util.Optional;
import java.util.UUID;

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
    public void removeById(TenantId tenantId, UUID id) {
        repository.findById(id)
                .filter(entity -> entity.getTenantId().equals(tenantId.getId()))
                .ifPresent(repository::delete);
    }
}