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
     * Maximum time for queue wait, rendering and PDF transfer.
     */
    private int readTimeoutMs = 420000;

    /**
     * Total HTTP attempts, including the first request.
     */
    private int maxAttempts = 3;

    /**
     * Initial exponential retry delay.
     */
    private int initialBackoffMs = 1000;

    /**
     * Maximum delay between retries.
     */
    private int maxBackoffMs = 30000;

    /**
     * Maximum accepted PDF response size.
     */
    private int maxPdfSizeMb = 100;
}
