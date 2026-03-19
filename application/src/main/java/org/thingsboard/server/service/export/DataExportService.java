package main.java.org.thingsboard.server.service.export;

import org.thingsboard.server.common.data.export.DataExportPreviewRequest;
import org.thingsboard.server.common.data.export.DataExportPreviewResponse;
import org.thingsboard.server.common.data.export.DataExportRequest;
import org.thingsboard.server.common.data.export.DataExportSchedule;
import org.thingsboard.server.common.data.export.DataExportScheduleRequest;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.io.OutputStream;

public interface DataExportService {

    DataExportPreviewResponse preview(SecurityUser user, DataExportPreviewRequest request) throws Exception;

    void writeCsv(SecurityUser user, DataExportRequest request, OutputStream outputStream) throws Exception;

    void saveSchedule(SecurityUser user, DataExportScheduleRequest request) throws Exception;

    DataExportSchedule getSchedule(SecurityUser user) throws Exception;

    void runSchedules() throws Exception;
}