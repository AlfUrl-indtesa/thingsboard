package org.thingsboard.server.common.data.report;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReportTemplateInfo extends ReportTemplate {

    private Integer sectionCount;
}

