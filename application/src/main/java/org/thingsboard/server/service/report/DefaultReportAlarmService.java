package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportAlarmItem;
import org.thingsboard.server.common.data.report.ReportAlarmQuery;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportSectionType;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.common.data.report.ReportTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultReportAlarmService implements ReportAlarmService {

    private final TbAlarmReader tbAlarmReader;
    private final ObjectMapper objectMapper;

    @Override
    public List<ReportAlarmItem> findAlarms(ReportTemplate template,
                                            GenerateReportRequest request,
                                            List<ReportTargetEntity> entities) {
        List<ReportAlarmItem> result = new ArrayList<>();

        if (template == null || template.getSections() == null || template.getSections().isEmpty()) {
            return result;
        }

        if (entities == null || entities.isEmpty()) {
            return result;
        }

        TenantId tenantId = template.getTenantId();

        for (ReportSectionConfig section : template.getSections()) {
            if (section.getType() != ReportSectionType.ALARM_LIST || !Boolean.TRUE.equals(section.getVisible())) {
                continue;
            }

            ReportAlarmQuery query = extractAlarmQuery(section.getConfig());

            for (ReportTargetEntity entity : entities) {
                result.addAll(
                        tbAlarmReader.readAlarms(
                                tenantId,
                                entity,
                                request.getStartTs(),
                                request.getEndTs(),
                                query
                        )
                );
            }
        }

        result.sort(Comparator.comparing(ReportAlarmItem::getTimestamp).reversed());
        return result;
    }

    private ReportAlarmQuery extractAlarmQuery(JsonNode config) {
        if (config == null || config.isNull()) {
            return new ReportAlarmQuery();
        }
        return objectMapper.convertValue(config, ReportAlarmQuery.class);
    }
}