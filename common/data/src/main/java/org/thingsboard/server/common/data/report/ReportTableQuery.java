package org.thingsboard.server.common.data.report;

import lombok.Data;


@Data
public class ReportTableQuery {
    private String key;
    private String label;

    private String unit;
    private ReportKpiAggregationType aggregation;

    /**
     * Column key in the output row. If omitted, key will be used.
     */
    private String columnKey;

    /**
     * left, center, right
     */
    private String align = "right";
}

