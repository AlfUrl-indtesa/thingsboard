package org.thingsboard.server.service.report;

import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportAlarmQuery;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportAlarmItem;

import java.util.List;

public interface TbAlarmReader {

    List<ReportAlarmItem> readAlarms(TenantId tenantId,
                                     ReportTargetEntity entity,
                                     Long startTs,
                                     Long endTs,
                                     ReportAlarmQuery query);
}