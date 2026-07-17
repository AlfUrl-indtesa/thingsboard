/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.report.ReportKpiAggregationType;
import org.thingsboard.server.common.data.report.ReportMetricPoint;
import org.thingsboard.server.common.data.report.ReportSeriesStatistics;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ReportKpiCalculationSupport {

    /**
     * DecimalFormat no es thread-safe.
     *
     * Cada hilo de generación utiliza su propia instancia para
     * evitar corrupción de formato cuando se ejecutan varios
     * reportes simultáneamente.
     */
    private static final ThreadLocal<DecimalFormat> DEFAULT_FORMAT = ThreadLocal.withInitial(
            ReportKpiCalculationSupport::createDecimalFormat);

    private final ReportSeriesStatisticsService statisticsService;

    public Double calculate(
            List<ReportMetricPoint> points,
            ReportKpiAggregationType aggregation) {

        ReportSeriesStatistics statistics = statisticsService.calculate(
                points);

        return statisticsService.resolveValue(
                statistics,
                aggregation);
    }

    public String format(
            Double value) {

        if (value == null
                || !Double.isFinite(value)) {
            return null;
        }

        return DEFAULT_FORMAT
                .get()
                .format(value);
    }

    private static DecimalFormat createDecimalFormat() {

        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(
                Locale.US);

        DecimalFormat format = new DecimalFormat(
                "#,##0.###",
                symbols);

        format.setRoundingMode(
                RoundingMode.HALF_UP);

        format.setGroupingUsed(true);

        return format;
    }
}