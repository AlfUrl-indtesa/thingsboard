package org.thingsboard.server.dao.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportTemplate;

import java.util.Optional;
import java.util.UUID;
import org.thingsboard.server.common.data.id.CustomerId;

public interface ReportTemplateDao {

    ReportTemplate save(TenantId tenantId, ReportTemplate reportTemplate);

    Optional<ReportTemplate> findById(TenantId tenantId, UUID id);

    Page<ReportTemplate> findByTenantId(TenantId tenantId, Pageable pageable);

    Page<ReportTemplate> findByTenantIdAndCustomerId(
            TenantId tenantId,
            CustomerId customerId,
            Pageable pageable);

    void removeById(TenantId tenantId, UUID id);
}