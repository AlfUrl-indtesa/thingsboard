package org.thingsboard.server.common.data.report;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ReportKpiQuery {

    @NotBlank
    private String key;

    @NotBlank
    private String label;

    private String unit;

    @NotNull
    private ReportKpiAggregationType aggregation;

    /**
     * If true, aggregate all selected entities into one KPI.
     * If false, produce one KPI per entity.
     */
    @NotNull
    private Boolean combineEntities = Boolean.FALSE;

    /**
     * Optional status hint logic can be added later.
     */
    private String status;
}