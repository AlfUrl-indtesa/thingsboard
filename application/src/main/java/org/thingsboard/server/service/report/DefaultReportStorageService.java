package org.thingsboard.server.service.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.common.data.report.ReportStorageType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultReportStorageService implements ReportStorageService {

    private final ReportStorageProperties properties;

    @Override
    public ReportExecution storeGeneratedFile(TenantId tenantId,
            ReportExecution execution,
            byte[] content,
            String fileName,
            String mimeType) {
        validateStoreInputs(tenantId, execution, content, fileName, mimeType);

        try {
            Path tenantDir = resolveTenantDirectory(tenantId);
            Files.createDirectories(tenantDir);

            String safeFileName = buildSafeFileName(execution, fileName);
            Path filePath = tenantDir.resolve(safeFileName);

            Files.write(filePath, content);

            execution.setStorageType(ReportStorageType.LOCAL_FILE_SYSTEM);
            execution.setFileName(safeFileName);
            execution.setMimeType(mimeType);
            execution.setFilePath(filePath.toAbsolutePath().toString());
            execution.setFileSize((long) content.length);
            execution.setChecksum(calculateSha256(content));

            return execution;
        } catch (IOException e) {
            throw new ReportServiceException(
                    ReportErrorCode.FILE_STORAGE_FAILED,
                    "Failed to store generated report file",
                    e);
        }
    }

    @Override
    public byte[] loadFile(TenantId tenantId, ReportExecution execution) {
        validateLoadInputs(tenantId, execution);

        try {
            Path filePath = Paths.get(execution.getFilePath());
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                throw new ReportServiceException(
                        ReportErrorCode.FILE_NOT_FOUND,
                        "Stored report file not found: " + execution.getFilePath());
            }
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new ReportServiceException(
                    ReportErrorCode.FILE_NOT_FOUND,
                    "Failed to read stored report file",
                    e);
        }
    }

    @Override
    public void deleteFile(TenantId tenantId, ReportExecution execution) {
        if (tenantId == null || execution == null) {
            return;
        }

        if (execution.getFilePath() == null || execution.getFilePath().isBlank()) {
            return;
        }

        try {
            Path filePath = Paths.get(execution.getFilePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new ReportServiceException(
                    ReportErrorCode.FILE_STORAGE_FAILED,
                    "Failed to delete generated report file: " + e.getMessage(),
                    e);
        }
    }

    @Override
    public boolean exists(TenantId tenantId, ReportExecution execution) {
        if (tenantId == null || execution == null || execution.getFilePath() == null
                || execution.getFilePath().isBlank()) {
            return false;
        }
        Path filePath = Paths.get(execution.getFilePath());
        return Files.exists(filePath) && Files.isRegularFile(filePath);
    }

    private void validateStoreInputs(TenantId tenantId,
            ReportExecution execution,
            byte[] content,
            String fileName,
            String mimeType) {
        if (tenantId == null) {
            throw new ReportServiceException(
                    ReportErrorCode.FILE_STORAGE_FAILED,
                    "TenantId is required for file storage");
        }
        if (execution == null || execution.getId() == null) {
            throw new ReportServiceException(
                    ReportErrorCode.FILE_STORAGE_FAILED,
                    "Execution with valid id is required for file storage");
        }
        if (content == null || content.length == 0) {
            throw new ReportServiceException(
                    ReportErrorCode.FILE_STORAGE_FAILED,
                    "Generated file content is empty");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new ReportServiceException(
                    ReportErrorCode.FILE_STORAGE_FAILED,
                    "File name is required");
        }
        if (mimeType == null || mimeType.isBlank()) {
            throw new ReportServiceException(
                    ReportErrorCode.FILE_STORAGE_FAILED,
                    "Mime type is required");
        }
    }

    private void validateLoadInputs(TenantId tenantId, ReportExecution execution) {
        if (tenantId == null) {
            throw new ReportServiceException(
                    ReportErrorCode.FILE_NOT_FOUND,
                    "TenantId is required for file loading");
        }
        if (execution == null) {
            throw new ReportServiceException(
                    ReportErrorCode.FILE_NOT_FOUND,
                    "Execution is required for file loading");
        }
        if (execution.getStorageType() != ReportStorageType.LOCAL_FILE_SYSTEM) {
            throw new ReportServiceException(
                    ReportErrorCode.FILE_NOT_FOUND,
                    "Unsupported storage type for file loading: " + execution.getStorageType());
        }
        if (execution.getFilePath() == null || execution.getFilePath().isBlank()) {
            throw new ReportServiceException(
                    ReportErrorCode.FILE_NOT_FOUND,
                    "Execution does not contain a valid file path");
        }
    }

    private Path resolveTenantDirectory(TenantId tenantId) {
        return Paths.get(properties.getBaseDir(), tenantId.getId().toString());
    }

    private String buildSafeFileName(ReportExecution execution, String originalFileName) {
        String sanitized = originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String prefix = execution.getId() != null ? execution.getId().toString() : UUID.randomUUID().toString();
        return prefix + "_" + sanitized;
    }

    private String calculateSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new ReportServiceException(
                    ReportErrorCode.FILE_STORAGE_FAILED,
                    "SHA-256 algorithm not available",
                    e);
        }
    }
}