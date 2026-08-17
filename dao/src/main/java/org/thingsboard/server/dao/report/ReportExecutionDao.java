/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.dao.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.common.data.report.ReportExecutionStatus;

import java.util.Optional;
import java.util.UUID;
import org.thingsboard.server.common.data.id.CustomerId;

public interface ReportExecutionDao {

        ReportExecution save(
                        TenantId tenantId,
                        ReportExecution reportExecution);

        Optional<ReportExecution> findById(
                        TenantId tenantId,
                        UUID id);

        Page<ReportExecution> findByTenantId(
                        TenantId tenantId,
                        Pageable pageable);

        Page<ReportExecution> findByTenantIdAndTemplateId(
                        TenantId tenantId,
                        UUID templateId,
                        Pageable pageable);

        Page<ReportExecution> findByTenantIdAndCustomerIdAndRequestedBy(
                        TenantId tenantId,
                        CustomerId customerId,
                        UUID requestedBy,
                        Pageable pageable);

        Page<ReportExecution> findByTenantIdAndTemplateIdAndCustomerIdAndRequestedBy(
                        TenantId tenantId,
                        UUID templateId,
                        CustomerId customerId,
                        UUID requestedBy,
                        Pageable pageable);

        Page<ReportExecution> findByStatus(
                        ReportExecutionStatus status,
                        Pageable pageable);

        boolean markRunningIfPending(
                        TenantId tenantId,
                        UUID executionId,
                        long startedTime);

        int resetStaleRunning(
                        long cutoffTime);

        void removeById(
                        TenantId tenantId,
                        UUID id);
}