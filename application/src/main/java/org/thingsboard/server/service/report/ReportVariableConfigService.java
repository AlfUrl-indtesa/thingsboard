package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportVariableConfig;

import java.util.List;

public interface ReportVariableConfigService {

    List<ReportVariableConfig> extractVariables(List<ReportSectionConfig> sections);

    List<ReportVariableConfig> extractVariables(JsonNode config);

    List<String> extractEnabledKeys(List<ReportVariableConfig> variables);

    boolean matchesEntity(ReportVariableConfig variable, EntityId entityId);

    double applyConversion(ReportVariableConfig variable, double value);
}