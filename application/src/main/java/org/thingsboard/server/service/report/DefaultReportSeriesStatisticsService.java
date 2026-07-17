/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.report.ReportKpiAggregationType;
import org.thingsboard.server.common.data.report.ReportMetricPoint;
import org.thingsboard.server.common.data.report.ReportSeriesStatistics;

import java.util.List;

@Service
public class DefaultReportSeriesStatisticsService
        implements ReportSeriesStatisticsService {

    @Override
    public ReportSeriesStatistics calculate(
            List<ReportMetricPoint> points) {

        ReportSeriesStatistics result =
                new ReportSeriesStatistics();

        int totalPointCount =
                points != null
                        ? points.size()
                        : 0;

        result.setTotalPointCount(
                totalPointCount
        );

        if (points == null
                || points.isEmpty()) {
            return result;
        }

        int validPointCount = 0;

        double min = 0.0;
        double max = 0.0;

        /*
         * El promedio incremental evita depender de una segunda
         * iteración y reduce el riesgo de desbordamiento que
         * implicaría calcularlo exclusivamente desde la suma.
         */
        double average = 0.0;

        double sum = 0.0;
        boolean finiteSum = true;

        Double firstByOrder = null;
        Double lastByOrder = null;

        Long firstTimestamp = null;
        Long lastTimestamp = null;

        Double firstTimestampValue = null;
        Double lastTimestampValue = null;

        for (ReportMetricPoint point : points) {
            if (point == null
                    || point.getValue() == null
                    || !Double.isFinite(
                            point.getValue()
                    )) {
                continue;
            }

            double value =
                    point.getValue();

            validPointCount++;

            if (validPointCount == 1) {
                min = value;
                max = value;
                average = value;
                firstByOrder = value;
            } else {
                min =
                        Math.min(
                                min,
                                value
                        );

                max =
                        Math.max(
                                max,
                                value
                        );

                average +=
                        (value - average)
                                / validPointCount;
            }

            lastByOrder = value;

            if (finiteSum) {
                sum += value;

                if (!Double.isFinite(sum)) {
                    finiteSum = false;
                }
            }

            Long timestamp =
                    point.getTs();

            if (timestamp != null) {
                if (firstTimestamp == null
                        || timestamp < firstTimestamp) {
                    firstTimestamp = timestamp;
                    firstTimestampValue = value;
                }

                if (lastTimestamp == null
                        || timestamp > lastTimestamp) {
                    lastTimestamp = timestamp;
                    lastTimestampValue = value;
                }
            }
        }

        result.setValidPointCount(
                validPointCount
        );

        result.setInvalidPointCount(
                totalPointCount - validPointCount
        );

        if (validPointCount == 0) {
            return result;
        }

        result.setHasData(true);

        result.setMin(min);
        result.setMax(max);
        result.setAvg(average);

        result.setSum(
                finiteSum
                        ? sum
                        : null
        );

        /*
         * Cuando hay timestamps, primero y último se determinan
         * cronológicamente. Si no hay timestamps, se conserva el
         * orden recibido.
         */
        Double firstValue =
                firstTimestampValue != null
                        ? firstTimestampValue
                        : firstByOrder;

        Double lastValue =
                lastTimestampValue != null
                        ? lastTimestampValue
                        : lastByOrder;

        result.setFirst(firstValue);
        result.setLast(lastValue);

        result.setFirstTs(firstTimestamp);
        result.setLastTs(lastTimestamp);

        if (firstValue != null
                && lastValue != null) {
            double delta =
                    lastValue - firstValue;

            result.setDelta(
                    Double.isFinite(delta)
                            ? delta
                            : null
            );
        }

        return result;
    }

    @Override
    public Double resolveValue(
            ReportSeriesStatistics statistics,
            ReportKpiAggregationType aggregation) {

        if (statistics == null
                || !statistics.isHasData()
                || aggregation == null) {
            return null;
        }

        return switch (aggregation) {
            case AVG ->
                    statistics.getAvg();

            case MIN ->
                    statistics.getMin();

            case MAX ->
                    statistics.getMax();

            case SUM ->
                    statistics.getSum();

            case COUNT ->
                    (double) statistics.getValidPointCount();

            case FIRST ->
                    statistics.getFirst();

            case LAST ->
                    statistics.getLast();

            case DELTA ->
                    statistics.getDelta();
        };
    }
}