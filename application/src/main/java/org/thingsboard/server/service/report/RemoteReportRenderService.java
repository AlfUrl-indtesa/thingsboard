package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.server.common.data.report.ReportErrorCode;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RemoteReportRenderService implements ReportRenderService {

    private final ReportRenderProperties properties;
    private final RestTemplateBuilder restTemplateBuilder;

    @Override
    public byte[] renderPdf(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new ReportServiceException(
                    ReportErrorCode.PDF_RENDER_FAILED,
                    "Report payload is empty"
            );
        }

        RestTemplate restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .build();

        String url = buildRenderUrl();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_PDF));

        HttpEntity<JsonNode> requestEntity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    byte[].class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ReportServiceException(
                        ReportErrorCode.PDF_RENDER_FAILED,
                        "Render service returned non-success status: " + response.getStatusCode()
                );
            }

            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new ReportServiceException(
                        ReportErrorCode.PDF_RENDER_FAILED,
                        "Render service returned empty PDF content"
                );
            }

            return body;
        } catch (RestClientException e) {
            throw new ReportServiceException(
                    ReportErrorCode.PDF_RENDER_FAILED,
                    "Failed to call remote render service at: " + url,
                    e
            );
        }
    }

    private String buildRenderUrl() {
        String baseUrl = properties.getBaseUrl();
        String renderPath = properties.getRenderPath();

        if (baseUrl.endsWith("/") && renderPath.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + renderPath;
        } else if (!baseUrl.endsWith("/") && !renderPath.startsWith("/")) {
            return baseUrl + "/" + renderPath;
        } else {
            return baseUrl + renderPath;
        }
    }
}