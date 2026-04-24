package org.thingsboard.server.common.data.report;

import lombok.Data;

@Data
public class ReportGenerationOptions {

    private ReportOutputFormat outputFormat = ReportOutputFormat.PDF;

    private ReportPaperSize paperSize = ReportPaperSize.A4;

    private ReportOrientation orientation = ReportOrientation.PORTRAIT;

    private Boolean includeCover = Boolean.TRUE;

    private Boolean includeSummary = Boolean.TRUE;

    private Boolean includePageNumbers = Boolean.TRUE;

    private Boolean includeGeneratedAt = Boolean.TRUE;

    private String fileNamePattern;
}