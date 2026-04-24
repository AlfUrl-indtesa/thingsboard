package org.thingsboard.server.common.data.report;

import lombok.Data;

@Data
public class ReportAlarmItem {

    private Long timestamp;
    private String severity;
    private String source;
    private String name;
    private String description;
}