package org.thingsboard.server.common.data.report;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReportDataResult {

    private List<ReportTargetEntity> entities = new ArrayList<>();
    private List<ReportKpi> kpis = new ArrayList<>();
    private List<ReportTimeSeries> timeSeries = new ArrayList<>();
    private List<ReportTable> tables = new ArrayList<>();
    private List<ReportAlarmItem> alarms = new ArrayList<>();
    private List<String> observations = new ArrayList<>();
}