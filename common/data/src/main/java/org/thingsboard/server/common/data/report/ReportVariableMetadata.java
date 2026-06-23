package org.thingsboard.server.common.data.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReportVariableMetadata {

    private String key;
    private String label;
    private String unit;
}