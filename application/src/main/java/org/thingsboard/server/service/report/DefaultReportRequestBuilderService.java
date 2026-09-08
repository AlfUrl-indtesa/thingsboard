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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportTemplate;

@Service
@RequiredArgsConstructor
public class DefaultReportRequestBuilderService implements ReportRequestBuilderService {

    private final ObjectMapper objectMapper;

    @Override
    public JsonNode buildExecutionRequest(ReportTemplate reportTemplate, GenerateReportRequest request) {
        if (reportTemplate == null || request == null) {
            throw new ReportServiceException(
                    ReportErrorCode.PAYLOAD_BUILD_FAILED,
                    "Report template and generation request are required");
        }

        if (reportTemplate.getId() == null
                || reportTemplate.getType() == null
                || reportTemplate.getName() == null
                || reportTemplate.getName().isBlank()) {
            throw new ReportServiceException(
                    ReportErrorCode.PAYLOAD_BUILD_FAILED,
                    "A persisted report template with name and type is required");
        }

        if (request.getStartTs() == null
                || request.getEndTs() == null
                || request.getStartTs() >= request.getEndTs()) {
            throw new ReportServiceException(
                    ReportErrorCode.PAYLOAD_BUILD_FAILED,
                    "A valid report generation time range is required");
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.set(
                "request",
                objectMapper.valueToTree(request));
        root.put("templateId", reportTemplate.getId().toString());
        root.put("templateName", reportTemplate.getName());
        root.put("reportType", reportTemplate.getType().name());
        root.put("startTs", request.getStartTs());
        root.put("endTs", request.getEndTs());

        if (request.getLocale() != null) {
            root.put("locale", request.getLocale());
        }
        if (request.getTimezone() != null) {
            root.put("timezone", request.getTimezone());
        }
        if (request.getEntityIds() != null) {
            root.set("entityIds", objectMapper.valueToTree(request.getEntityIds()));
        }

        root.set("scope", objectMapper.valueToTree(reportTemplate.getEntityFilter()));
        root.set("generationOptions", objectMapper.valueToTree(reportTemplate.getGenerationOptions()));

        return root;
    }
}
