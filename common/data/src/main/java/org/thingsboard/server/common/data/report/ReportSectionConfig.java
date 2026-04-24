package org.thingsboard.server.common.data.report;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ReportSectionConfig {

    @NotBlank
    private String key;

    @NotNull
    private ReportSectionType type;

    @NotBlank
    private String title;

    @NotNull
    @Min(0)
    private Integer order;

    @NotNull
    private Boolean visible = Boolean.TRUE;

    @NotNull
    private Boolean pageBreakBefore = Boolean.FALSE;

    private JsonNode config;
}