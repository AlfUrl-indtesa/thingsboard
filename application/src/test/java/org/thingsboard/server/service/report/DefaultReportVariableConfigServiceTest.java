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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportVariableConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultReportVariableConfigServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    private DefaultReportVariableConfigService service;

    @BeforeEach
    void setUp() {
        service = new DefaultReportVariableConfigService(
                objectMapper);
    }

    @Test
    void deserializesRealVariableConfiguration() {
        UUID deviceUuid = UUID.randomUUID();

        JsonNode config = JacksonUtil.toJsonNode("""
                {
                  "variables": [
                    {
                      "entityId": {
                        "entityType": "DEVICE",
                        "id": "%s"
                      },
                      "key": "temperature",
                      "enabled": true,
                      "scale": 1.5,
                      "offset": -2.0
                    }
                  ]
                }
                """.formatted(deviceUuid));

        DefaultReportVariableConfigService actualService =
                new DefaultReportVariableConfigService(
                        JacksonUtil.OBJECT_MAPPER);

        List<ReportVariableConfig> variables =
                actualService.extractVariables(config);

        assertThat(variables).hasSize(1);

        ReportVariableConfig variable = variables.getFirst();

        assertThat(variable.getEntityId())
                .isEqualTo(new DeviceId(deviceUuid));
        assertThat(variable.getKey())
                .isEqualTo("temperature");
        assertThat(variable.getEnabled()).isTrue();
        assertThat(variable.getScale()).isEqualTo(1.5);
        assertThat(variable.getOffset()).isEqualTo(-2.0);
    }

    @Test
    void combinesSectionsAndRemovesDuplicates() {
        UUID deviceUuid = UUID.randomUUID();
        UUID assetUuid = UUID.randomUUID();

        ReportVariableConfig first =
                variable(
                        new DeviceId(deviceUuid),
                        "temperature");

        ReportVariableConfig duplicate =
                variable(
                        new DeviceId(deviceUuid),
                        "temperature");

        ReportVariableConfig second =
                variable(
                        new AssetId(assetUuid),
                        "pressure");

        ArrayNode firstArray = variablesNode();
        ArrayNode secondArray = variablesNode();

        returnVariables(
                firstArray,
                List.of(first));

        returnVariables(
                secondArray,
                List.of(duplicate, second));

        ReportSectionConfig firstSection =
                section(configWith(firstArray));

        ReportSectionConfig emptySection =
                new ReportSectionConfig();

        ReportSectionConfig secondSection =
                section(configWith(secondArray));

        List<ReportVariableConfig> result =
                service.extractVariables(
                        Arrays.asList(
                                firstSection,
                                null,
                                emptySection,
                                secondSection));

        assertThat(result)
                .containsExactly(first, second);
    }

    @Test
    void returnsEmptyForAbsentConfigurations() {
        assertThat(service.extractVariables(
                (List<ReportSectionConfig>) null))
                .isEmpty();

        assertThat(service.extractVariables(List.of()))
                .isEmpty();

        assertThat(service.extractVariables(
                (JsonNode) null))
                .isEmpty();

        assertThat(service.extractVariables(
                JsonNodeFactory.instance.nullNode()))
                .isEmpty();

        assertThat(service.extractVariables(
                JsonNodeFactory.instance.objectNode()))
                .isEmpty();
    }

    @Test
    void rejectsVariablesThatAreNotAnArray() {
        ObjectNode config =
                JsonNodeFactory.instance.objectNode();

        config.put("variables", "invalid");

        assertInvalid(() ->
                service.extractVariables(config));
    }

    @Test
    void wrapsObjectMapperConversionFailures() {
        ArrayNode variables = variablesNode();

        when(objectMapper.convertValue(
                same(variables),
                any(TypeReference.class)))
                .thenThrow(new IllegalArgumentException(
                        "invalid entity id"));

        ReportServiceException exception =
                assertInvalid(() ->
                        service.extractVariables(
                                configWith(variables)));

        assertThat(exception.getCause())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullConversionResult() {
        ArrayNode variables = variablesNode();

        returnVariables(variables, null);

        assertInvalid(() ->
                service.extractVariables(
                        configWith(variables)));
    }

    @Test
    void rejectsNullVariableEntries() {
        ArrayNode variables = variablesNode();

        returnVariables(
                variables,
                Collections.singletonList(null));

        assertInvalid(() ->
                service.extractVariables(
                        configWith(variables)));
    }

    @Test
    void rejectsVariableWithoutValidEntityId() {
        ArrayNode variables = variablesNode();

        ReportVariableConfig missingEntity =
                new ReportVariableConfig();
        missingEntity.setKey("temperature");

        returnVariables(
                variables,
                List.of(missingEntity));

        assertInvalid(() ->
                service.extractVariables(
                        configWith(variables)));

        EntityId invalidEntity = org.mockito.Mockito.mock(
                EntityId.class);

        when(invalidEntity.getEntityType())
                .thenReturn(EntityType.DEVICE);
        when(invalidEntity.getId())
                .thenReturn(null);

        ReportVariableConfig missingUuid =
                variable(invalidEntity, "temperature");

        ArrayNode secondVariables = variablesNode();

        returnVariables(
                secondVariables,
                List.of(missingUuid));

        assertInvalid(() ->
                service.extractVariables(
                        configWith(secondVariables)));
    }

    @Test
    void rejectsVariableWithoutTelemetryKey() {
        ArrayNode variables = variablesNode();

        ReportVariableConfig variable =
                variable(
                        new DeviceId(UUID.randomUUID()),
                        " ");

        returnVariables(
                variables,
                List.of(variable));

        assertInvalid(() ->
                service.extractVariables(
                        configWith(variables)));
    }

    @Test
    void extractsDistinctEnabledKeysInOriginalOrder() {
        ReportVariableConfig temperature =
                variable(
                        new DeviceId(UUID.randomUUID()),
                        "temperature");

        ReportVariableConfig duplicateTemperature =
                variable(
                        new DeviceId(UUID.randomUUID()),
                        "temperature");

        ReportVariableConfig disabledPressure =
                variable(
                        new DeviceId(UUID.randomUUID()),
                        "pressure");
        disabledPressure.setEnabled(false);

        ReportVariableConfig blank =
                variable(
                        new DeviceId(UUID.randomUUID()),
                        " ");

        List<String> keys =
                service.extractEnabledKeys(
                        Arrays.asList(
                                temperature,
                                null,
                                duplicateTemperature,
                                disabledPressure,
                                blank));

        assertThat(keys)
                .containsExactly("temperature");
    }

    @Test
    void matchesEntitiesByTypeAndUuid() {
        UUID entityUuid = UUID.randomUUID();

        ReportVariableConfig variable =
                variable(
                        new DeviceId(entityUuid),
                        "temperature");

        assertThat(service.matchesEntity(
                variable,
                new DeviceId(entityUuid)))
                .isTrue();

        assertThat(service.matchesEntity(
                variable,
                new AssetId(entityUuid)))
                .isFalse();

        assertThat(service.matchesEntity(
                variable,
                new DeviceId(UUID.randomUUID())))
                .isFalse();

        assertThat(service.matchesEntity(null, null))
                .isFalse();
    }

    @Test
    void appliesScaleAndOffsetConversion() {
        ReportVariableConfig variable =
                variable(
                        new DeviceId(UUID.randomUUID()),
                        "temperature");

        variable.setScale(2.5);
        variable.setOffset(-1.0);

        assertThat(service.applyConversion(variable, 4.0))
                .isEqualTo(9.0);

        variable.setScale(null);
        variable.setOffset(null);

        assertThat(service.applyConversion(variable, 4.0))
                .isEqualTo(4.0);

        assertThat(service.applyConversion(null, 4.0))
                .isEqualTo(4.0);
    }

    private ReportServiceException assertInvalid(
            Executable executable) {

        ReportServiceException exception =
                assertThrows(
                        ReportServiceException.class,
                        executable);

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        ReportErrorCode.INVALID_ENTITY_SCOPE);

        return exception;
    }

    private ReportVariableConfig variable(
            EntityId entityId,
            String key) {

        ReportVariableConfig variable =
                new ReportVariableConfig();

        variable.setEntityId(entityId);
        variable.setKey(key);

        return variable;
    }

    private ReportSectionConfig section(
            JsonNode config) {

        ReportSectionConfig section =
                new ReportSectionConfig();

        section.setConfig(config);

        return section;
    }

    private ArrayNode variablesNode() {
        return JsonNodeFactory.instance.arrayNode();
    }

    private ObjectNode configWith(
            ArrayNode variables) {

        ObjectNode config =
                JsonNodeFactory.instance.objectNode();

        config.set("variables", variables);

        return config;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void returnVariables(
            ArrayNode variablesNode,
            List<ReportVariableConfig> variables) {

        when(objectMapper.convertValue(
                same(variablesNode),
                any(TypeReference.class)))
                .thenReturn(variables);
    }

}
