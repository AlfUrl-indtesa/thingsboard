/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.common.data.report.ReportExecutionStatus;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.dao.report.ReportExecutionDao;
import java.util.Objects;

import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportExecutionJobService {

        private final ReportExecutionDao reportExecutionDao;
        private final ReportTemplateService reportTemplateService;
        private final ReportValidationService reportValidationService;
        private final ReportPayloadBuilderService reportPayloadBuilderService;
        private final ReportRenderService reportRenderService;
        private final ReportStorageService reportStorageService;
        private final ObjectMapper objectMapper;

        public void process(
                        TenantId tenantId,
                        UUID executionId) {

                long startedTime = System.currentTimeMillis();

                /*
                 * Atomic database claim. Only one worker or application
                 * node may change this job from PENDING to RUNNING.
                 */
                boolean claimed = reportExecutionDao.markRunningIfPending(
                                tenantId,
                                executionId,
                                startedTime);

                if (!claimed) {
                        log.debug(
                                        "[report-job] executionId={} was not pending; skipping",
                                        executionId);

                        return;
                }

                ReportExecution execution = reportExecutionDao
                                .findById(
                                                tenantId,
                                                executionId)
                                .orElse(null);

                if (execution == null) {
                        log.warn(
                                        "[report-job] executionId={} disappeared after claim",
                                        executionId);

                        return;
                }

                Path stagingFile = null;

                try {
                        ReportTemplate template = reportTemplateService.findById(
                                        tenantId,
                                        execution
                                                        .getTemplateId()
                                                        .getId());

                        if (!Objects.equals(
                                        execution.getCustomerId(),
                                        template.getCustomerId())) {
                                throw new ReportServiceException(
                                                ReportErrorCode.ACCESS_DENIED,
                                                "Report template customer scope changed after the execution was requested");
                        }

                        reportValidationService
                                        .validateTemplateForExecution(
                                                        template);

                        GenerateReportRequest request = restoreRequest(
                                        execution);

                        reportValidationService
                                        .validateGenerateRequest(
                                                        request);

                        JsonNode payload = reportPayloadBuilderService
                                        .buildPayload(
                                                        template,
                                                        request);

                        execution.setPayloadSnapshot(
                                        payload);

                        execution = reportExecutionDao.save(
                                        tenantId,
                                        execution);

                        stagingFile = reportStorageService
                                        .createStagingFile(
                                                        tenantId,
                                                        execution);

                        RenderedReportFile renderedFile = reportRenderService.renderPdf(
                                        payload,
                                        stagingFile);

                        execution = reportStorageService
                                        .storeGeneratedFile(
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

                        execution.setErrorCode(null);
                        execution.setErrorMessage(null);

                        reportExecutionDao.save(
                                        tenantId,
                                        execution);

                        log.info(
                                        "[report-job] executionId={} completed successfully in {} ms",
                                        executionId,
                                        System.currentTimeMillis()
                                                        - startedTime);

                } catch (ReportServiceException e) {
                        markFailed(
                                        tenantId,
                                        execution,
                                        e.getErrorCode(),
                                        e.getMessage());

                        log.warn(
                                        "[report-job] executionId={} failed: {}",
                                        executionId,
                                        e.getMessage());

                } catch (Exception e) {
                        markFailed(
                                        tenantId,
                                        execution,
                                        ReportErrorCode.UNKNOWN_ERROR,
                                        e.getMessage() != null
                                                        ? e.getMessage()
                                                        : e.getClass().getSimpleName());

                        log.error(
                                        "[report-job] executionId={} failed unexpectedly",
                                        executionId,
                                        e);

                } finally {
                        reportStorageService.cleanupStagingFile(
                                        tenantId,
                                        stagingFile);
                }
        }

        private GenerateReportRequest restoreRequest(
                        ReportExecution execution)
                        throws Exception {

                JsonNode snapshot = execution.getExecutionRequest();

                if (snapshot == null
                                || snapshot.isNull()) {

                        throw new ReportServiceException(
                                        ReportErrorCode.UNKNOWN_ERROR,
                                        "Report execution request snapshot is missing");
                }

                JsonNode requestNode = snapshot.get("request");

                /*
                 * Normal path for executions created after the async
                 * implementation.
                 */
                if (requestNode != null
                                && !requestNode.isNull()) {

                        return objectMapper.treeToValue(
                                        requestNode,
                                        GenerateReportRequest.class);
                }

                /*
                 * Compatibility path for older PENDING records.
                 * Only fields belonging to GenerateReportRequest are copied.
                 */
                ObjectNode compatibleRequest = objectMapper.createObjectNode();

                copyIfPresent(
                                snapshot,
                                compatibleRequest,
                                "startTs");

                copyIfPresent(
                                snapshot,
                                compatibleRequest,
                                "endTs");

                copyIfPresent(
                                snapshot,
                                compatibleRequest,
                                "entityIds");

                copyIfPresent(
                                snapshot,
                                compatibleRequest,
                                "locale");

                copyIfPresent(
                                snapshot,
                                compatibleRequest,
                                "timezone");

                return objectMapper.treeToValue(
                                compatibleRequest,
                                GenerateReportRequest.class);
        }

        private void copyIfPresent(
                        JsonNode source,
                        ObjectNode target,
                        String fieldName) {

                JsonNode value = source.get(fieldName);

                if (value != null
                                && !value.isNull()) {

                        target.set(
                                        fieldName,
                                        value);
                }
        }

        private void markFailed(
                        TenantId tenantId,
                        ReportExecution execution,
                        ReportErrorCode errorCode,
                        String errorMessage) {

                try {
                        execution.setStatus(
                                        ReportExecutionStatus.FAILED);

                        execution.setFinishedTime(
                                        System.currentTimeMillis());

                        execution.setErrorCode(
                                        errorCode);

                        execution.setErrorMessage(
                                        errorMessage);

                        reportExecutionDao.save(
                                        tenantId,
                                        execution);

                } catch (Exception persistenceError) {
                        log.error(
                                        "[report-job] unable to persist FAILED status for executionId={}",
                                        execution.getId() != null
                                                        ? execution.getId().getId()
                                                        : null,
                                        persistenceError);
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
                                ? execution
                                                .getId()
                                                .toString()
                                : String.valueOf(
                                                System.currentTimeMillis());

                return baseName
                                + "_"
                                + executionId
                                + ".pdf";
        }
}