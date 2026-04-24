package org.thingsboard.server.service.report;

import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.report.ReportKpiAggregationType;
import org.thingsboard.server.common.data.report.ReportMetricPoint;

import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ReportKpiCalculationSupport {

    private static final DecimalFormat DEFAULT_FORMAT = new DecimalFormat("#,##0.###");

    public Double calculate(List<ReportMetricPoint> points, ReportKpiAggregationType aggregation) {
        if (points == null || points.isEmpty() || aggregation == null) {
            return null;
        }

        List<Double> values = points.stream()
                .map(ReportMetricPoint::getValue)
                .filter(v -> v != null)
                .collect(Collectors.toList());

        if (values.isEmpty()) {
            return null;
        }

        switch (aggregation) {
            case AVG:
                return values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
            case MIN:
                return values.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
            case MAX:
                return values.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
            case SUM:
                return values.stream().mapToDouble(Double::doubleValue).sum();
            case COUNT:
                return (double) values.size();
            case FIRST:
                return points.stream()
                        .filter(p -> p.getValue() != null)
                        .min(Comparator.comparing(ReportMetricPoint::getTs))
                        .map(ReportMetricPoint::getValue)
                        .orElse(null);
            case LAST:
                return points.stream()
                        .filter(p -> p.getValue() != null)
                        .max(Comparator.comparing(ReportMetricPoint::getTs))
                        .map(ReportMetricPoint::getValue)
                        .orElse(null);
            case DELTA:
                Double first = points.stream()
                        .filter(p -> p.getValue() != null)
                        .min(Comparator.comparing(ReportMetricPoint::getTs))
                        .map(ReportMetricPoint::getValue)
                        .orElse(null);

                Double last = points.stream()
                        .filter(p -> p.getValue() != null)
                        .max(Comparator.comparing(ReportMetricPoint::getTs))
                        .map(ReportMetricPoint::getValue)
                        .orElse(null);

                if (first == null || last == null) {
                    return null;
                }
                return last - first;
            default:
                return null;
        }
    }

    public String format(Double value) {
        if (value == null) {
            return null;
        }
        return DEFAULT_FORMAT.format(value);
    }
}