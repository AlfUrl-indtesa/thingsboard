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

