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
        public byte[] renderPdf(
                        JsonNode payload) {

                if (payload == null
                                || payload.isNull()) {

                        throw new ReportServiceException(
                                        ReportErrorCode.PDF_RENDER_FAILED,
                                        "Report payload is empty");
                }

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
                                                requestId);

                                if (response == null) {
                                        throw new ReportServiceException(
                                                        ReportErrorCode.PDF_RENDER_FAILED,
                                                        "Render service returned no HTTP response");
                                }

                                if (isSuccessful(
                                                response.statusCode())) {

                                        return validatePdfResponse(
                                                        response,
                                                        requestId);
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
                                /*
                                 * Incluye errores de conexión y timeout.
                                 */
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
                        String requestId) {

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

                                        /*
                                         * El JSON se escribe directamente al stream
                                         * de la solicitud.
                                         */
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

                                        int bodyLimit = isSuccessful(statusCode)
                                                        ? maximumPdfBytes()
                                                        : MAX_ERROR_BODY_BYTES;

                                        long declaredLength = responseHeaders
                                                        .getContentLength();

                                        if (isSuccessful(statusCode)
                                                        && declaredLength > bodyLimit) {
                                                throw new ReportServiceException(
                                                                ReportErrorCode.PDF_RENDER_FAILED,
                                                                "Render service PDF exceeds the "
                                                                                + "configured maximum size of "
                                                                                + properties.getMaxPdfSizeMb()
                                                                                + " MB");
                                        }

                                        /*
                                         * Se lee como máximo un byte adicional para
                                         * detectar respuestas superiores al límite.
                                         */
                                        byte[] body = response
                                                        .getBody()
                                                        .readNBytes(
                                                                        bodyLimit + 1);

                                        if (body.length > bodyLimit) {
                                                if (isSuccessful(statusCode)) {
                                                        throw new ReportServiceException(
                                                                        ReportErrorCode.PDF_RENDER_FAILED,
                                                                        "Render service PDF exceeds the "
                                                                                        + "configured maximum size of "
                                                                                        + properties.getMaxPdfSizeMb()
                                                                                        + " MB");
                                                }

                                                body = Arrays.copyOf(
                                                                body,
                                                                bodyLimit);
                                        }

                                        return new RenderHttpResponse(
                                                        statusCode,
                                                        responseHeaders,
                                                        body);
                                });
        }

        private byte[] validatePdfResponse(
                        RenderHttpResponse response,
                        String requestId) {

                byte[] body = response.body();

                if (body == null
                                || body.length == 0) {

                        throw new ReportServiceException(
                                        ReportErrorCode.PDF_RENDER_FAILED,
                                        "Render service returned empty PDF content");
                }

                /*
                 * No basta con confiar en Content-Type.
                 * El archivo debe comenzar con la firma %PDF-.
                 */
                if (!hasPdfSignature(body)) {
                        String preview = sanitizeBodyPreview(body);

                        throw new ReportServiceException(
                                        ReportErrorCode.PDF_RENDER_FAILED,
                                        "Render service returned invalid PDF content"
                                                        + (preview.isBlank()
                                                                        ? ""
                                                                        : ": " + preview));
                }

                MediaType contentType = response
                                .headers()
                                .getContentType();

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

                log.info(
                                "[report-render-client] requestId={} "
                                                + "render completed: {} bytes",
                                requestId,
                                body.length);

                return body;
        }

        private ReportServiceException buildHttpFailure(
                        RenderHttpResponse response,
                        String requestId) {

                String detail = extractErrorMessage(
                                response.body());

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

        private boolean hasPdfSignature(
                        byte[] body) {

                return body.length >= 5
                                && body[0] == '%'
                                && body[1] == 'P'
                                && body[2] == 'D'
                                && body[3] == 'F'
                                && body[4] == '-';
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
                        byte[] body) {
        }
}