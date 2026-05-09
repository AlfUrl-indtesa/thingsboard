package org.thingsboard.server.service.report;
import org.thingsboard.server.common.data.report.ReportEntityFilter;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.report.*;

@Service
public class DefaultReportValidationService implements ReportValidationService {

    @Override
    public void validateTemplateForSave(ReportTemplate reportTemplate) {
        if (reportTemplate == null) {
            throw new ReportServiceException(ReportErrorCode.UNKNOWN_ERROR, "Report template is null");
        }

        if (!StringUtils.hasText(reportTemplate.getName())) {
            throw new ReportServiceException(ReportErrorCode.UNKNOWN_ERROR, "Report template name is required");
        }

        if (reportTemplate.getType() == null) {
            throw new ReportServiceException(ReportErrorCode.UNKNOWN_ERROR, "Report template type is required");
        }

        if (reportTemplate.getScopeType() == null) {
            throw new ReportServiceException(ReportErrorCode.UNKNOWN_ERROR, "Report scope type is required");
        }

        if (reportTemplate.getEntityFilter() == null) {
            throw new ReportServiceException(ReportErrorCode.INVALID_ENTITY_SCOPE, "Entity filter is required");
        }

        if (CollectionUtils.isEmpty(reportTemplate.getSections())) {
            throw new ReportServiceException(ReportErrorCode.UNKNOWN_ERROR, "At least one report section is required");
        }

        validateEntityFilter(reportTemplate.getEntityFilter());
        validateSections(reportTemplate);
        validateTimeRangeConfig(reportTemplate.getDefaultTimeRange());
        validateEntityFilterAccess(currentUser, template.getEntityFilter());
    }

    @Override
    public void validateTemplateForExecution(ReportTemplate reportTemplate) {
        validateTemplateForSave(reportTemplate);

        if (reportTemplate.getStatus() != ReportTemplateStatus.ACTIVE) {
            throw new ReportServiceException(
                    ReportErrorCode.TEMPLATE_DISABLED,
                    "Report template is not active");
        }
    }

    @Override
    public void validateGenerateRequest(GenerateReportRequest request) {
        if (request == null) {
            throw new ReportServiceException(ReportErrorCode.UNKNOWN_ERROR, "Generate report request is null");
        }

        if (request.getStartTs() == null || request.getEndTs() == null) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_TIME_RANGE,
                    "Start and end timestamps are required");
        }

        if (request.getStartTs() >= request.getEndTs()) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_TIME_RANGE,
                    "Start timestamp must be earlier than end timestamp");
        }
    }

    private void validateEntityFilter(ReportEntityFilter entityFilter) {
        if (entityFilter.getScopeType() == null) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_ENTITY_SCOPE,
                    "Entity filter scope type is required");
        }

        switch (entityFilter.getScopeType()) {
            case FIXED_ENTITIES:
                if (CollectionUtils.isEmpty(entityFilter.getEntityIds())) {
                    throw new ReportServiceException(
                            ReportErrorCode.INVALID_ENTITY_SCOPE,
                            "Fixed entity scope requires entityIds");
                }
                break;
            case ENTITY_GROUP:
                if (entityFilter.getEntityGroupId() == null) {
                    throw new ReportServiceException(
                            ReportErrorCode.INVALID_ENTITY_SCOPE,
                            "Entity group scope requires entityGroupId");
                }
                break;
            case DYNAMIC_FILTER:
                if (entityFilter.getCriteria() == null || entityFilter.getCriteria().isNull()) {
                    throw new ReportServiceException(
                            ReportErrorCode.INVALID_ENTITY_SCOPE,
                            "Dynamic filter scope requires criteria");
                }
                break;
            default:
                throw new ReportServiceException(
                        ReportErrorCode.INVALID_ENTITY_SCOPE,
                        "Unsupported report scope type");
        }
    }

    private void validateSections(ReportTemplate reportTemplate) {
        boolean hasVisibleSection = false;

        for (ReportSectionConfig section : reportTemplate.getSections()) {
            if (!StringUtils.hasText(section.getKey())) {
                throw new ReportServiceException(
                        ReportErrorCode.UNKNOWN_ERROR,
                        "Section key is required");
            }
            if (section.getType() == null) {
                throw new ReportServiceException(
                        ReportErrorCode.UNKNOWN_ERROR,
                        "Section type is required");
            }
            if (!StringUtils.hasText(section.getTitle())) {
                throw new ReportServiceException(
                        ReportErrorCode.UNKNOWN_ERROR,
                        "Section title is required");
            }
            if (section.getOrder() == null || section.getOrder() < 0) {
                throw new ReportServiceException(
                        ReportErrorCode.UNKNOWN_ERROR,
                        "Section order must be greater than or equal to zero");
            }
            if (Boolean.TRUE.equals(section.getVisible())) {
                hasVisibleSection = true;
            }
        }

        if (!hasVisibleSection) {
            throw new ReportServiceException(
                    ReportErrorCode.UNKNOWN_ERROR,
                    "At least one visible report section is required");
        }
    }

    private void validateTimeRangeConfig(ReportTimeRangeConfig config) {
        if (config == null) {
            return;
        }

        if (config.getMode() == null) {
            throw new ReportServiceException(
                    ReportErrorCode.INVALID_TIME_RANGE,
                    "Report time range mode is required");
        }

        switch (config.getMode()) {
            case RELATIVE:
                if (config.getLastValue() == null || config.getLastValue() <= 0 || config.getLastUnit() == null) {
                    throw new ReportServiceException(
                            ReportErrorCode.INVALID_TIME_RANGE,
                            "Relative time range requires lastValue > 0 and lastUnit");
                }
                break;
            case ABSOLUTE:
                if (config.getDefaultStartTs() == null || config.getDefaultEndTs() == null ||
                        config.getDefaultStartTs() >= config.getDefaultEndTs()) {
                    throw new ReportServiceException(
                            ReportErrorCode.INVALID_TIME_RANGE,
                            "Absolute time range requires valid defaultStartTs and defaultEndTs");
                }
                break;
            default:
                throw new ReportServiceException(
                        ReportErrorCode.INVALID_TIME_RANGE,
                        "Unsupported time range mode");
        }
    }

    private void validateEntityFilterAccess(SecurityUser currentUser, ReportEntityFilter entityFilter) {
        if (entityFilter == null) {
            throw new IllegalArgumentException("Report entity filter is required.");
        }

        if (entityFilter.getScopeType() == null) {
            throw new IllegalArgumentException("Report scope type is required.");
        }

        if (entityFilter.getEntityType() == null || entityFilter.getEntityType().isBlank()) {
            throw new IllegalArgumentException("Report entity type is required.");
        }

        switch (currentUser.getAuthority()) {
            case TENANT_ADMIN:
                validateTenantAdminScope(entityFilter);
                break;

            case CUSTOMER_USER:
                validateCustomerUserScope(currentUser, entityFilter);
                break;

            default:
                throw new IllegalArgumentException("User authority is not allowed to manage reports.");
        }
    }

    private void validateTenantAdminScope(ReportEntityFilter entityFilter) {
        switch (entityFilter.getScopeType()) {
            case TENANT_ENTITIES:
            case CUSTOMER_ENTITIES:
            case CURRENT_CUSTOMER_ENTITIES:
                return;

            case FIXED_ENTITIES:
                if (entityFilter.getEntityIds() == null || entityFilter.getEntityIds().isEmpty()) {
                    throw new IllegalArgumentException("At least one entity is required.");
                }
                return;

            default:
                throw new IllegalArgumentException("Unsupported report scope type.");
        }
    }

    private void validateCustomerUserScope(SecurityUser currentUser, ReportEntityFilter entityFilter) {
        switch (entityFilter.getScopeType()) {
            case TENANT_ENTITIES:
                throw new IllegalArgumentException("Customer users cannot create tenant-wide reports.");

            case CURRENT_CUSTOMER_ENTITIES:
                return;

            case CUSTOMER_ENTITIES:
                if (entityFilter.getCustomerId() == null) {
                    throw new IllegalArgumentException("Customer id is required.");
                }
                if (!entityFilter.getCustomerId().equals(currentUser.getCustomerId())) {
                    throw new IllegalArgumentException("Customer users cannot create reports for another customer.");
                }
                return;

            case FIXED_ENTITIES:
                if (entityFilter.getEntityIds() == null || entityFilter.getEntityIds().isEmpty()) {
                    throw new IllegalArgumentException("At least one entity is required.");
                }
                return;

            default:
                throw new IllegalArgumentException("Unsupported report scope type.");
        }
    }
}