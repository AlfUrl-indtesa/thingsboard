package org.thingsboard.server.service.report;

import org.thingsboard.server.common.data.report.ReportVariableMetadata;

public interface ReportVariableMetadataService {

    ReportVariableMetadata resolve(String key, String providedLabel, String providedUnit);
}