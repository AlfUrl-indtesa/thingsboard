package org.thingsboard.server.dao.model.sql;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.thingsboard.server.common.data.report.*;
import org.thingsboard.server.dao.model.BaseSqlEntity;

import jakarta.persistence.*;
import java.util.UUID;

@Data
@Entity
@Table(name = "report_execution")
@EqualsAndHashCode(callSuper = true)
public class ReportExecutionEntity extends BaseSqlEntity<ReportExecution> {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "template_name_snapshot", nullable = false, length = 255)
    private String templateNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 50)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ReportExecutionStatus status;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "requested_time")
    private Long requestedTime;

    @Column(name = "started_time")
    private Long startedTime;

    @Column(name = "finished_time")
    private Long finishedTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_request", columnDefinition = "jsonb")
    private JsonNode executionRequest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_snapshot", columnDefinition = "jsonb")
    private JsonNode payloadSnapshot;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", length = 50)
    private ReportStorageType storageType;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "external_file_id", length = 255)
    private String externalFileId;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "checksum", length = 255)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 100)
    private ReportErrorCode errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_metadata", columnDefinition = "jsonb")
    private JsonNode executionMetadata;

    public ReportExecutionEntity() {
        super();
    }

    public ReportExecutionEntity(ReportExecution reportExecution) {
        if (reportExecution.getId() != null) {
            this.setId(reportExecution.getId());
        }
        this.setCreatedTime(reportExecution.getCreatedTime());
        this.tenantId = reportExecution.getTenantId() != null ? reportExecution.getTenantId().getId() : null;
        this.customerId = reportExecution.getCustomerId() != null ? reportExecution.getCustomerId().getId() : null;
        this.templateId = reportExecution.getTemplateId();
        this.templateNameSnapshot = reportExecution.getTemplateNameSnapshot();
        this.reportType = reportExecution.getReportType();
        this.status = reportExecution.getStatus();
        this.requestedBy = reportExecution.getRequestedBy();
        this.requestedTime = reportExecution.getRequestedTime();
        this.startedTime = reportExecution.getStartedTime();
        this.finishedTime = reportExecution.getFinishedTime();
        this.executionRequest = reportExecution.getExecutionRequest();
        this.payloadSnapshot = reportExecution.getPayloadSnapshot();
        this.fileName = reportExecution.getFileName();
        this.mimeType = reportExecution.getMimeType();
        this.storageType = reportExecution.getStorageType();
        this.filePath = reportExecution.getFilePath();
        this.externalFileId = reportExecution.getExternalFileId();
        this.fileSize = reportExecution.getFileSize();
        this.checksum = reportExecution.getChecksum();
        this.errorCode = reportExecution.getErrorCode();
        this.errorMessage = reportExecution.getErrorMessage();
        this.executionMetadata = reportExecution.getExecutionMetadata();
    }

    @Override
    public ReportExecution toData() {
        ReportExecution reportExecution = new ReportExecution();
        reportExecution.setId(this.getUuidId());
        reportExecution.setCreatedTime(this.getCreatedTime());
        reportExecution.setTenantId(new org.thingsboard.server.common.data.id.TenantId(tenantId));
        if (customerId != null) {
            reportExecution.setCustomerId(new org.thingsboard.server.common.data.id.CustomerId(customerId));
        }
        reportExecution.setTemplateId(templateId);
        reportExecution.setTemplateNameSnapshot(templateNameSnapshot);
        reportExecution.setReportType(reportType);
        reportExecution.setStatus(status);
        reportExecution.setRequestedBy(requestedBy);
        reportExecution.setRequestedTime(requestedTime);
        reportExecution.setStartedTime(startedTime);
        reportExecution.setFinishedTime(finishedTime);
        reportExecution.setExecutionRequest(executionRequest);
        reportExecution.setPayloadSnapshot(payloadSnapshot);
        reportExecution.setFileName(fileName);
        reportExecution.setMimeType(mimeType);
        reportExecution.setStorageType(storageType);
        reportExecution.setFilePath(filePath);
        reportExecution.setExternalFileId(externalFileId);
        reportExecution.setFileSize(fileSize);
        reportExecution.setChecksum(checksum);
        reportExecution.setErrorCode(errorCode);
        reportExecution.setErrorMessage(errorMessage);
        reportExecution.setExecutionMetadata(executionMetadata);
        return reportExecution;
    }
}