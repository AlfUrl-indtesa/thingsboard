/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class RemoteReportRenderService
                implements ReportRenderService {

        private static final int MAX_ERROR_BODY_BYTES = 16 * 1024;

        private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

        private static final int COPY_BUFFER_SIZE = 64 * 1024;

        private static final byte[] PDF_SIGNATURE = new byte[] {
                        '%',
                        'P',
                        'D',
                        'F',
                        '-'
        };

        private final ReportRenderProperties properties;
        private final ObjectMapper objectMapper;
        private final RestTemplate restTemplate;

        public RemoteReportRenderService(
                        ReportRenderProperties properties,
                        RestTemplateBuilder restTemplateBuilder,
                        ObjectMapper objectMapper) {

                this.properties = properties;
                this.objectMapper = objectMapper;

                /*
                 * RestTemplate se construye una sola vez y se reutiliza
                 * entre solicitudes concurrentes.
                 */
                this.restTemplate = restTemplateBuilder
                                .setConnectTimeout(
                                                Duration.ofMillis(
                                                                Math.max(
                                                                                1,
                                                                                properties.getConnectTimeoutMs())))
                                .setReadTimeout(
                                                Duration.ofMillis(
                                                                Math.max(
                                                                                1,
                                                                                properties.getReadTimeoutMs())))
                                .build();

                /*
                 * Los estados HTTP se procesan manualmente para
                 * identificar 429, 503 y Retry-After.
                 */
                this.restTemplate.setErrorHandler(
                                new ResponseErrorHandler() {

                                        @Override
                                        public boolean hasError(
                                                        ClientHttpResponse response) {

                                                return false;
                                        }

                                        @Override
                                        public void handleError(
                                                        ClientHttpResponse response) {

                                                // No-op.
                                        }
                                });
        }

        @Override
        public RenderedReportFile renderPdf(
                        JsonNode payload,
                        Path targetFile) {

                validateRenderInputs(
                                payload,
                                targetFile);

                String url = buildRenderUrl();

                String requestId = UUID.randomUUID()
                                .toString();

                int maxAttempts = clamp(
                                properties.getMaxAttempts(),
                                1,
                                10);

                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                        try {
                                RenderHttpResponse response = executeRenderRequest(
                                                url,
                                                payload,
                                                requestId,
                                                targetFile);

                                if (response == null) {
                                        throw new ReportServiceException(
                                                        ReportErrorCode.PDF_RENDER_FAILED,
                                                        "Render service returned no HTTP response");
                                }

                                if (isSuccessful(
                                                response.statusCode())) {
                                        RenderedReportFile renderedFile = response.renderedFile();

                                        if (renderedFile == null) {
                                                throw new ReportServiceException(
                                                                ReportErrorCode.PDF_RENDER_FAILED,
                                                                "Render service returned no staged PDF file");
                                        }

                                        validateResponseContentType(
                                                        response.headers(),
                                                        requestId);

                                        log.info(
                                                        "[report-render-client] requestId={} "
                                                                        + "render completed: {} bytes streamed to {}",
                                                        requestId,
                                                        renderedFile.size(),
                                                        renderedFile.path());

                                        return renderedFile;
                                }

                                if (isRetryableStatus(
                                                response.statusCode())
                                                && attempt < maxAttempts) {
                                        long delayMs = resolveRetryDelayMs(
                                                        response.headers(),
                                                        attempt);

                                        log.warn(
                                                        "[report-render-client] requestId={} HTTP {} "
                                                                        + "on attempt {}/{}. Retrying in {} ms",
                                                        requestId,
                                                        response.statusCode(),
                                                        attempt,
                                                        maxAttempts,
                                                        delayMs);

                                        sleepBeforeRetry(
                                                        delayMs,
                                                        requestId);

                                        continue;
                                }

                                throw buildHttpFailure(
                                                response,
                                                requestId);

                        } catch (ResourceAccessException e) {
                                if (attempt < maxAttempts) {
                                        long delayMs = resolveBackoffMs(
                                                        attempt);

                                        log.warn(
                                                        "[report-render-client] requestId={} "
                                                                        + "transport failure on attempt {}/{}. "
                                                                        + "Retrying in {} ms: {}",
                                                        requestId,
                                                        attempt,
                                                        maxAttempts,
                                                        delayMs,
                                                        safeExceptionMessage(e));

                                        sleepBeforeRetry(
                                                        delayMs,
                                                        requestId);

                                        continue;
                                }

                                throw new ReportServiceException(
                                                ReportErrorCode.PDF_RENDER_FAILED,
                                                "Failed to call remote render service after "
                                                                + maxAttempts
                                                                + " attempt(s) at: "
                                                                + url,
                                                e);

                        } catch (RestClientException e) {
                                throw new ReportServiceException(
                                                ReportErrorCode.PDF_RENDER_FAILED,
                                                "Failed to call remote render service at: "
                                                                + url,
                                                e);
                        }
                }

                throw new ReportServiceException(
                                ReportErrorCode.PDF_RENDER_FAILED,
                                "Remote render service exhausted all retry attempts");
        }

        private RenderHttpResponse executeRenderRequest(
                        String url,
                        JsonNode payload,
                        String requestId,
                        Path targetFile) {

                return restTemplate.execute(
                                url,
                                HttpMethod.POST,

                                request -> {
                                        HttpHeaders headers = request.getHeaders();

                                        headers.setContentType(
                                                        MediaType.APPLICATION_JSON);

                                        headers.setAccept(
                                                        List.of(
                                                                        MediaType.APPLICATION_PDF));

                                        headers.set(
                                                        "X-Eficentra-Render-Request-Id",
                                                        requestId);

                                        objectMapper.writeValue(
                                                        request.getBody(),
                                                        payload);
                                },

                                response -> {
                                        int statusCode = response
                                                        .getStatusCode()
                                                        .value();

                                        HttpHeaders responseHeaders = new HttpHeaders();

                                        responseHeaders.putAll(
                                                        response.getHeaders());

                                        if (isSuccessful(statusCode)) {
                                                validateDeclaredLength(
                                                                responseHeaders);

                                                RenderedReportFile renderedFile = streamPdfToFile(
                                                                response.getBody(),
                                                                targetFile,
                                                                requestId);

                                                return new RenderHttpResponse(
                                                                statusCode,
                                                                responseHeaders,
                                                                renderedFile,
                                                                null);
                                        }

                                        byte[] errorBody = readLimitedErrorBody(
                                                        response.getBody());

                                        return new RenderHttpResponse(
                                                        statusCode,
                                                        responseHeaders,
                                                        null,
                                                        errorBody);
                                });
        }

        private RenderedReportFile streamPdfToFile(
                        InputStream inputStream,
                        Path targetFile,
                        String requestId)
                        throws IOException {

                Path normalizedTarget = targetFile
                                .toAbsolutePath()
                                .normalize();

                Files.createDirectories(
                                normalizedTarget.getParent());

                MessageDigest digest = createSha256Digest();

                long maximumBytes = maximumPdfBytes();

                byte[] signature = inputStream.readNBytes(
                                PDF_SIGNATURE.length);

                if (!Arrays.equals(
                                signature,
                                PDF_SIGNATURE)) {
                        throw new ReportServiceException(
                                        ReportErrorCode.PDF_RENDER_FAILED,
                                        "Render service returned invalid PDF content");
                }

                long totalBytes = signature.length;

                try (
                                OutputStream outputStream = new BufferedOutputStream(
                                                Files.newOutputStream(
                                                                normalizedTarget,
                                                                StandardOpenOption.WRITE,
                                                                StandardOpenOption.TRUNCATE_EXISTING),
                                                COPY_BUFFER_SIZE)) {
                        outputStream.write(signature);
                        digest.update(signature);

                        byte[] buffer = new byte[COPY_BUFFER_SIZE];

                        int read;

                        while ((read = inputStream.read(buffer)) != -1) {
                                totalBytes += read;

                                if (totalBytes > maximumBytes) {
                                        throw new ReportServiceException(
                                                        ReportErrorCode.PDF_RENDER_FAILED,
                                                        "Render service PDF exceeds the "
                                                                        + "configured maximum size of "
                                                                        + properties.getMaxPdfSizeMb()
                                                                        + " MB");
                                }

                                outputStream.write(
                                                buffer,
                                                0,
                                                read);

                                digest.update(
                                                buffer,
                                                0,
                                                read);
                        }
                }

                if (totalBytes <= PDF_SIGNATURE.length) {

                        throw new ReportServiceException(
                                        ReportErrorCode.PDF_RENDER_FAILED,
                                        "Render service returned an incomplete PDF file");
                }

                return new RenderedReportFile(
                                normalizedTarget,
                                totalBytes,
                                HexFormat.of()
                                                .formatHex(
                                                                digest.digest()),
                                requestId);
        }

        private byte[] readLimitedErrorBody(
                        InputStream inputStream)
                        throws IOException {

                byte[] body = inputStream.readNBytes(
                                MAX_ERROR_BODY_BYTES + 1);

                return body.length <= MAX_ERROR_BODY_BYTES
                                ? body
                                : Arrays.copyOf(
                                                body,
                                                MAX_ERROR_BODY_BYTES);
        }

        private void validateDeclaredLength(
                        HttpHeaders headers) {

                long declaredLength = headers.getContentLength();

                if (declaredLength > maximumPdfBytes()) {

                        throw new ReportServiceException(
                                        ReportErrorCode.PDF_RENDER_FAILED,
                                        "Render service PDF exceeds the "
                                                        + "configured maximum size of "
                                                        + properties.getMaxPdfSizeMb()
                                                        + " MB");
                }
        }

        private void validateResponseContentType(
                        HttpHeaders headers,
                        String requestId) {

                MediaType contentType = headers.getContentType();

                if (contentType != null
                                && !MediaType.APPLICATION_PDF
                                                .isCompatibleWith(contentType)
                                && !MediaType.APPLICATION_OCTET_STREAM
                                                .isCompatibleWith(contentType)) {

                        log.warn(
                                        "[report-render-client] requestId={} "
                                                        + "valid PDF signature received with "
                                                        + "unexpected Content-Type {}",
                                        requestId,
                                        contentType);
                }
        }

        private void validateRenderInputs(
                        JsonNode payload,
                        Path targetFile) {

                if (payload == null
                                || payload.isNull()) {

                        throw new ReportServiceException(
                                        ReportErrorCode.PDF_RENDER_FAILED,
                                        "Report payload is empty");
                }

                if (targetFile == null) {
                        throw new ReportServiceException(
                                        ReportErrorCode.PDF_RENDER_FAILED,
                                        "Report staging file is required");
                }

                Path normalizedTarget = targetFile
                                .toAbsolutePath()
                                .normalize();

                if (!Files.exists(normalizedTarget)
                                || !Files.isRegularFile(
                                                normalizedTarget)) {
                        throw new ReportServiceException(
                                        ReportErrorCode.PDF_RENDER_FAILED,
                                        "Report staging file does not exist: "
                                                        + normalizedTarget);
                }
        }

        private MessageDigest createSha256Digest() {
                try {
                        return MessageDigest.getInstance(
                                        "SHA-256");

                } catch (NoSuchAlgorithmException e) {
                        throw new ReportServiceException(
                                        ReportErrorCode.PDF_RENDER_FAILED,
                                        "SHA-256 algorithm is not available",
                                        e);
                }
        }

        private ReportServiceException buildHttpFailure(
                        RenderHttpResponse response,
                        String requestId) {

                String detail = extractErrorMessage(
                                response.errorBody());

                String category;

                if (response.statusCode() == 429) {
                        category = "Render service is saturated";

                } else if (response.statusCode() == 503) {
                        category = "Render service is temporarily unavailable";

                } else {
                        category = "Render service returned HTTP "
                                        + response.statusCode();
                }

                String message = category
                                + (detail.isBlank()
                                                ? ""
                                                : ": " + detail)
                                + " [requestId="
                                + requestId
                                + "]";

                return new ReportServiceException(
                                ReportErrorCode.PDF_RENDER_FAILED,
                                message);
        }

        private String extractErrorMessage(
                        byte[] body) {

                if (body == null
                                || body.length == 0) {

                        return "";
                }

                try {
                        JsonNode error = objectMapper.readTree(body);

                        String message = firstNonBlank(
                                        error.path("message")
                                                        .asText(null),

                                        error.path("error")
                                                        .asText(null));

                        if (message != null) {
                                return limitText(
                                                message,
                                                MAX_ERROR_MESSAGE_LENGTH);
                        }

                } catch (Exception ignored) {
                        /*
                         * El cuerpo puede ser texto plano.
                         */
                }

                return limitText(
                                sanitizeBodyPreview(body),
                                MAX_ERROR_MESSAGE_LENGTH);
        }

        private long resolveRetryDelayMs(
                        HttpHeaders headers,
                        int attempt) {

                long retryAfterMs = parseRetryAfterMs(
                                headers != null
                                                ? headers.getFirst(
                                                                HttpHeaders.RETRY_AFTER)
                                                : null);

                if (retryAfterMs > 0) {
                        return clampLong(
                                        retryAfterMs,
                                        100,
                                        Math.max(
                                                        100,
                                                        properties.getMaxBackoffMs()));
                }

                return resolveBackoffMs(
                                attempt);
        }

        private long resolveBackoffMs(
                        int attempt) {

                long initial = Math.max(
                                100,
                                properties.getInitialBackoffMs());

                long maximum = Math.max(
                                initial,
                                properties.getMaxBackoffMs());

                long multiplier = 1L << Math.min(
                                20,
                                Math.max(
                                                0,
                                                attempt - 1));

                long exponential = Math.min(
                                maximum,
                                initial * multiplier);

                long jitter = ThreadLocalRandom
                                .current()
                                .nextLong(
                                                0,
                                                251);

                return Math.min(
                                maximum,
                                exponential + jitter);
        }

        private long parseRetryAfterMs(
                        String value) {

                if (value == null
                                || value.isBlank()) {

                        return -1;
                }

                String normalized = value.trim();

                /*
                 * Formato Retry-After en segundos.
                 */
                try {
                        long seconds = Long.parseLong(
                                        normalized);

                        return Math.max(
                                        0,
                                        seconds * 1000L);

                } catch (NumberFormatException ignored) {
                        /*
                         * También puede ser una fecha HTTP.
                         */
                }

                try {
                        ZonedDateTime retryTime = ZonedDateTime.parse(
                                        normalized,
                                        DateTimeFormatter.RFC_1123_DATE_TIME);

                        return Math.max(
                                        0,
                                        Duration.between(
                                                        Instant.now(),
                                                        retryTime.toInstant()).toMillis());

                } catch (DateTimeParseException ignored) {
                        return -1;
                }
        }

        private void sleepBeforeRetry(
                        long delayMs,
                        String requestId) {

                try {
                        Thread.sleep(delayMs);

                } catch (InterruptedException e) {
                        Thread.currentThread()
                                        .interrupt();

                        throw new ReportServiceException(
                                        ReportErrorCode.PDF_RENDER_FAILED,
                                        "Report rendering was interrupted "
                                                        + "while waiting to retry"
                                                        + " [requestId="
                                                        + requestId
                                                        + "]",
                                        e);
                }
        }

        private int maximumPdfBytes() {
                int maxPdfSizeMb = clamp(
                                properties.getMaxPdfSizeMb(),
                                1,
                                512);

                return maxPdfSizeMb
                                * 1024
                                * 1024;
        }

        private boolean isSuccessful(
                        int statusCode) {

                return statusCode >= 200
                                && statusCode < 300;
        }

        private boolean isRetryableStatus(
                        int statusCode) {

                return statusCode == 408
                                || statusCode == 429
                                || statusCode == 502
                                || statusCode == 503
                                || statusCode == 504;
        }

        private String sanitizeBodyPreview(
                        byte[] body) {

                if (body == null
                                || body.length == 0) {

                        return "";
                }

                String text = new String(
                                body,
                                StandardCharsets.UTF_8)
                                .replaceAll(
                                                "[\\r\\n\\t]+",
                                                " ")
                                .replaceAll(
                                                "\\s{2,}",
                                                " ")
                                .trim();

                return limitText(
                                text,
                                MAX_ERROR_MESSAGE_LENGTH);
        }

        private String safeExceptionMessage(
                        Exception exception) {

                String message = exception.getMessage();

                return message == null
                                || message.isBlank()
                                                ? exception
                                                                .getClass()
                                                                .getSimpleName()
                                                : limitText(
                                                                message,
                                                                MAX_ERROR_MESSAGE_LENGTH);
        }

        private String firstNonBlank(
                        String first,
                        String second) {

                if (first != null
                                && !first.isBlank()) {

                        return first;
                }

                if (second != null
                                && !second.isBlank()) {

                        return second;
                }

                return null;
        }

        private String limitText(
                        String value,
                        int maximumLength) {

                if (value == null) {
                        return "";
                }

                if (value.length() <= maximumLength) {

                        return value;
                }

                return value.substring(
                                0,
                                maximumLength - 1) + "…";
        }

        private int clamp(
                        int value,
                        int minimum,
                        int maximum) {

                return Math.max(
                                minimum,
                                Math.min(
                                                maximum,
                                                value));
        }

        private long clampLong(
                        long value,
                        long minimum,
                        long maximum) {

                return Math.max(
                                minimum,
                                Math.min(
                                                maximum,
                                                value));
        }

        private String buildRenderUrl() {
                String baseUrl = properties.getBaseUrl();

                String renderPath = properties.getRenderPath();

                if (baseUrl.endsWith("/")
                                && renderPath.startsWith("/")) {
                        return baseUrl.substring(
                                        0,
                                        baseUrl.length() - 1) + renderPath;

                } else if (!baseUrl.endsWith("/")
                                && !renderPath.startsWith("/")) {
                        return baseUrl
                                        + "/"
                                        + renderPath;

                } else {
                        return baseUrl
                                        + renderPath;
                }
        }

        private record RenderHttpResponse(
                        int statusCode,
                        HttpHeaders headers,
                        RenderedReportFile renderedFile,
                        byte[] errorBody) {
        }
}