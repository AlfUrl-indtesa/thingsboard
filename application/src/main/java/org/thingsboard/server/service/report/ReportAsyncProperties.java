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
@ConfigurationProperties(prefix = "report.async")
public class ReportAsyncProperties {

    /**
     * Enables background report generation.
     */
    private boolean enabled = true;

    /**
     * Number of report jobs executed concurrently.
     */
    private int corePoolSize = 2;

    /**
     * Maximum concurrent worker count.
     */
    private int maxPoolSize = 2;

    /**
     * In-memory executor queue. PostgreSQL remains the
     * persistent source of truth.
     */
    private int queueCapacity = 50;

    private int keepAliveSeconds = 60;

    /**
     * Wait time during a graceful ThingsBoard shutdown.
     */
    private int shutdownAwaitTerminationSeconds = 30;

    /**
     * Enables periodic recovery of pending and stale jobs.
     */
    private boolean recoveryEnabled = true;

    private long recoveryInitialDelayMs = 10000;

    private long recoveryIntervalMs = 5000;

    private int recoveryBatchSize = 100;

    /**
     * A RUNNING job older than this value is returned to
     * PENDING. This must exceed the complete render retry
     * window.
     */
    private int staleRunningTimeoutMinutes = 60;
}
