package main.java.org.thingsboard.server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.thingsboard.server.common.data.export.DataExportPreviewRequest;
import org.thingsboard.server.common.data.export.DataExportPreviewResponse;
import org.thingsboard.server.common.data.export.DataExportRequest;
import org.thingsboard.server.common.data.export.DataExportScheduleRequest;
import org.thingsboard.server.service.export.DataExportService;
import org.thingsboard.server.service.security.model.SecurityUser;

@RestController
@RequestMapping("/api/data-export")
@RequiredArgsConstructor
@Slf4j
public class DataExportController extends BaseController {

    private final DataExportService dataExportService;

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @PostMapping("/preview")
    public DataExportPreviewResponse preview(@RequestBody DataExportPreviewRequest request) throws Exception {
        SecurityUser user = getCurrentUser();
        return dataExportService.preview(user, request);
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @PostMapping("/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv(@RequestBody DataExportRequest request) throws Exception {
        SecurityUser user = getCurrentUser();

        StreamingResponseBody stream = outputStream -> {
            dataExportService.writeCsv(user, request, outputStream);
        };

        String fileName = "thingsboard-export-" + System.currentTimeMillis() + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(stream);
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @PostMapping("/schedule")
    public ResponseEntity<Void> saveSchedule(@RequestBody DataExportScheduleRequest request) throws Exception {
        SecurityUser user = getCurrentUser();
        dataExportService.saveSchedule(user, request);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping("/schedule")
    public Object getSchedule() throws Exception {
        SecurityUser user = getCurrentUser();
        return dataExportService.getSchedule(user);
    }
}