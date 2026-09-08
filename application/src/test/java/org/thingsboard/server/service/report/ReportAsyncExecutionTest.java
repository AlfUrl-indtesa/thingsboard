/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.ReportExecutionId;
import org.thingsboard.server.common.data.id.ReportTemplateId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.common.data.report.ReportExecutionStatus;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportType;
import org.thingsboard.server.dao.report.ReportExecutionDao;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportAsyncExecutionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsPendingExecutionAndDispatchesIt() {
        ReportExecutionDao executionDao = mock(ReportExecutionDao.class);
        ReportTemplateService templateService = mock(ReportTemplateService.class);
        ReportValidationService validationService = mock(ReportValidationService.class);
        ReportRequestBuilderService requestBuilderService = mock(ReportRequestBuilderService.class);
        ReportStorageService storageService = mock(ReportStorageService.class);
        ReportExecutionDispatcher dispatcher = mock(ReportExecutionDispatcher.class);

        DefaultReportExecutionService service = new DefaultReportExecutionService(
                executionDao,
                templateService,
                validationService,
                requestBuilderService,
                storageService,
                dispatcher);

        TenantId tenantId = new TenantId(UUID.randomUUID());
        CustomerId customerId = new CustomerId(UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        UUID templateUuid = UUID.randomUUID();
        UUID executionUuid = UUID.randomUUID();
        ReportTemplate template = template(tenantId, customerId, templateUuid);
        GenerateReportRequest request = request();
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("requestVersion", 1);

        when(templateService.findById(tenantId, templateUuid)).thenReturn(template);
        when(requestBuilderService.buildExecutionRequest(template, request)).thenReturn(snapshot);
        when(executionDao.save(eq(tenantId), any(ReportExecution.class))).thenAnswer(invocation -> {
            ReportExecution execution = invocation.getArgument(1);
            execution.setId(new ReportExecutionId(executionUuid));
            return execution;
        });

        ReportExecution result = service.generate(
                tenantId,
                userId,
                templateUuid,
                request);

        assertEquals(ReportExecutionStatus.PENDING, result.getStatus());
        assertEquals(tenantId, result.getTenantId());
        assertEquals(customerId, result.getCustomerId());
        assertEquals(template.getId(), result.getTemplateId());
        assertEquals(userId, result.getRequestedBy());
        assertEquals(snapshot, result.getExecutionRequest());
        assertNotNull(result.getRequestedTime());
        assertNotNull(result.getCreatedTime());

        verify(validationService).validateGenerateRequest(request);
        verify(validationService).validateTemplateForExecution(template);
        verify(dispatcher).submit(tenantId, executionUuid);
    }

    @Test
    void skipsJobWhenAtomicClaimFails() {
        ReportExecutionDao executionDao = mock(ReportExecutionDao.class);
        ReportTemplateService templateService = mock(ReportTemplateService.class);
        ReportValidationService validationService = mock(ReportValidationService.class);
        ReportPayloadBuilderService payloadBuilderService = mock(ReportPayloadBuilderService.class);
        ReportRenderService renderService = mock(ReportRenderService.class);
        ReportStorageService storageService = mock(ReportStorageService.class);

        ReportExecutionJobService service = jobService(
                executionDao,
                templateService,
                validationService,
                payloadBuilderService,
                renderService,
                storageService);

        TenantId tenantId = new TenantId(UUID.randomUUID());
        UUID executionId = UUID.randomUUID();

        when(executionDao.markRunningIfPending(
                eq(tenantId),
                eq(executionId),
                anyLong())).thenReturn(false);

        service.process(tenantId, executionId);

        verify(executionDao, never()).findById(tenantId, executionId);
        verifyNoInteractions(
                templateService,
                validationService,
                payloadBuilderService,
                renderService,
                storageService);
    }

    @Test
    void completesClaimedJobSuccessfully() {
        ReportExecutionDao executionDao = mock(ReportExecutionDao.class);
        ReportTemplateService templateService = mock(ReportTemplateService.class);
        ReportValidationService validationService = mock(ReportValidationService.class);
        ReportPayloadBuilderService payloadBuilderService = mock(ReportPayloadBuilderService.class);
        ReportRenderService renderService = mock(ReportRenderService.class);
        ReportStorageService storageService = mock(ReportStorageService.class);

        ReportExecutionJobService service = jobService(
                executionDao,
                templateService,
                validationService,
                payloadBuilderService,
                renderService,
                storageService);

        TenantId tenantId = new TenantId(UUID.randomUUID());
        CustomerId customerId = new CustomerId(UUID.randomUUID());
        UUID templateId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        ReportTemplate template = template(tenantId, customerId, templateId);
        ReportExecution execution = execution(tenantId, customerId, templateId, executionId);
        JsonNode payload = objectMapper.createObjectNode().put("kind", "report");
        Path stagingFile = temporaryDirectory.resolve("report.pdf.part");
        RenderedReportFile renderedFile = new RenderedReportFile(
                stagingFile,
                25,
                "a".repeat(64),
                "request-1");

        when(executionDao.markRunningIfPending(
                eq(tenantId),
                eq(executionId),
                anyLong())).thenReturn(true);
        when(executionDao.findById(tenantId, executionId)).thenReturn(Optional.of(execution));
        when(templateService.findById(tenantId, templateId)).thenReturn(template);
        when(payloadBuilderService.buildPayload(eq(template), any(GenerateReportRequest.class)))
                .thenReturn(payload);
        when(executionDao.save(eq(tenantId), same(execution))).thenReturn(execution);
        when(storageService.createStagingFile(tenantId, execution)).thenReturn(stagingFile);
        when(renderService.renderPdf(payload, stagingFile)).thenReturn(renderedFile);
        when(storageService.storeGeneratedFile(
                eq(tenantId),
                same(execution),
                same(renderedFile),
                anyString(),
                eq("application/pdf"))).thenReturn(execution);

        service.process(tenantId, executionId);

        assertEquals(ReportExecutionStatus.SUCCESS, execution.getStatus());
        assertEquals(payload, execution.getPayloadSnapshot());
        assertNotNull(execution.getFinishedTime());
        assertEquals(null, execution.getErrorCode());
        assertEquals(null, execution.getErrorMessage());

        verify(executionDao, times(2)).save(tenantId, execution);
        verify(storageService).cleanupStagingFile(tenantId, stagingFile);
    }

    @Test
    void persistsFailureAndCleansStagingState() {
        ReportExecutionDao executionDao = mock(ReportExecutionDao.class);
        ReportTemplateService templateService = mock(ReportTemplateService.class);
        ReportValidationService validationService = mock(ReportValidationService.class);
        ReportPayloadBuilderService payloadBuilderService = mock(ReportPayloadBuilderService.class);
        ReportRenderService renderService = mock(ReportRenderService.class);
        ReportStorageService storageService = mock(ReportStorageService.class);

        ReportExecutionJobService service = jobService(
                executionDao,
                templateService,
                validationService,
                payloadBuilderService,
                renderService,
                storageService);

        TenantId tenantId = new TenantId(UUID.randomUUID());
        CustomerId customerId = new CustomerId(UUID.randomUUID());
        UUID templateId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        ReportTemplate template = template(tenantId, customerId, templateId);
        ReportExecution execution = execution(tenantId, customerId, templateId, executionId);

        when(executionDao.markRunningIfPending(
                eq(tenantId),
                eq(executionId),
                anyLong())).thenReturn(true);
        when(executionDao.findById(tenantId, executionId)).thenReturn(Optional.of(execution));
        when(templateService.findById(tenantId, templateId)).thenReturn(template);
        when(payloadBuilderService.buildPayload(eq(template), any(GenerateReportRequest.class)))
                .thenThrow(new ReportServiceException(
                        ReportErrorCode.DATA_COLLECTION_FAILED,
                        "telemetry unavailable"));
        when(executionDao.save(tenantId, execution)).thenReturn(execution);

        service.process(tenantId, executionId);

        assertEquals(ReportExecutionStatus.FAILED, execution.getStatus());
        assertEquals(ReportErrorCode.DATA_COLLECTION_FAILED, execution.getErrorCode());
        assertEquals("telemetry unavailable", execution.getErrorMessage());
        assertNotNull(execution.getFinishedTime());

        verify(executionDao).save(tenantId, execution);
        verify(storageService).cleanupStagingFile(tenantId, null);
        verifyNoInteractions(renderService);
    }

    @Test
    void executesInlineWhenAsyncModeIsDisabled() {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        ReportExecutionJobService jobService = mock(ReportExecutionJobService.class);
        ReportExecutionDao executionDao = mock(ReportExecutionDao.class);
        ReportAsyncProperties properties = new ReportAsyncProperties();
        properties.setEnabled(false);

        ReportExecutionDispatcher dispatcher = new ReportExecutionDispatcher(
                executor,
                jobService,
                executionDao,
                properties);

        TenantId tenantId = new TenantId(UUID.randomUUID());
        UUID executionId = UUID.randomUUID();

        dispatcher.submit(tenantId, executionId);

        verify(jobService).process(tenantId, executionId);
        verifyNoInteractions(executor);
    }

    @Test
    void suppressesDuplicateQueuedJobsUntilTaskFinishes() {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        ReportExecutionJobService jobService = mock(ReportExecutionJobService.class);
        ReportExecutionDao executionDao = mock(ReportExecutionDao.class);
        ReportAsyncProperties properties = new ReportAsyncProperties();

        ReportExecutionDispatcher dispatcher = new ReportExecutionDispatcher(
                executor,
                jobService,
                executionDao,
                properties);

        TenantId tenantId = new TenantId(UUID.randomUUID());
        UUID executionId = UUID.randomUUID();

        dispatcher.submit(tenantId, executionId);
        dispatcher.submit(tenantId, executionId);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(taskCaptor.capture());

        taskCaptor.getValue().run();
        dispatcher.submit(tenantId, executionId);

        verify(jobService).process(tenantId, executionId);
        verify(executor, times(2)).execute(any(Runnable.class));
    }

    @Test
    void releasesQueueReservationAfterRejection() {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        ReportExecutionJobService jobService = mock(ReportExecutionJobService.class);
        ReportExecutionDao executionDao = mock(ReportExecutionDao.class);
        ReportAsyncProperties properties = new ReportAsyncProperties();

        doThrow(new TaskRejectedException("queue full"))
                .doNothing()
                .when(executor)
                .execute(any(Runnable.class));

        ReportExecutionDispatcher dispatcher = new ReportExecutionDispatcher(
                executor,
                jobService,
                executionDao,
                properties);

        TenantId tenantId = new TenantId(UUID.randomUUID());
        UUID executionId = UUID.randomUUID();

        dispatcher.submit(tenantId, executionId);
        dispatcher.submit(tenantId, executionId);

        verify(executor, times(2)).execute(any(Runnable.class));
        verifyNoInteractions(jobService);
    }

    @Test
    void recoversStaleJobsAndQueuesPendingOnes() {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        ReportExecutionJobService jobService = mock(ReportExecutionJobService.class);
        ReportExecutionDao executionDao = mock(ReportExecutionDao.class);
        ReportAsyncProperties properties = new ReportAsyncProperties();
        properties.setRecoveryBatchSize(7);
        properties.setStaleRunningTimeoutMinutes(2);

        TenantId tenantId = new TenantId(UUID.randomUUID());
        UUID executionId = UUID.randomUUID();
        ReportExecution pending = new ReportExecution();
        pending.setTenantId(tenantId);
        pending.setId(new ReportExecutionId(executionId));

        when(executionDao.resetStaleRunning(anyLong())).thenReturn(1);
        when(executionDao.findByStatus(
                eq(ReportExecutionStatus.PENDING),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(pending)));

        ReportExecutionDispatcher dispatcher = new ReportExecutionDispatcher(
                executor,
                jobService,
                executionDao,
                properties);

        dispatcher.recoverAndDispatch();

        verify(executionDao).resetStaleRunning(anyLong());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(executionDao).findByStatus(
                eq(ReportExecutionStatus.PENDING),
                pageableCaptor.capture());

        assertEquals(7, pageableCaptor.getValue().getPageSize());
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void clampsUnsafeExecutorConfiguration() {
        ReportAsyncProperties properties = new ReportAsyncProperties();
        properties.setCorePoolSize(0);
        properties.setMaxPoolSize(0);
        properties.setQueueCapacity(-5);
        properties.setKeepAliveSeconds(0);
        properties.setShutdownAwaitTerminationSeconds(0);

        ThreadPoolTaskExecutor executor = new ReportAsyncConfiguration()
                .reportTaskExecutor(properties);

        try {
            assertEquals(1, executor.getCorePoolSize());
            assertEquals(1, executor.getMaxPoolSize());
            assertEquals("report-worker-", executor.getThreadNamePrefix());
            assertTrue(executor.getThreadPoolExecutor().getQueue().isEmpty());
            assertFalse(executor.getThreadPoolExecutor().allowsCoreThreadTimeOut());
        } finally {
            executor.destroy();
        }
    }

    private ReportExecutionJobService jobService(
            ReportExecutionDao executionDao,
            ReportTemplateService templateService,
            ReportValidationService validationService,
            ReportPayloadBuilderService payloadBuilderService,
            ReportRenderService renderService,
            ReportStorageService storageService) {

        return new ReportExecutionJobService(
                executionDao,
                templateService,
                validationService,
                payloadBuilderService,
                renderService,
                storageService,
                objectMapper);
    }

    private ReportTemplate template(
            TenantId tenantId,
            CustomerId customerId,
            UUID templateId) {

        ReportTemplate template = new ReportTemplate();
        template.setId(new ReportTemplateId(templateId));
        template.setTenantId(tenantId);
        template.setCustomerId(customerId);
        template.setName("Async report");
        template.setType(ReportType.CUSTOM);
        return template;
    }

    private ReportExecution execution(
            TenantId tenantId,
            CustomerId customerId,
            UUID templateId,
            UUID executionId) {

        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.set("request", objectMapper.valueToTree(request()));

        ReportExecution execution = new ReportExecution();
        execution.setId(new ReportExecutionId(executionId));
        execution.setTenantId(tenantId);
        execution.setCustomerId(customerId);
        execution.setTemplateId(new ReportTemplateId(templateId));
        execution.setStatus(ReportExecutionStatus.RUNNING);
        execution.setExecutionRequest(snapshot);
        return execution;
    }

    private GenerateReportRequest request() {
        GenerateReportRequest request = new GenerateReportRequest();
        request.setStartTs(100L);
        request.setEndTs(200L);
        request.setLocale("es-MX");
        request.setTimezone("America/Monterrey");
        return request;
    }
}
