package org.thingsboard.server.common.data.report;

import lombok.Data;


@Data
public class ReportChartQuery {
    private String key;

    private String label;

    private String unit;

    /**
     * Whether to merge all selected entities into a single logical chart group.
     * The actual rendering layer may decide how to display the returned series.
     */
    private Boolean combineEntities = Boolean.FALSE;

    /**
     * Telemetry aggregation to use at query time.
     * Example: NONE, AVG, MIN, MAX, SUM, COUNT
     */
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

