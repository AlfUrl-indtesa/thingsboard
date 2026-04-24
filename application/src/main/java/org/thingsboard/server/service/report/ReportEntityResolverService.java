package org.thingsboard.server.service.report;

import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTemplate;

import java.util.List;

public interface ReportEntityResolverService {

    List<ReportTargetEntity> resolveEntities(ReportTemplate template, GenerateReportRequest request);
}