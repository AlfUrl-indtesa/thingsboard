package org.thingsboard.server.dao.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thingsboard.server.common.data.report.ReportExecutionStatus;
import org.thingsboard.server.dao.model.sql.ReportExecutionEntity;

import java.util.UUID;

public interface ReportExecutionRepository extends JpaRepository<ReportExecutionEntity, UUID> {

    Page<ReportExecutionEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<ReportExecutionEntity> findByTenantIdAndTemplateId(UUID tenantId, UUID templateId, Pageable pageable);

    Page<ReportExecutionEntity> findByTenantIdAndStatus(UUID tenantId, ReportExecutionStatus status, Pageable pageable);

    Page<ReportExecutionEntity> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId, Pageable pageable);
}