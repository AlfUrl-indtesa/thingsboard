package org.thingsboard.server.common.data.report;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ReportTableQuery {

    @NotBlank
    private String key;

    @NotBlank
    private String label;

    private String unit;

    @NotNull
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