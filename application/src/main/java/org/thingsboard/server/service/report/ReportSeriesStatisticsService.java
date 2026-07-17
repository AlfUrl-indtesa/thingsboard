/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import org.thingsboard.server.common.data.report.ReportKpiAggregationType;
import org.thingsboard.server.common.data.report.ReportMetricPoint;
import org.thingsboard.server.common.data.report.ReportSeriesStatistics;

import java.util.List;

public interface ReportSeriesStatisticsService {

    /**
     * Calcula todas las estadísticas de una serie en una sola
     * iteración.
     */
    ReportSeriesStatistics calculate(
            List<ReportMetricPoint> points
    );

    /**
     * Obtiene desde el resultado estadístico el valor de la
     * agregación solicitada.
     */
    Double resolveValue(
            ReportSeriesStatistics statistics,
            ReportKpiAggregationType aggregation
    );
}