/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.Objects;

@Service
public class ReportAccessService {

    public boolean isTenantAdmin(
            SecurityUser user) {

        return user != null
                && Authority.TENANT_ADMIN.equals(
                        user.getAuthority());
    }

    public boolean isCustomerUser(
            SecurityUser user) {

        return user != null
                && Authority.CUSTOMER_USER.equals(
                        user.getAuthority());
    }

    public void checkTemplateRead(
            SecurityUser user,
            ReportTemplate template) {

        if (user == null
                || template == null
                || template.getTenantId() == null
                || !Objects.equals(
                        user.getTenantId(),
                        template.getTenantId())) {
            deny();
        }

        if (isTenantAdmin(user)) {
            return;
        }

        if (isCustomerUser(user)
                && user.getCustomerId() != null
                && Objects.equals(
                        user.getCustomerId(),
                        template.getCustomerId())) {
            return;
        }

        deny();
    }

    public void checkExecutionRead(
            SecurityUser user,
            ReportExecution execution) {

        if (user == null
                || execution == null
                || execution.getTenantId() == null
                || !Objects.equals(
                        user.getTenantId(),
                        execution.getTenantId())) {
            deny();
        }

        if (isTenantAdmin(user)) {
            return;
        }

        if (isCustomerUser(user)
                && user.getCustomerId() != null
                && Objects.equals(
                        user.getCustomerId(),
                        execution.getCustomerId())
                && execution.getRequestedBy() != null
                && user.getId() != null
                && Objects.equals(
                        user.getId().getId(),
                        execution.getRequestedBy())) {
            return;
        }

        deny();
    }

    public void checkExecutionDelete(
            SecurityUser user,
            ReportExecution execution) {

        checkExecutionRead(
                user,
                execution);
    }

    private void deny() {
        throw new AccessDeniedException(
                "You don't have permission to access this report resource.");
    }
}