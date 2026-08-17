/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.GenerateReportResponse;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.service.report.ReportAccessService;
import org.thingsboard.server.service.report.ReportExecutionService;
import org.thingsboard.server.service.report.ReportTemplateService;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
public class ReportTemplateController
        extends BaseController {

    private final ReportTemplateService reportTemplateService;

    private final ReportExecutionService reportExecutionService;

    private final ReportAccessService reportAccessService;

    @PostMapping("/report-templates")
    @ResponseBody
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    public ReportTemplate saveReportTemplate(
            @Valid @RequestBody ReportTemplate reportTemplate)
            throws Exception {

        TenantId tenantId = getTenantId();

        UUID userId = getCurrentUser()
                .getId()
                .getId();

        return reportTemplateService.save(
                tenantId,
                userId,
                reportTemplate);
    }

    @GetMapping("/report-templates/{templateId}")
    @ResponseBody
    public ReportTemplate getReportTemplateById(
            @PathVariable("templateId") String strTemplateId)
            throws Exception {

        checkParameter(
                "templateId",
                strTemplateId);

        SecurityUser user = getCurrentUser();

        TenantId tenantId = getTenantId();

        ReportTemplate template = reportTemplateService.findById(
                tenantId,
                UUID.fromString(
                        strTemplateId));

        reportAccessService.checkTemplateRead(
                user,
                template);

        return template;
    }

    @GetMapping("/report-templates")
    @ResponseBody
    public Page<ReportTemplate> getReportTemplates(
            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "10") int pageSize)
            throws Exception {

        SecurityUser user = getCurrentUser();

        TenantId tenantId = getTenantId();

        PageRequest pageable = PageRequest.of(
                page,
                pageSize);

        if (reportAccessService
                .isTenantAdmin(user)) {

            return reportTemplateService
                    .findByTenantId(
                            tenantId,
                            pageable);
        }

        return reportTemplateService
                .findByTenantIdAndCustomerId(
                        tenantId,
                        user.getCustomerId(),
                        pageable);
    }

    @DeleteMapping("/report-templates/{templateId}")
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    public void deleteReportTemplate(
            @PathVariable("templateId") String strTemplateId)
            throws Exception {

        checkParameter(
                "templateId",
                strTemplateId);

        reportTemplateService.delete(
                getTenantId(),
                UUID.fromString(
                        strTemplateId));
    }

    @PostMapping("/report-templates/{templateId}/generate")
    @ResponseBody
    public GenerateReportResponse generateReport(
            @PathVariable("templateId") String strTemplateId,

            @Valid @RequestBody GenerateReportRequest request)
            throws Exception {

        checkParameter(
                "templateId",
                strTemplateId);

        SecurityUser user = getCurrentUser();

        TenantId tenantId = getTenantId();

        UUID templateId = UUID.fromString(
                strTemplateId);

        ReportTemplate template = reportTemplateService.findById(
                tenantId,
                templateId);

        /*
         * A CUSTOMER_USER cannot generate a template
         * belonging to another customer even if the UUID
         * is supplied manually.
         */
        reportAccessService.checkTemplateRead(
                user,
                template);

        var execution = reportExecutionService.generate(
                tenantId,
                user.getId().getId(),
                templateId,
                request);

        return new GenerateReportResponse(
                execution.getId().getId(),
                execution.getStatus());
    }
}