package org.thingsboard.server.service.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportVariableConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DefaultReportVariableConfigService implements ReportVariableConfigService {

    private final ObjectMapper objectMapper;

    @Override
    public List<ReportVariableConfig> extractVariables(List<ReportSectionConfig> sections) {
        List<ReportVariableConfig> result = new ArrayList<>();

        if (sections == null || sections.isEmpty()) {
            return result;
        }

        for (ReportSectionConfig section : sections) {
            if (section == null || section.getConfig() == null) {
                continue;
            }

            result.addAll(extractVariables(section.getConfig()));
        }

        return deduplicate(result);
    }

    @Override
    public List<ReportVariableConfig> extractVariables(JsonNode config) {
        if (config == null || !config.has("variables") || !config.get("variables").isArray()) {
            return List.of();
        }

        try {
            List<ReportVariableConfig> variables = objectMapper.convertValue(
                    config.get("variables"),
                    new TypeReference<List<ReportVariableConfig>>() {}
            );

            return deduplicate(variables);
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public List<String> extractEnabledKeys(List<ReportVariableConfig> variables) {
        Set<String> keys = new LinkedHashSet<>();

        if (variables == null) {
            return List.of();
        }

        for (ReportVariableConfig variable : variables) {
            if (Boolean.FALSE.equals(variable.getEnabled())) {
                continue;
            }

            if (variable.getKey() != null && !variable.getKey().isBlank()) {
                keys.add(variable.getKey());
            }
        }

        return new ArrayList<>(keys);
    }

    @Override
    public boolean matchesEntity(ReportVariableConfig variable, EntityId entityId) {
        if (variable == null || variable.getEntityId() == null || entityId == null) {
            return false;
        }

        return variable.getEntityId().getEntityType() == entityId.getEntityType()
                && variable.getEntityId().getId().equals(entityId.getId());
    }

    @Override
    public double applyConversion(ReportVariableConfig variable, double value) {
        if (variable == null) {
            return value;
        }

        double scale = variable.getScale() != null ? variable.getScale() : 1.0;
        double offset = variable.getOffset() != null ? variable.getOffset() : 0.0;

        return (value * scale) + offset;
    }

    private List<ReportVariableConfig> deduplicate(List<ReportVariableConfig> variables) {
        if (variables == null || variables.isEmpty()) {
            return List.of();
        }

        List<ReportVariableConfig> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (ReportVariableConfig variable : variables) {
            if (variable == null || variable.getEntityId() == null || variable.getKey() == null) {
                continue;
            }

            String uniqueKey = variable.getEntityId().getEntityType() + ":" +
                    variable.getEntityId().getId() + ":" +
                    variable.getKey();

            if (seen.add(uniqueKey)) {
                result.add(variable);
            }
        }

        return result;
    }
}