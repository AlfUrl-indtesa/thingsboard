package org.thingsboard.server.common.data.report;

import lombok.Data;

@Data
public class ReportAlarmQuery {

    /**
     * Max number of alarms to return.
     */
    private Integer limit = 100;

    /**
     * Optional severity filter.
     * Example: CRITICAL, MAJOR, MINOR, WARNING, INDETERMINATE
     */
    private String severity;

    /**
     * Whether cleared alarms should be included.
     */
    private Boolean includeCleared = Boolean.TRUE;

    /**
     * ASC or DESC
     */
    private String orderBy = "DESC";
}

