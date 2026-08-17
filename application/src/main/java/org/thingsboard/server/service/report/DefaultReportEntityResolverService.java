package org.thingsboard.server.service.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.*;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.device.DeviceService;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultReportEntityResolverService implements ReportEntityResolverService {

    private final DeviceService deviceService;
    private final AssetService assetService;
    private final ReportEntitySecurityService reportEntitySecurityService;

    @Override
    public List<ReportTargetEntity> resolveEntities(ReportTemplate template, GenerateReportRequest request) {
        reportEntitySecurityService
                .validateExecutionScope(
                        template,
                        request);

        List<ReportTargetEntity> result = new ArrayList<>();

        List<EntityId> sourceEntityIds = request.getEntityIds();
        if (sourceEntityIds == null || sourceEntityIds.isEmpty()) {
            sourceEntityIds = template.getEntityFilter().getEntityIds();
        }

        if (template.getScopeType() == ReportScopeType.FIXED_ENTITIES) {
            if (sourceEntityIds == null || sourceEntityIds.isEmpty()) {
                throw new ReportServiceException(
                        ReportErrorCode.INVALID_ENTITY_SCOPE,
                        "No entities were resolved for fixed entity report scope");
            }

            TenantId tenantId = template.getTenantId();

            for (EntityId entityId : sourceEntityIds) {
                result.add(resolveTargetEntity(tenantId, entityId));
            }

            return result;
        }

        throw new ReportServiceException(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                "Report scope type is not yet supported: " + template.getScopeType());
    }

    private ReportTargetEntity resolveTargetEntity(
            TenantId tenantId,
            EntityId entityId) {

        if (tenantId == null
                || entityId == null
                || entityId.getId() == null
                || entityId.getEntityType() == null) {

            throw new ReportServiceException(
                    ReportErrorCode.INVALID_ENTITY_SCOPE,
                    "Invalid report entity reference");
        }

        ReportTargetEntity target = new ReportTargetEntity();

        target.setEntityId(
                entityId.getId());

        target.setEntityType(
                entityId.getEntityType().name());

        String name;
        String label;

        switch (entityId.getEntityType()) {

            case DEVICE:
                Device device = deviceService.findDeviceById(
                        tenantId,
                        new DeviceId(
                                entityId.getId()));

                if (device == null) {
                    throw new ReportServiceException(
                            ReportErrorCode.INVALID_ENTITY_SCOPE,
                            "Report device was not found");
                }

                name = device.getName();

                label = device.getLabel();

                break;

            case ASSET:
                Asset asset = assetService.findAssetById(
                        tenantId,
                        new AssetId(
                                entityId.getId()));

                if (asset == null) {
                    throw new ReportServiceException(
                            ReportErrorCode.INVALID_ENTITY_SCOPE,
                            "Report asset was not found");
                }

                name = asset.getName();

                label = asset.getLabel();

                break;

            default:
                throw new ReportServiceException(
                        ReportErrorCode.INVALID_ENTITY_SCOPE,
                        "Unsupported report entity type: "
                                + entityId.getEntityType());
        }

        target.setName(
                notBlank(name)
                        ? name
                        : entityId.getId().toString());

        target.setLabel(
                notBlank(label)
                        ? label
                        : target.getEntityType());

        return target;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}