package org.thingsboard.server.service.report;

import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportAlarmItem;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTemplate;

import java.util.List;

public interface ReportAlarmService {

    List<ReportAlarmItem> findAlarms(ReportTemplate template,
                                     GenerateReportRequest request,
                                     List<ReportTargetEntity> entities);
}