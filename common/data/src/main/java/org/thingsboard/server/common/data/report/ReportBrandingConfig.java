package org.thingsboard.server.common.data.report;

import lombok.Data;

@Data
public class ReportBrandingConfig {

    private String companyName;

    private String logoResourceKey;

    private String primaryColor;

    private String secondaryColor;

    private String accentColor;

    private String textColor;

    private String footerText;

    private String footerLogoResourceKey;

    private String customerName;
    private String siteName;

    private String coverTitle;
    private String coverSubtitle;

    private String logoUrl;

    private String confidentialityText;

    private Boolean showPageNumbers = true;
    private Boolean showGeneratedDate = true;
}
