/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import org.springframework.core.io.Resource;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportExecution;

import java.nio.file.Path;

public interface ReportStorageService {

        Path createStagingFile(
                        TenantId tenantId,
                        ReportExecution execution);

        ReportExecution storeGeneratedFile(
                        TenantId tenantId,
                        ReportExecution execution,
                        RenderedReportFile renderedFile,
                        String fileName,
                        String mimeType);

        /**
         * Returns a filesystem-backed resource without loading the
         * complete PDF into the JVM heap.
         */
        Resource loadFile(
                        TenantId tenantId,
                        ReportExecution execution);

        boolean exists(
                        TenantId tenantId,
                        ReportExecution execution);

        void deleteFile(
                        TenantId tenantId,
                        ReportExecution execution);

        void cleanupStagingFile(
                        TenantId tenantId,
                        Path stagingFile);
}