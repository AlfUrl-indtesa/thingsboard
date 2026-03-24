package org.thingsboard.server.common.data.export;

import lombok.Data;

import java.util.List;

@Data
public class DataExportPreviewResponse {

    private List<ExportableDeviceInfo> devices;
    private List<String> keys;
    private List<String> attributeKeys;
    private String suggestedEmail;
    private boolean emailRequired;
    private Long defaultStartTs;
    private Long defaultEndTs;

    @Data
    public static class ExportableDeviceInfo {
        private String id;
        private String name;
        private String type;
        private String label;
    }
}