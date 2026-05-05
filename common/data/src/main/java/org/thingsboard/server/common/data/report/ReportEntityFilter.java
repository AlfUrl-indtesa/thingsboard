package org.thingsboard.server.common.data.report;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.thingsboard.server.common.data.id.EntityId;

import java.util.List;
import java.util.UUID;

@Data
public class ReportEntityFilter {

    private ReportScopeType scopeType;

    private String entityType;

    private List<EntityId> entityIds;

    private UUID entityGroupId;

    private JsonNode criteria;
}