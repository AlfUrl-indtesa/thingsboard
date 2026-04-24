package org.thingsboard.server.dao.report;

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