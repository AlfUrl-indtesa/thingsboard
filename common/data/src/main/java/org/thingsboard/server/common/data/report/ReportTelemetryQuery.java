package org.thingsboard.server.common.data.report;

import lombok.Data;


@Data
public class ReportTelemetryQuery {
    private String key;

    private String label;

    private String unit;
    private Long startTs;
    private Long endTs;

    private Integer limit;

    private Long interval;
    private ReportAggregationType aggregation = ReportAggregationType.NONE;

    /**
     * Expected values: ASC or DESC.
     */
    private String orderBy;
}

