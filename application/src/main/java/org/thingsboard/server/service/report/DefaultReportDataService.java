package org.thingsboard.server.service.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.report.*;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultReportDataService implements ReportDataService {

    private final ReportEntityResolverService reportEntityResolverService;
    private final ReportTelemetryService reportTelemetryService;
    private final ReportKpiService reportKpiService;
    private final ReportChartService reportChartService;
    private final ReportTableService reportTableService;
    private final ReportAlarmService reportAlarmService;

    @Override
    public ReportDataResult collectReportData(ReportTemplate template, GenerateReportRequest request) {
        List<ReportTargetEntity> entities = reportEntityResolverService.resolveEntities(template, request);

        ReportDataResult result = new ReportDataResult();
        result.setEntities(entities);
        result.setKpis(reportKpiService.buildKpis(template, request, entities));
        result.setTimeSeries(reportChartService.buildTimeSeries(template, request, entities));
        result.setTables(reportTableService.buildTables(template, request, entities));
        result.setAlarms(reportAlarmService.findAlarms(template, request, entities));

        return result;
    }
}