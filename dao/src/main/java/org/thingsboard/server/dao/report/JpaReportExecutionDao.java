/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.dao.report;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.common.data.report.ReportExecutionStatus;
import org.thingsboard.server.dao.model.sql.ReportExecutionEntity;

import java.util.Optional;
import java.util.UUID;
import org.thingsboard.server.common.data.id.CustomerId;

@Component
@RequiredArgsConstructor
public class JpaReportExecutionDao
                implements ReportExecutionDao {

        private final ReportExecutionRepository repository;

        @Override
        public ReportExecution save(
                        TenantId tenantId,
                        ReportExecution reportExecution) {

                ReportExecutionEntity entity = new ReportExecutionEntity(
                                reportExecution);

                return repository
                                .save(entity)
                                .toData();
        }

        @Override
        public Optional<ReportExecution> findById(
                        TenantId tenantId,
                        UUID id) {

                return repository
                                .findById(id)
                                .filter(entity -> entity.getTenantId()
                                                .equals(
                                                                tenantId.getId()))
                                .map(
                                                ReportExecutionEntity::toData);
        }

        @Override
        public Page<ReportExecution> findByTenantId(
                        TenantId tenantId,
                        Pageable pageable) {

                return repository
                                .findByTenantId(
                                                tenantId.getId(),
                                                pageable)
                                .map(
                                                ReportExecutionEntity::toData);
        }

        @Override
        public Page<ReportExecution> findByTenantIdAndTemplateId(
                        TenantId tenantId,
                        UUID templateId,
                        Pageable pageable) {

                return repository
                                .findByTenantIdAndTemplateId(
                                                tenantId.getId(),
                                                templateId,
                                                pageable)
                                .map(
                                                ReportExecutionEntity::toData);
        }

        @Override
        public Page<ReportExecution> findByTenantIdAndCustomerIdAndRequestedBy(
                        TenantId tenantId,
                        CustomerId customerId,
                        UUID requestedBy,
                        Pageable pageable) {

                return repository
                                .findByTenantIdAndCustomerIdAndRequestedBy(
                                                tenantId.getId(),
                                                customerId.getId(),
                                                requestedBy,
                                                pageable)
                                .map(
                                                ReportExecutionEntity::toData);
        }

        @Override
        public Page<ReportExecution> findByTenantIdAndTemplateIdAndCustomerIdAndRequestedBy(
                        TenantId tenantId,
                        UUID templateId,
                        CustomerId customerId,
                        UUID requestedBy,
                        Pageable pageable) {

                return repository
                                .findByTenantIdAndTemplateIdAndCustomerIdAndRequestedBy(
                                                tenantId.getId(),
                                                templateId,
                                                customerId.getId(),
                                                requestedBy,
                                                pageable)
                                .map(
                                                ReportExecutionEntity::toData);
        }

        @Override
        public Page<ReportExecution> findByStatus(
                        ReportExecutionStatus status,
                        Pageable pageable) {

                return repository
                                .findByStatusOrderByRequestedTimeAsc(
                                                status,
                                                pageable)
                                .map(
                                                ReportExecutionEntity::toData);
        }

        @Override
        public boolean markRunningIfPending(
                        TenantId tenantId,
                        UUID executionId,
                        long startedTime) {

                return repository.markRunningIfPending(
                                tenantId.getId(),
                                executionId,
                                ReportExecutionStatus.PENDING,
                                ReportExecutionStatus.RUNNING,
                                startedTime) == 1;
        }

        @Override
        public int resetStaleRunning(
                        long cutoffTime) {

                return repository.resetStaleRunning(
                                ReportExecutionStatus.RUNNING,
                                ReportExecutionStatus.PENDING,
                                cutoffTime);
        }

        @Override
        public void removeById(
                        TenantId tenantId,
                        UUID id) {

                repository
                                .findById(id)
                                .filter(entity -> entity.getTenantId()
                                                .equals(
                                                                tenantId.getId()))
                                .ifPresent(
                                                repository::delete);
        }
}