package org.thingsboard.server.common.data.report;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ReportChartQuery {

    @NotBlank
    private String key;

    private String label;

    private String unit;

    /**
     * Whether to merge all selected entities into a single logical chart group.
     * The actual rendering layer may decide how to display the returned series.
     */
    @NotNull
    private Boolean combineEntities = Boolean.FALSE;

    /**
     * Telemetry aggregation to use at query time.
     * Example: NONE, AVG, MIN, MAX, SUM, COUNT
     */
    @NotNull
    private ReportAggregationType aggregation = ReportAggregationType.NONE;

    /**
     * Optional interval in milliseconds for aggregated queries.
     */
    private Long interval;

    /**
     * Optional maximum number of returned points.
     */
    private Integer limit;

    /**
     * ASC or DESC.
     */
    private String orderBy = "ASC";
}