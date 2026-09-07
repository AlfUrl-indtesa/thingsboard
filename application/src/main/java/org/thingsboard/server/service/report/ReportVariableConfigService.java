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

import com.fasterxml.jackson.databind.JsonNode;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.report.ReportSectionConfig;
import org.thingsboard.server.common.data.report.ReportVariableConfig;

import java.util.List;

public interface ReportVariableConfigService {

    List<ReportVariableConfig> extractVariables(
            List<ReportSectionConfig> sections);

    List<ReportVariableConfig> extractVariables(
            JsonNode config);

    List<String> extractEnabledKeys(
            List<ReportVariableConfig> variables);

    boolean matchesEntity(
            ReportVariableConfig variable,
            EntityId entityId);

    double applyConversion(
            ReportVariableConfig variable,
            double value);

}
