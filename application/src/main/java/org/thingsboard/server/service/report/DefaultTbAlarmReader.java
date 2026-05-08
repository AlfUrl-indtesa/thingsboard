package org.thingsboard.server.service.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.alarm.AlarmQueryV2;
import org.thingsboard.server.common.data.alarm.AlarmSearchStatus;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.report.ReportAlarmItem;
import org.thingsboard.server.common.data.report.ReportAlarmQuery;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportTargetEntity;
import org.thingsboard.server.dao.alarm.AlarmService;
import org.thingsboard.server.common.data.page.SortOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultTbAlarmReader implements TbAlarmReader {

    private static final int DEFAULT_LIMIT = 100;
    private static final String DEFAULT_SORT_PROPERTY = "createdTime";
    

    private final AlarmService alarmService;
    private final ReportEntityIdFactory reportEntityIdFactory;

    @Override
    public List<ReportAlarmItem> readAlarms(TenantId tenantId,
            ReportTargetEntity entity,
            Long startTs,
            Long endTs,
            ReportAlarmQuery query) {
        validate(tenantId, entity, startTs, endTs);

        EntityId entityId = reportEntityIdFactory.toEntityId(entity);
        ReportAlarmQuery effectiveQuery = query != null ? query : new ReportAlarmQuery();

        SortOrder sortOrder = new SortOrder(
                DEFAULT_SORT_PROPERTY,
                resolveSortDirection(effectiveQuery.getOrderBy()));

        TimePageLink pageLink = new TimePageLink(
                resolveLimit(effectiveQuery.getLimit()),
                0,
                null,
                sortOrder,
                startTs,
                endTs);

        AlarmQueryV2 alarmQuery = new AlarmQueryV2(
                entityId,
                pageLink,
                Collections.emptyList(),
                buildSearchStatuses(effectiveQuery.getIncludeCleared()),
                buildSeverities(effectiveQuery.getSeverity()),
                null);

        try {
            PageData<AlarmInfo> pageData = alarmService.findAlarmsV2(tenantId, alarmQuery);
            return mapAlarms(pageData != null ? pageData.getData() : Collections.emptyList(), entity);
        } catch (Exception e) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "Failed to read alarms for entity: " + entity.getEntityId(),
                    e);
        }
    }

    private void validate(TenantId tenantId,
            ReportTargetEntity entity,
            Long startTs,
            Long endTs) {
        if (tenantId == null) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "TenantId is required for alarm query");
        }

        if (entity == null || entity.getEntityId() == null || entity.getEntityType() == null) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_ENTITY_SCOPE,
                    "Valid target entity is required for alarm query");
        }

        if (startTs == null || endTs == null || startTs >= endTs) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_TIME_RANGE,
                    "Invalid alarm time range");
        }
    }

    private int resolveLimit(Integer limit) {
        return (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;
    }

    private SortOrder.Direction resolveSortDirection(String orderBy) {
        if (orderBy == null || orderBy.isBlank()) {
            return SortOrder.Direction.DESC;
        }

        String normalized = orderBy.trim().toUpperCase();
        return "ASC".equals(normalized) ? SortOrder.Direction.ASC : SortOrder.Direction.DESC;
    }

    private List<AlarmSearchStatus> buildSearchStatuses(Boolean includeCleared) {
        if (Boolean.TRUE.equals(includeCleared)) {
            return Collections.singletonList(AlarmSearchStatus.ANY);
        }
        return Collections.singletonList(AlarmSearchStatus.ACTIVE);
    }

    private List<AlarmSeverity> buildSeverities(String severity) {
        if (severity == null || severity.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return Collections.singletonList(AlarmSeverity.valueOf(severity.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new ReportServiceException(
                    ReportErrorCode.DATA_COLLECTION_FAILED,
                    "Invalid alarm severity: " + severity);
        }
    }

    private List<ReportAlarmItem> mapAlarms(List<AlarmInfo> alarms, ReportTargetEntity entity) {
        List<ReportAlarmItem> result = new ArrayList<>();
        if (alarms == null || alarms.isEmpty()) {
            return result;
        }

        for (AlarmInfo alarm : alarms) {
            ReportAlarmItem item = new ReportAlarmItem();
            item.setTimestamp(alarm.getCreatedTime());
            item.setSeverity(alarm.getSeverity() != null ? alarm.getSeverity().name() : null);
            item.setSource(entity.getName());
            item.setName(alarm.getType());
            item.setDescription(alarm.getDetails() != null ? alarm.getDetails().toString() : null);
            result.add(item);
        }

        return result;
    }
}