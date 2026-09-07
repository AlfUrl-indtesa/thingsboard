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
package org.thingsboard.server.service.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportVariableConfig;
import org.thingsboard.server.queue.util.TbCoreComponent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@TbCoreComponent
@RequiredArgsConstructor
public class DefaultReportVariableConfigService
        implements ReportVariableConfigService {

    private static final TypeReference<List<ReportVariableConfig>>
            VARIABLE_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @Override
    public List<ReportVariableConfig> extractVariables(
            List<ReportSectionConfig> sections) {

        if (sections == null || sections.isEmpty()) {
            return List.of();
        }

        List<ReportVariableConfig> variables =
                new ArrayList<>();

        for (ReportSectionConfig section : sections) {
            if (section == null
                    || section.getConfig() == null
                    || section.getConfig().isNull()) {
                continue;
            }

            variables.addAll(
                    extractVariables(section.getConfig()));
        }

        return normalizeVariables(variables);
    }

    @Override
    public List<ReportVariableConfig> extractVariables(
            JsonNode config) {

        if (config == null || config.isNull()) {
            return List.of();
        }

        JsonNode variablesNode = config.get("variables");

        if (variablesNode == null || variablesNode.isNull()) {
            return List.of();
        }

        if (!variablesNode.isArray()) {
            throw invalidConfiguration(
                    "Report section variables must be an array");
        }

        try {
            List<ReportVariableConfig> variables =
                    objectMapper.convertValue(
                            variablesNode,
                            VARIABLE_LIST_TYPE);

            if (variables == null) {
                throw invalidConfiguration(
                        "Report section variables could not be parsed");
            }

            return normalizeVariables(variables);
        } catch (ReportServiceException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_ENTITY_SCOPE,
                    "Report section variables are invalid",
                    e);
        }
    }

    @Override
    public List<String> extractEnabledKeys(
            List<ReportVariableConfig> variables) {

        if (variables == null || variables.isEmpty()) {
            return List.of();
        }

        Set<String> keys = new LinkedHashSet<>();

        for (ReportVariableConfig variable : variables) {
            if (variable == null
                    || Boolean.FALSE.equals(
                            variable.getEnabled())) {
                continue;
            }

            if (StringUtils.hasText(variable.getKey())) {
                keys.add(variable.getKey());
            }
        }

        return new ArrayList<>(keys);
    }

    @Override
    public boolean matchesEntity(
            ReportVariableConfig variable,
            EntityId entityId) {

        if (variable == null
                || variable.getEntityId() == null
                || variable.getEntityId().getEntityType() == null
                || variable.getEntityId().getId() == null
                || entityId == null
                || entityId.getEntityType() == null
                || entityId.getId() == null) {
            return false;
        }

        return variable.getEntityId().getEntityType()
                == entityId.getEntityType()
                && variable.getEntityId().getId()
                .equals(entityId.getId());
    }

    @Override
    public double applyConversion(
            ReportVariableConfig variable,
            double value) {

        if (variable == null) {
            return value;
        }

        double scale = variable.getScale() != null
                ? variable.getScale()
                : 1.0;

        double offset = variable.getOffset() != null
                ? variable.getOffset()
                : 0.0;

        return (value * scale) + offset;
    }

    private List<ReportVariableConfig> normalizeVariables(
            List<ReportVariableConfig> variables) {

        if (variables == null || variables.isEmpty()) {
            return List.of();
        }

        List<ReportVariableConfig> result =
                new ArrayList<>();

        Set<String> seen = new LinkedHashSet<>();

        for (ReportVariableConfig variable : variables) {
            validateVariable(variable);

            EntityId entityId = variable.getEntityId();

            String uniqueKey =
                    entityId.getEntityType()
                            + ":"
                            + entityId.getId()
                            + ":"
                            + variable.getKey();

            if (seen.add(uniqueKey)) {
                result.add(variable);
            }
        }

        return result;
    }

    private void validateVariable(
            ReportVariableConfig variable) {

        if (variable == null) {
            throw invalidConfiguration(
                    "Report variable cannot be null");
        }

        EntityId entityId = variable.getEntityId();

        if (entityId == null
                || entityId.getEntityType() == null
                || entityId.getId() == null) {
            throw invalidConfiguration(
                    "Every report variable requires a valid entityId");
        }

        if (!StringUtils.hasText(variable.getKey())) {
            throw invalidConfiguration(
                    "Every report variable requires a telemetry key");
        }
    }

    private ReportServiceException invalidConfiguration(
            String message) {

        return new ReportServiceException(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                message);
    }

}
