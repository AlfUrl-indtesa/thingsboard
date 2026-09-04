/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
