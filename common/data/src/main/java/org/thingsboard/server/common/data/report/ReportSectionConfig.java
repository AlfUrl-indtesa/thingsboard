package org.thingsboard.server.common.data.report;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;


@Data
public class ReportSectionConfig {
    private String key;
    private ReportSectionType type;
    private String title;
    private Integer order;
    private Boolean visible = Boolean.TRUE;
    private Boolean pageBreakBefore = Boolean.FALSE;

    private JsonNode config;
}

