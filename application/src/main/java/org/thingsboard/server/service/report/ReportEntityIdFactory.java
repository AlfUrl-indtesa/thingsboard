package org.thingsboard.server.service.report;

import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.report.ReportTargetEntity;

public interface ReportEntityIdFactory {

    EntityId toEntityId(ReportTargetEntity entity);
}