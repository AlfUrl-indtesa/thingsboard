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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.Resource;
import org.thingsboard.server.common.data.id.ReportExecutionId;
import org.thingsboard.server.common.data.id.ReportTemplateId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportEntityFilter;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.common.data.report.ReportGenerationOptions;
import org.thingsboard.server.common.data.report.ReportScopeType;
import org.thingsboard.server.common.data.report.ReportStorageType;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.report.ReportType;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportGenerationBoundaryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsStableExecutionRequest() {
        ReportTemplate template = validTemplate();
        GenerateReportRequest request = validRequest();

        JsonNode result = new DefaultReportRequestBuilderService(
                objectMapper).buildExecutionRequest(
                        template,
                        request);

        assertEquals(
                template.getId().toString(),
                result.path("templateId").asText());

        assertEquals(
                "Boundary test",
                result.path("templateName").asText());

        assertEquals(
                "CUSTOM",
                result.path("reportType").asText());

        assertEquals(
                100L,
                result.path("startTs").asLong());

        assertEquals(
                200L,
                result.path("endTs").asLong());

        assertEquals(
                "es-MX",
                result.path("locale").asText());

        assertEquals(
                "America/Monterrey",
                result.path("timezone").asText());

        assertTrue(result.has("request"));
        assertTrue(result.has("scope"));
        assertTrue(result.has("generationOptions"));
    }

    @Test
    void rejectsIncompleteExecutionRequest() {
        DefaultReportRequestBuilderService service =
                new DefaultReportRequestBuilderService(
                        objectMapper);

        ReportServiceException nullInput = assertThrows(
                ReportServiceException.class,
                () -> service.buildExecutionRequest(
                        null,
                        validRequest()));

        assertEquals(
                ReportErrorCode.PAYLOAD_BUILD_FAILED,
                nullInput.getErrorCode());

        ReportTemplate template = validTemplate();
        template.setId(null);

        ReportServiceException missingId = assertThrows(
                ReportServiceException.class,
                () -> service.buildExecutionRequest(
                        template,
                        validRequest()));

        assertEquals(
                ReportErrorCode.PAYLOAD_BUILD_FAILED,
                missingId.getErrorCode());

        GenerateReportRequest invalidRange = validRequest();
        invalidRange.setEndTs(100L);

        ReportServiceException rangeError = assertThrows(
                ReportServiceException.class,
                () -> service.buildExecutionRequest(
                        validTemplate(),
                        invalidRange));

        assertEquals(
                ReportErrorCode.PAYLOAD_BUILD_FAILED,
                rangeError.getErrorCode());
    }

    @Test
    void storesLoadsAndDeletesPdf() throws Exception {
        ReportStorageProperties properties =
                storageProperties();

        DefaultReportStorageService service =
                new DefaultReportStorageService(
                        properties);

        TenantId tenantId = new TenantId(
                UUID.randomUUID());

        ReportExecution execution = execution();

        Path stagingFile = service.createStagingFile(
                tenantId,
                execution);

        byte[] pdf = "%PDF-1.7\nboundary\n%%EOF\n".getBytes(
                StandardCharsets.UTF_8);

        Files.write(
                stagingFile,
                pdf);

        String checksum = checksum(pdf);

        ReportExecution stored = service.storeGeneratedFile(
                tenantId,
                execution,
                new RenderedReportFile(
                        stagingFile,
                        pdf.length,
                        checksum,
                        "request-1"),
                "../../Quarter 1?.pdf",
                "application/pdf");

        assertEquals(
                ReportStorageType.LOCAL_FILE_SYSTEM,
                stored.getStorageType());

        assertEquals(
                Long.valueOf(pdf.length),
                stored.getFileSize());

        assertEquals(
                checksum,
                stored.getChecksum());

        assertFalse(stored.getFileName().contains("/"));
        assertFalse(stored.getFileName().contains("\\"));

        assertTrue(service.exists(
                tenantId,
                stored));

        Resource resource = service.loadFile(
                tenantId,
                stored);

        assertTrue(resource.exists());
        assertEquals(
                pdf.length,
                resource.contentLength());

        service.deleteFile(
                tenantId,
                stored);

        assertFalse(service.exists(
                tenantId,
                stored));
    }

    @Test
    void rejectsChangedStagingChecksum() throws Exception {
        DefaultReportStorageService service =
                new DefaultReportStorageService(
                        storageProperties());

        TenantId tenantId = new TenantId(
                UUID.randomUUID());

        ReportExecution execution = execution();

        Path stagingFile = service.createStagingFile(
                tenantId,
                execution);

        byte[] pdf = "%PDF-1.7\nchanged\n%%EOF\n".getBytes(
                StandardCharsets.UTF_8);

        Files.write(
                stagingFile,
                pdf);

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.storeGeneratedFile(
                        tenantId,
                        execution,
                        new RenderedReportFile(
                                stagingFile,
                                pdf.length,
                                "0".repeat(64),
                                "request-2"),
                        "changed.pdf",
                        "application/pdf"));

        assertEquals(
                ReportErrorCode.FILE_STORAGE_FAILED,
                exception.getErrorCode());

        assertTrue(Files.exists(stagingFile));
    }

    @Test
    void rejectsStoredPathOutsideTenantDirectory() {
        DefaultReportStorageService service =
                new DefaultReportStorageService(
                        storageProperties());

        TenantId tenantId = new TenantId(
                UUID.randomUUID());

        ReportExecution execution = execution();
        execution.setStorageType(
                ReportStorageType.LOCAL_FILE_SYSTEM);

        execution.setFilePath(
                temporaryDirectory
                        .resolve("outside.pdf")
                        .toString());

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.loadFile(
                        tenantId,
                        execution));

        assertEquals(
                ReportErrorCode.FILE_NOT_FOUND,
                exception.getErrorCode());
    }

    @Test
    void streamsPdfFromTemporaryRenderer() throws Exception {
        byte[] pdf = "%PDF-1.7\nrenderer\n%%EOF\n".getBytes(
                StandardCharsets.UTF_8);

        RenderedReportFile rendered = renderResponse(
                pdf,
                "application/pdf");

        assertNotNull(rendered);
        assertEquals(
                pdf.length,
                rendered.size());

        assertEquals(
                checksum(pdf),
                rendered.checksum());

        assertTrue(
                rendered.requestId() != null
                        && !rendered.requestId().isBlank());

        assertEquals(
                "%PDF-",
                Files.readString(
                        rendered.path(),
                        StandardCharsets.UTF_8)
                        .substring(0, 5));
    }

    @Test
    void rejectsInvalidRendererContent() {
        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> renderResponse(
                        "not-a-pdf".getBytes(
                                StandardCharsets.UTF_8),
                        "text/plain"));

        assertEquals(
                ReportErrorCode.PDF_RENDER_FAILED,
                exception.getErrorCode());
    }

    @Test
    void rejectsNonHttpRendererUrl() throws Exception {
        ReportRenderProperties properties =
                renderProperties();

        properties.setBaseUrl("file:///tmp");

        RemoteReportRenderService service =
                new RemoteReportRenderService(
                        properties,
                        new RestTemplateBuilder(),
                        objectMapper);

        Path target = Files.createFile(
                temporaryDirectory.resolve(
                        "invalid-url.pdf.part"));

        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> service.renderPdf(
                        objectMapper.createObjectNode(),
                        target));

        assertEquals(
                ReportErrorCode.PDF_RENDER_FAILED,
                exception.getErrorCode());
    }

    private RenderedReportFile renderResponse(
            byte[] responseBody,
            String contentType) throws Exception {

        HttpServer server = HttpServer.create(
                new InetSocketAddress(
                        "127.0.0.1",
                        0),
                0);

        server.createContext(
                "/render-report",
                exchange -> {
                    try {
                        exchange.getRequestBody().readAllBytes();

                        exchange.getResponseHeaders().set(
                                "Content-Type",
                                contentType);

                        exchange.sendResponseHeaders(
                                200,
                                responseBody.length);

                        try (OutputStream output =
                                     exchange.getResponseBody()) {
                            output.write(responseBody);
                        }
                    } finally {
                        exchange.close();
                    }
                });

        server.start();

        try {
            ReportRenderProperties properties =
                    renderProperties();

            properties.setBaseUrl(
                    "http://127.0.0.1:"
                            + server.getAddress().getPort());

            RemoteReportRenderService service =
                    new RemoteReportRenderService(
                            properties,
                            new RestTemplateBuilder(),
                            objectMapper);

            Path target = Files.createFile(
                    temporaryDirectory.resolve(
                            UUID.randomUUID()
                                    + ".pdf.part"));

            return service.renderPdf(
                    objectMapper.createObjectNode()
                            .put("test", true),
                    target);

        } finally {
            server.stop(0);
        }
    }

    private ReportStorageProperties storageProperties() {
        ReportStorageProperties properties =
                new ReportStorageProperties();

        properties.setBaseDir(
                temporaryDirectory
                        .resolve("reports")
                        .toString());

        return properties;
    }

    private ReportRenderProperties renderProperties() {
        ReportRenderProperties properties =
                new ReportRenderProperties();

        properties.setMaxAttempts(1);
        properties.setConnectTimeoutMs(3000);
        properties.setReadTimeoutMs(5000);
        properties.setMaxPdfSizeMb(1);

        return properties;
    }

    private ReportExecution execution() {
        ReportExecution execution =
                new ReportExecution();

        execution.setId(
                new ReportExecutionId(
                        UUID.randomUUID()));

        return execution;
    }

    private ReportTemplate validTemplate() {
        ReportEntityFilter filter =
                new ReportEntityFilter();

        filter.setScopeType(
                ReportScopeType.TENANT_ENTITIES);

        filter.setEntityType("DEVICE");

        ReportTemplate template =
                new ReportTemplate();

        template.setId(
                new ReportTemplateId(
                        UUID.randomUUID()));

        template.setName("Boundary test");
        template.setType(ReportType.CUSTOM);
        template.setScopeType(
                ReportScopeType.TENANT_ENTITIES);

        template.setEntityFilter(filter);
        template.setGenerationOptions(
                new ReportGenerationOptions());

        return template;
    }

    private GenerateReportRequest validRequest() {
        GenerateReportRequest request =
                new GenerateReportRequest();

        request.setStartTs(100L);
        request.setEndTs(200L);
        request.setLocale("es-MX");
        request.setTimezone("America/Monterrey");

        return request;
    }

    private String checksum(byte[] content)
            throws Exception {

        return HexFormat.of().formatHex(
                MessageDigest
                        .getInstance("SHA-256")
                        .digest(content));
    }
}
