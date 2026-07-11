/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
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
    private String granularity;

    private ReportAggregationType aggregation;

    private Long startTs;
    private Long endTs;

    /**
     * Periodo inmediatamente anterior al periodo principal.
     */
    private Long previousStartTs;
    private Long previousEndTs;

    private List<ReportMetricPoint> points = new ArrayList<>();

    /**
     * Muestras del periodo anterior, ya convertidas con el mismo
     * scale y offset configurados para la variable.
     */
    private List<ReportMetricPoint> previousPoints = new ArrayList<>();
}