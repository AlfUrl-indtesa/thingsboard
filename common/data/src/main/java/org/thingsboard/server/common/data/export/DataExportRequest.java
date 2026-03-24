package org.thingsboard.server.common.data.export;

import lombok.Data;

import java.util.List;

@Data
public class DataExportRequest {
    private List<String> deviceIds;
    private List<String> keys;
    private List<String> attributeKeys;
    private boolean includeCalculatedFields = true;
    private boolean includeAttributes = true;
    private Long startTs;
    private Long endTs;
    private boolean autoDetectOldestTs = true;
    private String format = "csv";
}