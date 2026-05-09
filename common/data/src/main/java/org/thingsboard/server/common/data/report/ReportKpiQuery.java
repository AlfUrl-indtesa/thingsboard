package org.thingsboard.server.common.data.report;

import lombok.Data;


@Data
public class ReportKpiQuery {
    private String key;
    private String label;

    private String unit;
    private ReportKpiAggregationType aggregation;

    /**
     * If true, aggregate all selected entities into one KPI.
     * If false, produce one KPI per entity.
     */
    private Boolean combineEntities = Boolean.FALSE;

    /**
     * Optional status hint logic can be added later.
     */
    private String status;
}

