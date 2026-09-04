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
package org.thingsboard.server.common.data.report;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;

import java.util.List;
import java.util.UUID;

@Data
public class ReportEntityFilter {

    private ReportScopeType scopeType;

    /**
     * DEVICE, ASSET, etc.
     */
    private String entityType;

    /**
     * Entidades específicas seleccionadas manualmente.
     * Se usa cuando scopeType = FIXED_ENTITIES.
     */
    private List<EntityId> entityIds;

    /**
     * Customer objetivo.
     * Se usa cuando scopeType = CUSTOMER_ENTITIES.
     */
    private CustomerId customerId;

    /**
     * Reservado para soporte futuro de grupos.
     */
    private UUID entityGroupId;

    /**
     * Reservado para filtros avanzados.
     */
    private JsonNode criteria;
}
