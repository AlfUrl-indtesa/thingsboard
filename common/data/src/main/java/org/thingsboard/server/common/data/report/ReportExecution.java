package org.thingsboard.server.common.data.report;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;

import javax.validation.constraints.NotNull;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReportExecution extends BaseData<UUID> {

    @NotNull
    private TenantId tenantId;

    private CustomerId customerId;

    @NotNull
    private UUID templateId;

    @NotNull
    private String templateNameSnapshot;

    @NotNull
    private ReportType reportType;

    @NotNull
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