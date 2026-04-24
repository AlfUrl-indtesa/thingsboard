package org.thingsboard.server.common.data.report;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ReportTelemetryQuery {

    @NotBlank
    private String key;

    private String label;

    private String unit;

    @NotNull
    private Long startTs;

    @NotNull
    private Long endTs;

    private Integer limit;

    private Long interval;

    @NotNull
    private ReportAggregationType aggregation = ReportAggregationType.NONE;

    /**
     * Expected values: ASC or DESC.
     */
    private String orderBy;
}