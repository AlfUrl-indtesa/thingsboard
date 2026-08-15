/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.common.data.report.ReportExecutionStatus;
import org.thingsboard.server.dao.report.ReportExecutionDao;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ReportExecutionDispatcher {

    private final ThreadPoolTaskExecutor reportTaskExecutor;
    private final ReportExecutionJobService reportExecutionJobService;
    private final ReportExecutionDao reportExecutionDao;
    private final ReportAsyncProperties properties;

    /**
     * Prevents the same PENDING record from being inserted
     * repeatedly into this node's executor queue.
     */
    private final Set<UUID> scheduledExecutionIds =
            ConcurrentHashMap.newKeySet();

    public ReportExecutionDispatcher(
            @Qualifier("reportTaskExecutor")
            ThreadPoolTaskExecutor reportTaskExecutor,
            ReportExecutionJobService reportExecutionJobService,
            ReportExecutionDao reportExecutionDao,
            ReportAsyncProperties properties) {

        this.reportTaskExecutor =
                reportTaskExecutor;

        this.reportExecutionJobService =
                reportExecutionJobService;

        this.reportExecutionDao =
                reportExecutionDao;

        this.properties =
                properties;
    }

    public void submit(
            TenantId tenantId,
            UUID executionId) {

        if (!properties.isEnabled()) {
            reportExecutionJobService.process(
                    tenantId,
                    executionId
            );

            return;
        }

        if (!scheduledExecutionIds.add(
                executionId
        )) {
            return;
        }

        try {
            reportTaskExecutor.execute(() -> {
                try {
                    reportExecutionJobService.process(
                            tenantId,
                            executionId
                    );

                } finally {
                    scheduledExecutionIds.remove(
                            executionId
                    );
                }
            });

        } catch (TaskRejectedException e) {
            scheduledExecutionIds.remove(
                    executionId
            );

            /*
             * The database record remains PENDING and will be
             * submitted again by the recovery scan.
             */
            log.warn(
                    "[report-async] executor queue is full; executionId={} remains PENDING",
                    executionId
            );
        }
    }

    @Scheduled(
            initialDelayString =
                    "${report.async.recovery-initial-delay-ms:10000}",
            fixedDelayString =
                    "${report.async.recovery-interval-ms:5000}"
    )
    public void recoverAndDispatch() {

        if (!properties.isEnabled()
                || !properties.isRecoveryEnabled()) {
            return;
        }

        resetStaleRunningJobs();
        dispatchPendingJobs();
    }

    private void resetStaleRunningJobs() {
        long timeoutMs =
                Math.max(
                        1,
                        properties
                                .getStaleRunningTimeoutMinutes()
                )
                        * 60_000L;

        long cutoffTime =
                System.currentTimeMillis()
                        - timeoutMs;

        int recovered =
                reportExecutionDao.resetStaleRunning(
                        cutoffTime
                );

        if (recovered > 0) {
            log.warn(
                    "[report-async] recovered {} stale RUNNING report job(s)",
                    recovered
            );
        }
    }

    private void dispatchPendingJobs() {
        int batchSize =
                Math.max(
                        1,
                        properties.getRecoveryBatchSize()
                );

        Page<ReportExecution> pending =
                reportExecutionDao.findByStatus(
                        ReportExecutionStatus.PENDING,
                        PageRequest.of(
                                0,
                                batchSize
                        )
                );

        for (ReportExecution execution
                : pending.getContent()) {

            if (execution.getTenantId() == null
                    || execution.getId() == null) {
                continue;
            }

            submit(
                    execution.getTenantId(),
                    execution.getId().getId()
            );
        }
    }
}