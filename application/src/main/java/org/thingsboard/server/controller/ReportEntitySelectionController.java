/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0
 */

package org.thingsboard.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.report.ReportSelectableEntity;
import org.thingsboard.server.service.report.ReportEntitySelectionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReportEntitySelectionController extends BaseController {

    private final ReportEntitySelectionService reportEntitySelectionService;

    @GetMapping("/api/reports/selectable-entities")
    public PageData<ReportSelectableEntity> getSelectableEntities(
            @RequestParam EntityType entityType,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String textSearch,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) throws Exception {

        CustomerId targetCustomerId = customerId != null ? new CustomerId(customerId) : null;

        return reportEntitySelectionService.findSelectableEntities(
                getCurrentUser(),
                entityType,
                targetCustomerId,
                textSearch,
                page,
                pageSize
        );
    }

    @GetMapping("/api/reports/selectable-entity-keys")
    public List<String> getSelectableEntityKeys(
            @RequestParam EntityType entityType,
            @RequestParam UUID entityId) throws Exception {

        return reportEntitySelectionService.findSelectableEntityKeys(
                getCurrentUser(),
                EntityIdFactory.getByTypeAndUuid(entityType, entityId)
        );
    }
}