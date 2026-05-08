package org.thingsboard.server.service.report;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportTemplateStatus;
import org.thingsboard.server.dao.report.ReportTemplateDao;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultReportTemplateService implements ReportTemplateService {

    private final ReportTemplateDao reportTemplateDao;
    private final ReportValidationService reportValidationService;

    @Override
    public ReportTemplate save(TenantId tenantId, UUID userId, ReportTemplate reportTemplate) {
        reportValidationService.validateTemplateForSave(reportTemplate);

        reportTemplate.setTenantId(tenantId);

        long now = System.currentTimeMillis();

        if (reportTemplate.getId() == null) {
            reportTemplate.setCreatedTime(now);
            reportTemplate.setCreatedBy(userId);
            if (reportTemplate.getStatus() == null) {
                reportTemplate.setStatus(ReportTemplateStatus.DRAFT);
            }
        } else {
            ReportTemplate existing = findById(tenantId, reportTemplate.getId().getId());
            reportTemplate.setCreatedTime(existing.getCreatedTime());
            reportTemplate.setCreatedBy(existing.getCreatedBy());
        }

        reportTemplate.setUpdatedTime(now);
        reportTemplate.setUpdatedBy(userId);

        return reportTemplateDao.save(tenantId, reportTemplate);
    }

    @Override
    public ReportTemplate findById(TenantId tenantId, UUID templateId) {
        return reportTemplateDao.findById(tenantId, templateId)
                .orElseThrow(() -> new ReportServiceException(
                        ReportErrorCode.TEMPLATE_NOT_FOUND,
                        "Report template not found: " + templateId
                ));
    }

    @Override
    public Page<ReportTemplate> findByTenantId(TenantId tenantId, Pageable pageable) {
        return reportTemplateDao.findByTenantId(tenantId, pageable);
    }

    @Override
    public void delete(TenantId tenantId, UUID templateId) {
        ReportTemplate existing = findById(tenantId, templateId);
        if (Boolean.TRUE.equals(existing.getSystem())) {
            throw new ReportServiceException(
                    ReportErrorCode.ACCESS_DENIED,
                    "System report templates cannot be deleted"
            );
        }
        reportTemplateDao.removeById(tenantId, templateId);
    }
}