package org.thingsboard.server.common.data.report;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class GenerateReportResponse {

    private UUID executionId;
    private ReportExecutionStatus status;
}

