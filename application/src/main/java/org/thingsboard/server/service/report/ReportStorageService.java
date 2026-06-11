package org.thingsboard.server.service.report;

import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportExecution;

public interface ReportStorageService {

    ReportExecution storeGeneratedFile(TenantId tenantId,
                                       ReportExecution execution,
                                       byte[] content,
                                       String fileName,
                                       String mimeType);

    byte[] loadFile(TenantId tenantId, ReportExecution execution);

    boolean exists(TenantId tenantId, ReportExecution execution);

    void deleteFile(TenantId tenantId, ReportExecution execution);
}