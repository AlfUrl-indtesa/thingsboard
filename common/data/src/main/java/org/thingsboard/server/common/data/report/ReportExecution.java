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
package org.thingsboard.server.common.data.report;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;

import java.util.UUID;
import org.thingsboard.server.common.data.id.ReportExecutionId;
import org.thingsboard.server.common.data.id.ReportTemplateId;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReportExecution extends BaseData<ReportExecutionId> {
    private TenantId tenantId;

    private CustomerId customerId;
    private ReportTemplateId templateId;
    private String templateNameSnapshot;
    private ReportType reportType;
    private ReportExecutionStatus status = ReportExecutionStatus.PENDING;

    private UUID requestedBy;

    private Long requestedTime;

    private Long startedTime;

    private Long finishedTime;

    private JsonNode executionRequest;

    private JsonNode payloadSnapshot;

    private String fileName;

    private String mimeType;

    private ReportStorageType storageType;

    private String filePath;

    private String externalFileId;

    private Long fileSize;

    private String checksum;

    private ReportErrorCode errorCode;

    private String errorMessage;

    private JsonNode executionMetadata;
}
