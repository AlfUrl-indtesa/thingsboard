package org.thingsboard.server.common.data.report;

import lombok.Data;


@Data
public class ReportTimeRangeConfig {
    private ReportTimeRangeMode mode;

    private Long defaultStartTs;

    private Long defaultEndTs;

    private Integer lastValue;

    private ReportTimeUnit lastUnit;
    private Boolean allowCustomOverride = Boolean.TRUE;
}

