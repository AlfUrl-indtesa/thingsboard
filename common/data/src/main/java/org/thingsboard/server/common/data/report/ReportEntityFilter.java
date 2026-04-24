package org.thingsboard.server.common.data.report;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityGroupId;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class ReportEntityFilter {

    @NotNull
    private ReportScopeType scopeType;

    private String entityType;

    private List<EntityId> entityIds;

    private EntityGroupId entityGroupId;

    private JsonNode criteria;
}