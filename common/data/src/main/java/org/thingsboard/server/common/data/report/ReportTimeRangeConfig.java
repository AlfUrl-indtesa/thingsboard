package org.thingsboard.server.common.data.report;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ReportTimeRangeConfig {

    @NotNull
    private ReportTimeRangeMode mode;

    private Long defaultStartTs;

    private Long defaultEndTs;

    private Integer lastValue;

    private ReportTimeUnit lastUnit;

    @NotNull
    private Boolean allowCustomOverride = Boolean.TRUE;
}