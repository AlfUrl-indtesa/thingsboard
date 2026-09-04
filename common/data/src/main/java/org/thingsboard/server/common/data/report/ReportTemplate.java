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
import lombok.EqualsAndHashCode;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.thingsboard.server.common.data.id.ReportTemplateId;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReportTemplate extends BaseData<ReportTemplateId> {
    private TenantId tenantId;

    private CustomerId customerId;
    private String name;

    private String description;
    private ReportType type;
    private ReportTemplateStatus status = ReportTemplateStatus.DRAFT;
    private ReportScopeType scopeType;
    private ReportEntityFilter entityFilter;
    private List<ReportSectionConfig> sections;
    private ReportBrandingConfig branding;
    private ReportTimeRangeConfig defaultTimeRange;
    private ReportGenerationOptions generationOptions;
    private ReportOutputFormat outputFormat = ReportOutputFormat.PDF;
    private Boolean system = Boolean.FALSE;

    private UUID createdBy;

    private Long updatedTime;

    private UUID updatedBy;
}
