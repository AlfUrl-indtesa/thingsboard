package org.thingsboard.server.service.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.report.*;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultReportEntityResolverService implements ReportEntityResolverService {

    @Override
    public List<ReportTargetEntity> resolveEntities(ReportTemplate template, GenerateReportRequest request) {
        List<ReportTargetEntity> result = new ArrayList<>();

        List<EntityId> sourceEntityIds = request.getEntityIds();
        if (sourceEntityIds == null || sourceEntityIds.isEmpty()) {
            sourceEntityIds = template.getEntityFilter().getEntityIds();
        }

        if (template.getScopeType() == ReportScopeType.FIXED_ENTITIES) {
            if (sourceEntityIds == null || sourceEntityIds.isEmpty()) {
                throw new ReportServiceException(
                        ReportErrorCode.INVALID_ENTITY_SCOPE,
                        "No entities were resolved for fixed entity report scope"
                );
            }

            for (EntityId entityId : sourceEntityIds) {
                ReportTargetEntity target = new ReportTargetEntity();
                target.setEntityId(entityId.getId());
                target.setEntityType(entityId.getEntityType().name());
                target.setName(entityId.getId().toString());
                target.setLabel(entityId.getEntityType().name());
                result.add(target);
            }
            return result;
        }

        throw new ReportServiceException(
                ReportErrorCode.INVALID_ENTITY_SCOPE,
                "Report scope type is not yet supported: " + template.getScopeType()
        );
    }
}