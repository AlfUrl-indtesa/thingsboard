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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.common.data.report.ReportStorageType;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultReportStorageService
                implements ReportStorageService {

        private static final String STAGING_DIRECTORY_NAME = ".staging";

        private final ReportStorageProperties properties;

        @Override
        public Path createStagingFile(
                        TenantId tenantId,
                        ReportExecution execution) {

                validateTenantAndExecution(
                                tenantId,
                                execution);

                try {
                        Path stagingDirectory = resolveStagingDirectory(
                                        tenantId);

                        Files.createDirectories(
                                        stagingDirectory);

                        return Files.createTempFile(
                                        stagingDirectory,
                                        execution.getId().toString()
                                                        + "-",
                                        ".pdf.part");

                } catch (IOException e) {
                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_STORAGE_FAILED,
                                        "Failed to create report staging file",
                                        e);
                }
        }

        @Override
        public ReportExecution storeGeneratedFile(
                        TenantId tenantId,
                        ReportExecution execution,
                        RenderedReportFile renderedFile,
                        String fileName,
                        String mimeType) {

                validateStoreInputs(
                                tenantId,
                                execution,
                                renderedFile,
                                fileName,
                                mimeType);

                Path sourcePath = renderedFile.path()
                                .toAbsolutePath()
                                .normalize();

                try {
                        Path tenantDirectory = resolveTenantDirectory(
                                        tenantId);

                        Path stagingDirectory = resolveStagingDirectory(
                                        tenantId);

                        Files.createDirectories(
                                        tenantDirectory);

                        Files.createDirectories(
                                        stagingDirectory);

                        validateStagingPath(
                                        stagingDirectory,
                                        sourcePath);

                        long actualSize = Files.size(sourcePath);

                        if (actualSize <= 0) {
                                throw new ReportServiceException(
                                                ReportErrorCode.FILE_STORAGE_FAILED,
                                                "Rendered report staging file is empty");
                        }

                        if (actualSize != renderedFile.size()) {

                                throw new ReportServiceException(
                                                ReportErrorCode.FILE_STORAGE_FAILED,
                                                "Rendered report staging file size changed before storage");
                        }

                        String actualChecksum = calculateSha256(
                                        sourcePath);

                        if (!actualChecksum.equalsIgnoreCase(
                                        renderedFile.checksum())) {

                                throw new ReportServiceException(
                                                ReportErrorCode.FILE_STORAGE_FAILED,
                                                "Rendered report staging file checksum changed before storage");
                        }

                        String safeFileName = buildSafeFileName(
                                        execution,
                                        fileName);

                        Path targetPath = tenantDirectory
                                        .resolve(safeFileName)
                                        .toAbsolutePath()
                                        .normalize();

                        if (!targetPath.startsWith(
                                        tenantDirectory)) {
                                throw new ReportServiceException(
                                                ReportErrorCode.FILE_STORAGE_FAILED,
                                                "Resolved report file path is outside the tenant directory");
                        }

                        moveStagingFile(
                                        sourcePath,
                                        targetPath);

                        execution.setStorageType(
                                        ReportStorageType.LOCAL_FILE_SYSTEM);

                        execution.setFileName(
                                        safeFileName);

                        execution.setMimeType(
                                        mimeType);

                        execution.setFilePath(
                                        targetPath.toString());

                        execution.setFileSize(
                                        actualSize);

                        execution.setChecksum(
                                        actualChecksum);

                        return execution;

                } catch (IOException e) {
                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_STORAGE_FAILED,
                                        "Failed to store generated report file",
                                        e);
                }
        }

        @Override
        public Resource loadFile(
                        TenantId tenantId,
                        ReportExecution execution) {

                validateLoadInputs(
                                tenantId,
                                execution);

                Path filePath = resolveStoredFilePath(
                                tenantId,
                                execution);

                if (!Files.exists(filePath)
                                || !Files.isRegularFile(
                                                filePath,
                                                LinkOption.NOFOLLOW_LINKS)) {
                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_NOT_FOUND,
                                        "Stored report file not found: "
                                                        + execution.getFilePath());
                }

                if (!Files.isReadable(filePath)) {
                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_NOT_FOUND,
                                        "Stored report file is not readable: "
                                                        + execution.getFilePath());
                }

                return new FileSystemResource(
                                filePath);
        }

        @Override
        public void deleteFile(
                        TenantId tenantId,
                        ReportExecution execution) {

                if (tenantId == null
                                || execution == null
                                || execution.getFilePath() == null
                                || execution.getFilePath().isBlank()) {
                        return;
                }

                try {
                        Files.deleteIfExists(
                                        resolveStoredFilePath(
                                                        tenantId,
                                                        execution));

                } catch (IOException e) {
                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_STORAGE_FAILED,
                                        "Failed to delete generated report file: "
                                                        + e.getMessage(),
                                        e);
                }
        }

        @Override
        public boolean exists(
                        TenantId tenantId,
                        ReportExecution execution) {

                if (tenantId == null
                                || execution == null
                                || execution.getFilePath() == null
                                || execution.getFilePath().isBlank()) {
                        return false;
                }

                try {
                        Path filePath = resolveStoredFilePath(
                                        tenantId,
                                        execution);

                        return Files.exists(filePath)
                                        && Files.isRegularFile(
                                                        filePath,
                                                        LinkOption.NOFOLLOW_LINKS);

                } catch (ReportServiceException e) {
                        return false;
                }
        }

        @Override
        public void cleanupStagingFile(
                        TenantId tenantId,
                        Path stagingFile) {

                if (tenantId == null
                                || stagingFile == null) {
                        return;
                }

                try {
                        Path stagingDirectory = resolveStagingDirectory(
                                        tenantId);

                        Path normalizedFile = stagingFile
                                        .toAbsolutePath()
                                        .normalize();

                        validateStagingLocation(
                                        stagingDirectory,
                                        normalizedFile);

                        Files.deleteIfExists(
                                        normalizedFile);

                } catch (Exception e) {
                        log.warn(
                                        "Failed to clean report staging file {}: {}",
                                        stagingFile,
                                        e.getMessage());
                }
        }

        private void moveStagingFile(
                        Path sourcePath,
                        Path targetPath)
                        throws IOException {

                try {
                        Files.move(
                                        sourcePath,
                                        targetPath,
                                        StandardCopyOption.ATOMIC_MOVE);

                } catch (AtomicMoveNotSupportedException e) {
                        Files.move(
                                        sourcePath,
                                        targetPath);
                }
        }

        private void validateStoreInputs(
                        TenantId tenantId,
                        ReportExecution execution,
                        RenderedReportFile renderedFile,
                        String fileName,
                        String mimeType) {

                validateTenantAndExecution(
                                tenantId,
                                execution);

                if (renderedFile == null
                                || renderedFile.path() == null) {

                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_STORAGE_FAILED,
                                        "Rendered report staging file is required");
                }

                if (renderedFile.size() <= 0) {
                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_STORAGE_FAILED,
                                        "Rendered report file size is invalid");
                }

                if (renderedFile.checksum() == null
                                || !renderedFile.checksum()
                                                .matches(
                                                                "[0-9a-fA-F]{64}")) {

                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_STORAGE_FAILED,
                                        "Rendered report checksum is invalid");
                }

                if (fileName == null
                                || fileName.isBlank()) {

                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_STORAGE_FAILED,
                                        "File name is required");
                }

                if (mimeType == null
                                || mimeType.isBlank()) {

                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_STORAGE_FAILED,
                                        "Mime type is required");
                }
        }

        private void validateTenantAndExecution(
                        TenantId tenantId,
                        ReportExecution execution) {

                if (tenantId == null) {
                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_STORAGE_FAILED,
                                        "TenantId is required for file storage");
                }

                if (execution == null
                                || execution.getId() == null) {

                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_STORAGE_FAILED,
                                        "Execution with valid id is required for file storage");
                }
        }

        private void validateLoadInputs(
                        TenantId tenantId,
                        ReportExecution execution) {

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
                                        "Unsupported storage type for file loading: "
                                                        + execution.getStorageType());
                }

                if (execution.getFilePath() == null
                                || execution.getFilePath().isBlank()) {

                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_NOT_FOUND,
                                        "Execution does not contain a valid file path");
                }
        }

        private Path resolveTenantDirectory(
                        TenantId tenantId) {

                return Paths.get(
                                properties.getBaseDir(),
                                tenantId.getId().toString())
                                .toAbsolutePath()
                                .normalize();
        }

        private Path resolveStagingDirectory(
                        TenantId tenantId) {

                return resolveTenantDirectory(
                                tenantId)
                                .resolve(
                                                STAGING_DIRECTORY_NAME)
                                .toAbsolutePath()
                                .normalize();
        }

        private Path resolveStoredFilePath(
                        TenantId tenantId,
                        ReportExecution execution) {

                Path tenantDirectory = resolveTenantDirectory(
                                tenantId);

                Path filePath = Paths.get(
                                execution.getFilePath())
                                .toAbsolutePath()
                                .normalize();

                if (!filePath.startsWith(
                                tenantDirectory)) {
                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_NOT_FOUND,
                                        "Stored report path is outside the tenant directory");
                }

                return filePath;
        }

        private void validateStagingPath(
                        Path stagingDirectory,
                        Path stagingFile) {

                Path normalizedFile = stagingFile
                                .toAbsolutePath()
                                .normalize();

                validateStagingLocation(
                                stagingDirectory,
                                normalizedFile);

                if (!Files.exists(normalizedFile)
                                || !Files.isRegularFile(
                                                normalizedFile,
                                                LinkOption.NOFOLLOW_LINKS)) {
                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_STORAGE_FAILED,
                                        "Report staging file does not exist");
                }
        }

        private void validateStagingLocation(
                        Path stagingDirectory,
                        Path stagingFile) {

                Path normalizedDirectory = stagingDirectory
                                .toAbsolutePath()
                                .normalize();

                Path normalizedFile = stagingFile
                                .toAbsolutePath()
                                .normalize();

                if (!normalizedFile.startsWith(
                                normalizedDirectory)) {
                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_STORAGE_FAILED,
                                        "Report staging path is outside the tenant staging directory");
                }
        }

        private String calculateSha256(
                        Path filePath) {

                try {
                        MessageDigest digest = MessageDigest.getInstance(
                                        "SHA-256");

                        try (InputStream inputStream = Files.newInputStream(
                                        filePath)) {

                                byte[] buffer = new byte[16 * 1024];
                                int read;

                                while ((read = inputStream.read(buffer)) != -1) {
                                        digest.update(
                                                        buffer,
                                                        0,
                                                        read);
                                }
                        }

                        return HexFormat.of().formatHex(
                                        digest.digest());

                } catch (IOException | NoSuchAlgorithmException e) {
                        throw new ReportServiceException(
                                        ReportErrorCode.FILE_STORAGE_FAILED,
                                        "Failed to verify generated report checksum",
                                        e);
                }
        }

        private String buildSafeFileName(
                        ReportExecution execution,
                        String originalFileName) {

                String sanitized = originalFileName.replaceAll(
                                "[^a-zA-Z0-9._-]",
                                "_");

                String prefix = execution.getId() != null
                                ? execution.getId().toString()
                                : UUID.randomUUID().toString();

                return prefix
                                + "_"
                                + sanitized;
        }
}
