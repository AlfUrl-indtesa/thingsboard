package org.thingsboard.server.service.report;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "report.render")
public class ReportRenderProperties {

    /**
     * Base URL of the external PDF render service.
     * Example: http://127.0.0.1:3000
     */
    private String baseUrl = "http://127.0.0.1:3000";

    /**
     * Render endpoint path.
     */
    private String renderPath = "/render-report";

    /**
     * HTTP connect timeout in milliseconds.
     */
    private int connectTimeoutMs = 5000;

    /**
     * HTTP read timeout in milliseconds.
     */
    private int readTimeoutMs = 120000;
}