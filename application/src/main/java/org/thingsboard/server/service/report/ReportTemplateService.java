package org.thingsboard.server.service.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportTemplate;

import java.util.UUID;

public interface ReportTemplateService {

    ReportTemplate save(TenantId tenantId, UUID userId, ReportTemplate reportTemplate);

    ReportTemplate findById(TenantId tenantId, UUID templateId);

    Page<ReportTemplate> findByTenantId(TenantId tenantId, Pageable pageable);

    void delete(TenantId tenantId, UUID templateId);
}