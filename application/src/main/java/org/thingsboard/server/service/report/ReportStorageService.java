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
