package org.thingsboard.server.common.data.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReportMetricPoint {

    private Long ts;
    private Double value;
}

