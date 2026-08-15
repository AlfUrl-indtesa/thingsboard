package org.thingsboard.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.GenerateReportResponse;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.service.report.ReportExecutionService;
import org.thingsboard.server.service.report.ReportTemplateService;
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
public class ReportTemplateController extends BaseController {

    private final ReportTemplateService reportTemplateService;
    private final ReportExecutionService reportExecutionService;

    @PostMapping("/report-templates")
    @ResponseBody
    public ReportTemplate saveReportTemplate(@Valid @RequestBody ReportTemplate reportTemplate) throws Exception {
        TenantId tenantId = getTenantId();
        UUID userId = getCurrentUser().getId().getId();
        return reportTemplateService.save(tenantId, userId, reportTemplate);
    }

    @GetMapping("/report-templates/{templateId}")
    @ResponseBody
    public ReportTemplate getReportTemplateById(@PathVariable("templateId") String strTemplateId) throws Exception {
        checkParameter("templateId", strTemplateId);
        TenantId tenantId = getTenantId();
        UUID templateId = UUID.fromString(strTemplateId);
        return reportTemplateService.findById(tenantId, templateId);
    }

    @GetMapping("/report-templates")
    @ResponseBody
    public Page<ReportTemplate> getReportTemplates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) throws Exception {
        TenantId tenantId = getTenantId();
        return reportTemplateService.findByTenantId(tenantId, PageRequest.of(page, pageSize));
    }

    @DeleteMapping("/report-templates/{templateId}")
    public void deleteReportTemplate(@PathVariable("templateId") String strTemplateId) throws Exception {
        checkParameter("templateId", strTemplateId);
        TenantId tenantId = getTenantId();
        UUID templateId = UUID.fromString(strTemplateId);
        reportTemplateService.delete(tenantId, templateId);
    }

    @PostMapping("/report-templates/{templateId}/generate")
    @ResponseBody
    public GenerateReportResponse generateReport(
            @PathVariable("templateId") String strTemplateId,
            @Valid @RequestBody GenerateReportRequest request) throws Exception {
        checkParameter("templateId", strTemplateId);

        TenantId tenantId = getTenantId();
        UUID userId = getCurrentUser().getId().getId();
        UUID templateId = UUID.fromString(strTemplateId);

        var execution = reportExecutionService.generate(tenantId, userId, templateId, request);
        return new GenerateReportResponse(execution.getId().getId(), execution.getStatus());
    }
}