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