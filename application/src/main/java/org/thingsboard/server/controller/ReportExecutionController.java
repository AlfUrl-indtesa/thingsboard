package org.thingsboard.server.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.service.report.ReportExecutionService;
import org.thingsboard.server.service.report.ReportStorageService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportExecutionController extends BaseController {

        private final ReportExecutionService reportExecutionService;
        private final ReportStorageService reportStorageService;

        @GetMapping("/report-executions/{executionId}")
        @ResponseBody
        public ReportExecution getReportExecutionById(@PathVariable("executionId") String strExecutionId)
                        throws Exception {
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

                return reportExecutionService.findByTenantId(
                                tenantId,
                                PageRequest.of(page, pageSize));
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

                return reportExecutionService.findByTenantIdAndTemplateId(
                                tenantId,
                                templateId,
                                PageRequest.of(page, pageSize));
        }

        @DeleteMapping("/report-executions/{executionId}")
        public void deleteReportExecution(@PathVariable("executionId") String strExecutionId) throws Exception {
                checkParameter("executionId", strExecutionId);

                TenantId tenantId = getTenantId();
                UUID executionId = UUID.fromString(strExecutionId);

                reportExecutionService.delete(tenantId, executionId);
        }

        @GetMapping("/report-executions/{executionId}/download")
        public void downloadReportExecution(@PathVariable("executionId") String strExecutionId,
                        HttpServletResponse response) throws Exception {
                checkParameter("executionId", strExecutionId);

                TenantId tenantId = getTenantId();
                UUID executionId = UUID.fromString(strExecutionId);

                ReportExecution execution = reportExecutionService.findById(tenantId, executionId);

                if (execution == null) {
                        throw new IllegalArgumentException("Report execution not found.");
                }

                byte[] fileContent = reportStorageService.loadFile(tenantId, execution);

                String fileName = execution.getFileName() != null && !execution.getFileName().isBlank()
                                ? execution.getFileName()
                                : "report.pdf";

                String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                                .replace("+", "%20");

                response.setContentType(
                                execution.getMimeType() != null && !execution.getMimeType().isBlank()
                                                ? execution.getMimeType()
                                                : MediaType.APPLICATION_PDF_VALUE);

                response.setHeader(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename*=UTF-8''" + encodedFileName);

                response.setContentLength(fileContent.length);
                response.getOutputStream().write(fileContent);
                response.flushBuffer();
        }
}