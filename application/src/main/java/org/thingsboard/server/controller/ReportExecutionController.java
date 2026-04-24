package org.thingsboard.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.service.report.ReportExecutionService;
import org.thingsboard.server.service.report.ReportStorageService;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportExecutionController extends BaseController {

    private final ReportExecutionService reportExecutionService;
    private final ReportStorageService reportStorageService;

    @GetMapping("/report-executions/{executionId}")
    @ResponseBody
    public ReportExecution getReportExecutionById(@PathVariable("executionId") String strExecutionId) throws Exception {
        checkParameter("executionId", strExecutionId);
        TenantId tenantId = getTenantId();
        UUID executionId = UUID.fromString(strExecutionId);
        return reportExecutionService.findById(tenantId, executionId);
    }

    @GetMapping("/report-executions")
    @ResponseBody
    public Page<ReportExecution> getReportExecutions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) throws Exception {
        TenantId tenantId = getTenantId();
        return reportExecutionService.findByTenantId(tenantId, PageRequest.of(page, pageSize));
    }

    @GetMapping("/report-executions/template/{templateId}")
    @ResponseBody
    public Page<ReportExecution> getReportExecutionsByTemplateId(
            @PathVariable("templateId") String strTemplateId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) throws Exception {
        checkParameter("templateId", strTemplateId);
        TenantId tenantId = getTenantId();
        UUID templateId = UUID.fromString(strTemplateId);
        return reportExecutionService.findByTenantIdAndTemplateId(tenantId, templateId, PageRequest.of(page, pageSize));
    }

    @GetMapping("/report-executions/{executionId}/download")
    public ResponseEntity<byte[]> downloadReportExecutionFile(
            @PathVariable("executionId") String strExecutionId) throws Exception {
        checkParameter("executionId", strExecutionId);

        TenantId tenantId = getTenantId();
        UUID executionId = UUID.fromString(strExecutionId);

        ReportExecution execution = reportExecutionService.findById(tenantId, executionId);
        byte[] content = reportStorageService.loadFile(tenantId, execution);

        String fileName = execution.getFileName() != null ? execution.getFileName() : "report.pdf";
        String mimeType = execution.getMimeType() != null ? execution.getMimeType() : "application/pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(mimeType))
                .contentLength(content.length)
                .body(content);
    }
}