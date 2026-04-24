package org.thingsboard.server.service.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportExecution;

import java.util.UUID;

public interface ReportExecutionService {

    ReportExecution generate(TenantId tenantId, UUID userId, UUID templateId, GenerateReportRequest request);

    ReportExecution findById(TenantId tenantId, UUID executionId);

    Page<ReportExecution> findByTenantId(TenantId tenantId, Pageable pageable);

    Page<ReportExecution> findByTenantIdAndTemplateId(TenantId tenantId, UUID templateId, Pageable pageable);
}