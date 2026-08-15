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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.service.report.ReportExecutionService;
import org.thingsboard.server.service.report.ReportStorageService;
import org.springframework.security.access.prepost.PreAuthorize;

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

        @GetMapping("/report-executions/{executionId}")
        @ResponseBody
        public ReportExecution getReportExecutionById(
                        @PathVariable("executionId") String strExecutionId)
                        throws Exception {

                checkParameter(
                                "executionId",
                                strExecutionId);

                TenantId tenantId = getTenantId();

                UUID executionId = UUID.fromString(
                                strExecutionId);

                return reportExecutionService.findById(
                                tenantId,
                                executionId);
        }

        @GetMapping("/report-executions")
        @ResponseBody
        public Page<ReportExecution> getReportExecutions(
                        @RequestParam(defaultValue = "0") int page,

                        @RequestParam(defaultValue = "10") int pageSize)
                        throws Exception {

                TenantId tenantId = getTenantId();

                return reportExecutionService.findByTenantId(
                                tenantId,
                                PageRequest.of(
                                                page,
                                                pageSize));
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

                TenantId tenantId = getTenantId();

                UUID templateId = UUID.fromString(
                                strTemplateId);

                return reportExecutionService
                                .findByTenantIdAndTemplateId(
                                                tenantId,
                                                templateId,
                                                PageRequest.of(
                                                                page,
                                                                pageSize));
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

                Resource resource = reportStorageService.loadFile(
                                tenantId,
                                execution);

                String fileName = execution.getFileName() != null
                                && !execution.getFileName()
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