package org.thingsboard.server.service.report;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "report.storage")
public class ReportStorageProperties {

    /**
     * Base directory where generated report files will be stored.
     */
    private String baseDir = "data/reports";
}