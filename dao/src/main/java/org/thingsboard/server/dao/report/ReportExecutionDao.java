package org.thingsboard.server.dao.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportExecution;

import java.util.Optional;
import java.util.UUID;

public interface ReportExecutionDao {

    ReportExecution save(TenantId tenantId, ReportExecution reportExecution);

    Optional<ReportExecution> findById(TenantId tenantId, UUID id);

    Page<ReportExecution> findByTenantId(TenantId tenantId, Pageable pageable);

    Page<ReportExecution> findByTenantIdAndTemplateId(TenantId tenantId, UUID templateId, Pageable pageable);

    void removeById(TenantId tenantId, UUID id);
}