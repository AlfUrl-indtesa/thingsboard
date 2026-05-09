package org.thingsboard.server.common.data.report;

import lombok.Data;
import org.thingsboard.server.common.data.id.EntityId;

import java.util.List;

@Data
public class GenerateReportRequest {
    private Long startTs;
    private Long endTs;

    private List<EntityId> entityIds;

    private String locale;

    private String timezone;
}

