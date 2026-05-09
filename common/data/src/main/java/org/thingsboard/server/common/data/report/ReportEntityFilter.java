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

