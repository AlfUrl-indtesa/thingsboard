package org.thingsboard.server.common.data.report;

import lombok.Data;
import org.thingsboard.server.common.data.id.EntityId;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class GenerateReportRequest {

    @NotNull
    private Long startTs;

    @NotNull
    private Long endTs;

    private List<EntityId> entityIds;

    private String locale;

    private String timezone;
}