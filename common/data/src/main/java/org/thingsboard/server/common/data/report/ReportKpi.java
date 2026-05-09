package org.thingsboard.server.common.data.report;

import lombok.Data;

@Data
public class ReportKpi {

    private String key;
    private String label;
    private Double value;
    private String formattedValue;
    private String unit;
    private String status;
}

