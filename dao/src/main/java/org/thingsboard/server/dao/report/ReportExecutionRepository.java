/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.dao.report;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportExecutionStatus;
import org.thingsboard.server.dao.model.sql.ReportExecutionEntity;

import java.util.UUID;

public interface ReportExecutionRepository
                extends JpaRepository<ReportExecutionEntity, UUID> {

        Page<ReportExecutionEntity> findByTenantId(
                        UUID tenantId,
                        Pageable pageable);

        Page<ReportExecutionEntity> findByTenantIdAndTemplateId(
                        UUID tenantId,
                        UUID templateId,
                        Pageable pageable);

        Page<ReportExecutionEntity> findByTenantIdAndStatus(
                        UUID tenantId,
                        ReportExecutionStatus status,
                        Pageable pageable);

        Page<ReportExecutionEntity> findByTenantIdAndCustomerId(
                        UUID tenantId,
                        UUID customerId,
                        Pageable pageable);

        Page<ReportExecutionEntity> findByTenantIdAndCustomerIdAndRequestedBy(
                        UUID tenantId,
                        UUID customerId,
                        UUID requestedBy,
                        Pageable pageable);

        Page<ReportExecutionEntity> findByTenantIdAndTemplateIdAndCustomerIdAndRequestedBy(
                        UUID tenantId,
                        UUID templateId,
                        UUID customerId,
                        UUID requestedBy,
                        Pageable pageable);

        Page<ReportExecutionEntity> findByStatusOrderByRequestedTimeAsc(
                        ReportExecutionStatus status,
                        Pageable pageable);

        /**
         * Atomic compare-and-set transition. It prevents two
         * ThingsBoard nodes from running the same pending job.
         */
        @Transactional
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("""
                        update ReportExecutionEntity e
                           set e.status = :runningStatus,
                               e.startedTime = :startedTime,
                               e.finishedTime = null,
                               e.errorCode = null,
                               e.errorMessage = null
                         where e.id = :executionId
                           and e.tenantId = :tenantId
                           and e.status = :pendingStatus
                        """)
        int markRunningIfPending(
                        @Param("tenantId") UUID tenantId,

                        @Param("executionId") UUID executionId,

                        @Param("pendingStatus") ReportExecutionStatus pendingStatus,

                        @Param("runningStatus") ReportExecutionStatus runningStatus,

                        @Param("startedTime") long startedTime);

        /**
         * Returns abandoned RUNNING jobs to the persistent queue.
         */
        @Transactional
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("""
                        update ReportExecutionEntity e
                           set e.status = :pendingStatus,
                               e.startedTime = null,
                               e.finishedTime = null,
                               e.errorCode = null,
                               e.errorMessage = null
                         where e.status = :runningStatus
                           and e.startedTime is not null
                           and e.startedTime < :cutoffTime
                        """)
        int resetStaleRunning(
                        @Param("runningStatus") ReportExecutionStatus runningStatus,

                        @Param("pendingStatus") ReportExecutionStatus pendingStatus,

                        @Param("cutoffTime") long cutoffTime);
}