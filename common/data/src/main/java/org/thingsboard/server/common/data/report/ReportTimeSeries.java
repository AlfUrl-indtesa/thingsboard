package org.thingsboard.server.common.data.report;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class ReportTimeSeries {

    private UUID entityId;
    private String entityType;
    private String entityName;

    private String key;
    private String label;
    private String unit;

    private ReportAggregationType aggregation;

    private Long startTs;
    private Long endTs;

    private List<ReportMetricPoint> points = new ArrayList<>();
}