package org.thingsboard.server.dao.model.sql;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;
import org.thingsboard.server.common.data.report.*;
import org.thingsboard.server.dao.model.BaseSqlEntity;

import javax.persistence.*;
import java.util.UUID;

@Data
@Entity
@Table(name = "report_template")
@EqualsAndHashCode(callSuper = true)
public class ReportTemplateEntity extends BaseSqlEntity<ReportTemplate> {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private ReportType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ReportTemplateStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 50)
    private ReportScopeType scopeType;

    @Type(type = "jsonb")
    @Column(name = "entity_filter", columnDefinition = "jsonb", nullable = false)
    private ReportEntityFilter entityFilter;

    @Type(type = "jsonb")
    @Column(name = "sections", columnDefinition = "jsonb", nullable = false)
    private java.util.List<ReportSectionConfig> sections;

    @Type(type = "jsonb")
    @Column(name = "branding", columnDefinition = "jsonb")
    private ReportBrandingConfig branding;

    @Type(type = "jsonb")
    @Column(name = "default_time_range", columnDefinition = "jsonb")
    private ReportTimeRangeConfig defaultTimeRange;

    @Type(type = "jsonb")
    @Column(name = "generation_options", columnDefinition = "jsonb")
    private ReportGenerationOptions generationOptions;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_format", nullable = false, length = 50)
    private ReportOutputFormat outputFormat;

    @Column(name = "system", nullable = false)
    private Boolean system;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_time")
    private Long updatedTime;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public ReportTemplateEntity() {
        super();
    }

    public ReportTemplateEntity(ReportTemplate reportTemplate) {
        if (reportTemplate.getId() != null) {
            this.setId(reportTemplate.getId());
        }
        this.setCreatedTime(reportTemplate.getCreatedTime());
        this.tenantId = reportTemplate.getTenantId() != null ? reportTemplate.getTenantId().getId() : null;
        this.customerId = reportTemplate.getCustomerId() != null ? reportTemplate.getCustomerId().getId() : null;
        this.name = reportTemplate.getName();
        this.description = reportTemplate.getDescription();
        this.type = reportTemplate.getType();
        this.status = reportTemplate.getStatus();
        this.scopeType = reportTemplate.getScopeType();
        this.entityFilter = reportTemplate.getEntityFilter();
        this.sections = reportTemplate.getSections();
        this.branding = reportTemplate.getBranding();
        this.defaultTimeRange = reportTemplate.getDefaultTimeRange();
        this.generationOptions = reportTemplate.getGenerationOptions();
        this.outputFormat = reportTemplate.getOutputFormat();
        this.system = reportTemplate.getSystem();
        this.createdBy = reportTemplate.getCreatedBy();
        this.updatedTime = reportTemplate.getUpdatedTime();
        this.updatedBy = reportTemplate.getUpdatedBy();
    }

    @Override
    public ReportTemplate toData() {
        ReportTemplate reportTemplate = new ReportTemplate();
        reportTemplate.setId(this.getUuidId());
        reportTemplate.setCreatedTime(this.getCreatedTime());
        reportTemplate.setTenantId(new org.thingsboard.server.common.data.id.TenantId(tenantId));
        if (customerId != null) {
            reportTemplate.setCustomerId(new org.thingsboard.server.common.data.id.CustomerId(customerId));
        }
        reportTemplate.setName(name);
        reportTemplate.setDescription(description);
        reportTemplate.setType(type);
        reportTemplate.setStatus(status);
        reportTemplate.setScopeType(scopeType);
        reportTemplate.setEntityFilter(entityFilter);
        reportTemplate.setSections(sections);
        reportTemplate.setBranding(branding);
        reportTemplate.setDefaultTimeRange(defaultTimeRange);
        reportTemplate.setGenerationOptions(generationOptions);
        reportTemplate.setOutputFormat(outputFormat);
        reportTemplate.setSystem(system);
        reportTemplate.setCreatedBy(createdBy);
        reportTemplate.setUpdatedTime(updatedTime);
        reportTemplate.setUpdatedBy(updatedBy);
        return reportTemplate;
    }
}