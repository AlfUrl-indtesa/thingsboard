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
package org.thingsboard.server.common.data.report;

import lombok.Data;
import org.thingsboard.server.common.data.id.EntityId;

@Data
public class ReportVariableConfig {

    private EntityId entityId;
    private String entityName;

    private String key;
    private Boolean enabled = true;

    private String label;
    private String unit;

    private Double scale = 1.0;
    private Double offset = 0.0;

    private Boolean chartEnabled = true;
    private Boolean tableEnabled = true;

    private String granularity = "FULL";

    private ReportVariableAnalysisConfig analysis = new ReportVariableAnalysisConfig();

    private ReportVariableStatsConfig stats = new ReportVariableStatsConfig();

    @Data
    public static class ReportVariableStatsConfig {
        private Boolean min = true;
        private Boolean max = true;
        private Boolean avg = true;
        private Boolean count = true;
        private Boolean sum = false;
        private Boolean first = false;
        private Boolean last = false;
        private Boolean delta = false;

    }

    @Data
    public static class ReportVariableAnalysisConfig {

        private Boolean enabled = false;

        private ReportVariableRangeConfig expectedRange = new ReportVariableRangeConfig();

        private ReportVariableRangeConfig warningRange = new ReportVariableRangeConfig();

        private String performanceDirection = "TARGET_RANGE";

        private Boolean comparePreviousPeriod = true;
        private Boolean detectTrend = true;
        private Boolean detectOutliers = false;

        private Double minimumCoveragePct = 80.0;
    }

    @Data
    public static class ReportVariableRangeConfig {

        private Boolean enabled = false;
        private Double min;
        private Double max;
    }
}