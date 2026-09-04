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

import org.junit.jupiter.api.Test;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportTargetEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultReportEntityIdFactoryTest {

    private final DefaultReportEntityIdFactory factory =
            new DefaultReportEntityIdFactory();

    @Test
    void createsDeviceId() {
        UUID uuid = UUID.randomUUID();
        ReportTargetEntity target = target(uuid, "DEVICE");

        EntityId result = factory.toEntityId(target);

        assertInstanceOf(DeviceId.class, result);
        assertEquals(uuid, result.getId());
        assertEquals(EntityType.DEVICE, result.getEntityType());
    }

    @Test
    void createsAssetId() {
        UUID uuid = UUID.randomUUID();
        ReportTargetEntity target = target(uuid, "ASSET");

        EntityId result = factory.toEntityId(target);

        assertInstanceOf(AssetId.class, result);
        assertEquals(uuid, result.getId());
        assertEquals(EntityType.ASSET, result.getEntityType());
    }

    @Test
    void rejectsMalformedEntityTypeWithTypedError() {
        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> factory.toEntityId(
                        target(UUID.randomUUID(), "NOT_A_TYPE")));

        assertEquals(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                exception.getErrorCode());
    }

    @Test
    void rejectsUnsupportedThingsBoardEntityType() {
        ReportServiceException exception = assertThrows(
                ReportServiceException.class,
                () -> factory.toEntityId(
                        target(UUID.randomUUID(), "TENANT")));

        assertEquals(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                exception.getErrorCode());
    }

    @Test
    void rejectsIncompleteTarget() {
        ReportServiceException nullTarget = assertThrows(
                ReportServiceException.class,
                () -> factory.toEntityId(null));

        assertEquals(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                nullTarget.getErrorCode());

        ReportTargetEntity missingId = new ReportTargetEntity();
        missingId.setEntityType("DEVICE");

        assertThrows(
                ReportServiceException.class,
                () -> factory.toEntityId(missingId));
    }

    private ReportTargetEntity target(UUID uuid, String entityType) {
        ReportTargetEntity target = new ReportTargetEntity();
        target.setEntityId(uuid);
        target.setEntityType(entityType);
        return target;
    }
}
