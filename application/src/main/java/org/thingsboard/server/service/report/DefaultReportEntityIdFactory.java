/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

        EntityType entityType;

        try {
            entityType = EntityType.valueOf(entity.getEntityType());
        } catch (IllegalArgumentException exception) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_ENTITY_SCOPE,
                    "Unsupported report entity type: "
                            + entity.getEntityType(),
                    exception);
        }

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
