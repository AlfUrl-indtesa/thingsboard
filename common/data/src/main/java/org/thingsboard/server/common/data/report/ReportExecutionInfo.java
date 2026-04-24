package org.thingsboard.server.common.data.report;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReportExecutionInfo extends ReportExecution {

    private String requestedByName;
}