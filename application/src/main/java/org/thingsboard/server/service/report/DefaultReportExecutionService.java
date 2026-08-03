/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.common.data.report.ReportExecutionStatus;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.dao.report.ReportExecutionDao;

import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultReportExecutionService
                implements ReportExecutionService {

        private final ReportExecutionDao reportExecutionDao;
        private final ReportTemplateService reportTemplateService;
        private final ReportValidationService reportValidationService;
        private final ReportRequestBuilderService reportRequestBuilderService;
        private final ReportPayloadBuilderService reportPayloadBuilderService;
        private final ReportRenderService reportRenderService;
        private final ReportStorageService reportStorageService;

        @Override
        public ReportExecution generate(
                        TenantId tenantId,
                        UUID userId,
                        UUID templateId,
                        GenerateReportRequest request) {

                reportValidationService.validateGenerateRequest(request);

                ReportTemplate template = reportTemplateService.findById(
                                tenantId,
                                templateId);

                reportValidationService.validateTemplateForExecution(template);

                long now = System.currentTimeMillis();

                ReportExecution execution = new ReportExecution();

                execution.setTenantId(tenantId);
                execution.setCustomerId(template.getCustomerId());
                execution.setTemplateId(template.getId());
                execution.setTemplateNameSnapshot(template.getName());
                execution.setReportType(template.getType());
                execution.setStatus(ReportExecutionStatus.PENDING);
                execution.setRequestedBy(userId);
                execution.setRequestedTime(now);
                execution.setCreatedTime(now);

                JsonNode executionRequest = reportRequestBuilderService.buildExecutionRequest(
                                template,
                                request);

                execution.setExecutionRequest(executionRequest);

                execution = reportExecutionDao.save(
                                tenantId,
                                execution);

                Path stagingFile = null;

                try {
                        execution.setStatus(
                                        ReportExecutionStatus.RUNNING);

                        execution.setStartedTime(
                                        System.currentTimeMillis());

                        execution = reportExecutionDao.save(
                                        tenantId,
                                        execution);

                        JsonNode payload = reportPayloadBuilderService.buildPayload(
                                        template,
                                        request);

                        execution.setPayloadSnapshot(payload);

                        execution = reportExecutionDao.save(
                                        tenantId,
                                        execution);

                        stagingFile = reportStorageService.createStagingFile(
                                        tenantId,
                                        execution);

                        RenderedReportFile renderedFile = reportRenderService.renderPdf(
                                        payload,
                                        stagingFile);

                        execution = reportStorageService.storeGeneratedFile(
                                        tenantId,
                                        execution,
                                        renderedFile,
                                        buildOutputFileName(
                                                        template,
                                                        execution),
                                        "application/pdf");

                        execution.setStatus(
                                        ReportExecutionStatus.SUCCESS);

                        execution.setFinishedTime(
                                        System.currentTimeMillis());

                        return reportExecutionDao.save(
                                        tenantId,
                                        execution);

                } catch (ReportServiceException e) {
                        execution.setStatus(
                                        ReportExecutionStatus.FAILED);

                        execution.setFinishedTime(
                                        System.currentTimeMillis());

                        execution.setErrorCode(
                                        e.getErrorCode());

                        execution.setErrorMessage(
                                        e.getMessage());

                        reportExecutionDao.save(
                                        tenantId,
                                        execution);

                        throw e;

                } catch (Exception e) {
                        execution.setStatus(
                                        ReportExecutionStatus.FAILED);

                        execution.setFinishedTime(
                                        System.currentTimeMillis());

                        execution.setErrorCode(
                                        ReportErrorCode.UNKNOWN_ERROR);

                        execution.setErrorMessage(
                                        e.getMessage());

                        reportExecutionDao.save(
                                        tenantId,
                                        execution);

                        throw new ReportServiceException(
                                        ReportErrorCode.UNKNOWN_ERROR,
                                        "Unexpected error during report generation",
                                        e);

                } finally {
                        reportStorageService.cleanupStagingFile(
                                        tenantId,
                                        stagingFile);
                }
        }

        private String buildOutputFileName(
                        ReportTemplate template,
                        ReportExecution execution) {

                String baseName = template.getName() != null
                                && !template.getName().isBlank()
                                                ? template.getName()
                                                                .trim()
                                                                .replaceAll(
                                                                                "[^a-zA-Z0-9._-]",
                                                                                "_")
                                                : "report";

                String executionId = execution.getId() != null
                                ? execution.getId().toString()
                                : String.valueOf(
                                                System.currentTimeMillis());

                return baseName
                                + "_"
                                + executionId
                                + ".pdf";
        }

        @Override
        public ReportExecution findById(
                        TenantId tenantId,
                        UUID executionId) {

                return reportExecutionDao
                                .findById(
                                                tenantId,
                                                executionId)
                                .orElseThrow(() -> new ReportServiceException(
                                                ReportErrorCode.UNKNOWN_ERROR,
                                                "Report execution not found: "
                                                                + executionId));
        }

        @Override
        public Page<ReportExecution> findByTenantId(
                        TenantId tenantId,
                        Pageable pageable) {

                return reportExecutionDao.findByTenantId(
                                tenantId,
                                pageable);
        }

        @Override
        public Page<ReportExecution> findByTenantIdAndTemplateId(
                        TenantId tenantId,
                        UUID templateId,
                        Pageable pageable) {

                return reportExecutionDao
                                .findByTenantIdAndTemplateId(
                                                tenantId,
                                                templateId,
                                                pageable);
        }

        @Override
        public void delete(
                        TenantId tenantId,
                        UUID executionId) {

                ReportExecution execution = findById(
                                tenantId,
                                executionId);

                reportStorageService.deleteFile(
                                tenantId,
                                execution);

                reportExecutionDao.removeById(
                                tenantId,
                                executionId);
        }
}