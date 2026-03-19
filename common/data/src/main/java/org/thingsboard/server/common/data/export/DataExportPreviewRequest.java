package main.java.org.thingsboard.server.common.data.export;

import lombok.Data;

import java.util.List;

@Data
public class DataExportPreviewRequest {
    private List<String> deviceIds;
    private boolean includeCalculatedFields = true;
    private boolean includeAttributes = true;
}