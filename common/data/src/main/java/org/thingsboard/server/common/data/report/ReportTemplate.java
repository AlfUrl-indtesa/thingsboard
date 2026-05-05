package org.thingsboard.server.common.data.report;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.thingsboard.server.common.data.id.ReportTemplateId;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReportTemplate extends BaseData<ReportTemplateId> {

    @NotNull
    private TenantId tenantId;

    private CustomerId customerId;

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private ReportType type;

    @NotNull
    private ReportTemplateStatus status = ReportTemplateStatus.DRAFT;

    @NotNull
    private ReportScopeType scopeType;

    @Valid
    @NotNull
    private ReportEntityFilter entityFilter;

    @Valid
    @NotNull
    private List<ReportSectionConfig> sections;

    @Valid
    private ReportBrandingConfig branding;

    @Valid
    private ReportTimeRangeConfig defaultTimeRange;

    @Valid
    private ReportGenerationOptions generationOptions;

    @NotNull
    private ReportOutputFormat outputFormat = ReportOutputFormat.PDF;

    @NotNull
    private Boolean system = Boolean.FALSE;

    private UUID createdBy;

    private Long updatedTime;

    private UUID updatedBy;
}