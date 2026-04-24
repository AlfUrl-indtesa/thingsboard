package org.thingsboard.server.service.report;

import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportTargetEntity;

@Service
public class DefaultReportEntityIdFactory implements ReportEntityIdFactory {

    @Override
    public EntityId toEntityId(ReportTargetEntity entity) {
        if (entity == null || entity.getEntityId() == null || entity.getEntityType() == null) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_ENTITY_SCOPE,
                    "Invalid report target entity"
            );
        }

        EntityType entityType = EntityType.valueOf(entity.getEntityType());

        switch (entityType) {
            case DEVICE:
                return new DeviceId(entity.getEntityId());
            case ASSET:
                return new AssetId(entity.getEntityId());
            default:
                throw new ReportServiceException(
                        ReportErrorCode.INVALID_ENTITY_SCOPE,
                        "Unsupported entity type for report telemetry: " + entity.getEntityType()
                );
        }
    }
}