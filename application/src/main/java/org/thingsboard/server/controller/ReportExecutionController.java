/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.service.report.ReportAccessService;
import org.thingsboard.server.service.report.ReportExecutionService;
import org.thingsboard.server.service.report.ReportStorageService;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
public class ReportExecutionController
                extends BaseController {

        private final ReportExecutionService reportExecutionService;

        private final ReportStorageService reportStorageService;

        private final ReportAccessService reportAccessService;

        @GetMapping("/report-executions/{executionId}")
        @ResponseBody
        public ReportExecution getReportExecutionById(
                        @PathVariable("executionId") String strExecutionId)
                        throws Exception {

                checkParameter(
                                "executionId",
                                strExecutionId);

                SecurityUser user = getCurrentUser();

                ReportExecution execution = reportExecutionService.findById(
                                getTenantId(),
                                UUID.fromString(
                                                strExecutionId));

                reportAccessService.checkExecutionRead(
                                user,
                                execution);

                return sanitizeExecution(
                                execution);
        }

        @GetMapping("/report-executions")
        @ResponseBody
        public Page<ReportExecution> getReportExecutions(
                        @RequestParam(defaultValue = "0") int page,

                        @RequestParam(defaultValue = "10") int pageSize)
                        throws Exception {

                SecurityUser user = getCurrentUser();

                TenantId tenantId = getTenantId();

                PageRequest pageable = PageRequest.of(
                                page,
                                pageSize);

                Page<ReportExecution> executions;

                if (reportAccessService
                                .isTenantAdmin(user)) {

                        executions = reportExecutionService
                                        .findByTenantId(
                                                        tenantId,
                                                        pageable);

                } else {
                        executions = reportExecutionService
                                        .findByTenantIdAndCustomerIdAndRequestedBy(
                                                        tenantId,
                                                        user.getCustomerId(),
                                                        user.getId().getId(),
                                                        pageable);
                }

                return executions.map(
                                this::sanitizeExecution);
        }

        @GetMapping("/report-executions/template/{templateId}")
        @ResponseBody
        public Page<ReportExecution> getReportExecutionsByTemplateId(

                        @PathVariable("templateId") String strTemplateId,

                        @RequestParam(defaultValue = "0") int page,

                        @RequestParam(defaultValue = "10") int pageSize)
                        throws Exception {

                checkParameter(
                                "templateId",
                                strTemplateId);

                SecurityUser user = getCurrentUser();

                TenantId tenantId = getTenantId();

                UUID templateId = UUID.fromString(
                                strTemplateId);

                PageRequest pageable = PageRequest.of(
                                page,
                                pageSize);

                Page<ReportExecution> executions;

                if (reportAccessService
                                .isTenantAdmin(user)) {

                        executions = reportExecutionService
                                        .findByTenantIdAndTemplateId(
                                                        tenantId,
                                                        templateId,
                                                        pageable);

                } else {
                        executions = reportExecutionService
                                        .findByTenantIdAndTemplateIdAndCustomerIdAndRequestedBy(
                                                        tenantId,
                                                        templateId,
                                                        user.getCustomerId(),
                                                        user.getId().getId(),
                                                        pageable);
                }

                return executions.map(
                                this::sanitizeExecution);
        }

        @DeleteMapping("/report-executions/{executionId}")
        public void deleteReportExecution(
                        @PathVariable("executionId") String strExecutionId)
                        throws Exception {

                checkParameter(
                                "executionId",
                                strExecutionId);

                TenantId tenantId = getTenantId();

                UUID executionId = UUID.fromString(
                                strExecutionId);

                ReportExecution execution = reportExecutionService.findById(
                                tenantId,
                                executionId);

                reportAccessService.checkExecutionDelete(
                                getCurrentUser(),
                                execution);

                reportExecutionService.delete(
                                tenantId,
                                executionId);
        }

        @GetMapping("/report-executions/{executionId}/download")
        public ResponseEntity<Resource> downloadReportExecution(

                        @PathVariable("executionId") String strExecutionId)
                        throws Exception {

                checkParameter(
                                "executionId",
                                strExecutionId);

                TenantId tenantId = getTenantId();

                UUID executionId = UUID.fromString(
                                strExecutionId);

                ReportExecution execution = reportExecutionService.findById(
                                tenantId,
                                executionId);

                /*
                 * Authorization MUST occur before obtaining the
                 * filesystem resource.
                 */
                reportAccessService.checkExecutionRead(
                                getCurrentUser(),
                                execution);

                Resource resource = reportStorageService.loadFile(
                                tenantId,
                                execution);

                String fileName = execution.getFileName() != null
                                && !execution
                                                .getFileName()
                                                .isBlank()
                                                                ? execution.getFileName()
                                                                : "report.pdf";

                MediaType mediaType = resolveMediaType(
                                execution.getMimeType());

                long contentLength = resolveContentLength(
                                execution,
                                resource);

                HttpHeaders headers = new HttpHeaders();

                headers.setContentType(
                                mediaType);

                headers.setContentDisposition(
                                ContentDisposition
                                                .attachment()
                                                .filename(
                                                                fileName,
                                                                StandardCharsets.UTF_8)
                                                .build());

                headers.setCacheControl(
                                CacheControl
                                                .noStore()
                                                .getHeaderValue());

                headers.set(
                                "X-Content-Type-Options",
                                "nosniff");

                if (contentLength >= 0) {
                        headers.setContentLength(
                                        contentLength);
                }

                return ResponseEntity
                                .ok()
                                .headers(headers)
                                .body(resource);
        }

        private ReportExecution sanitizeExecution(
                        ReportExecution execution) {

                /*
                 * These properties are internal implementation
                 * details and must not be exposed through the API.
                 */
                execution.setFilePath(null);
                execution.setExternalFileId(null);
                execution.setChecksum(null);
                execution.setPayloadSnapshot(null);

                return execution;
        }

        private MediaType resolveMediaType(
                        String mimeType) {

                if (mimeType == null
                                || mimeType.isBlank()) {

                        return MediaType.APPLICATION_PDF;
                }

                try {
                        return MediaType.parseMediaType(
                                        mimeType);

                } catch (IllegalArgumentException e) {
                        return MediaType.APPLICATION_PDF;
                }
        }

        private long resolveContentLength(
                        ReportExecution execution,
                        Resource resource) {

                Long storedSize = execution.getFileSize();

                if (storedSize != null
                                && storedSize >= 0) {

                        return storedSize;
                }

                try {
                        return resource.contentLength();

                } catch (IOException e) {
                        return -1;
                }
        }
}