package org.thingsboard.server.common.data.export;

import lombok.Data;

import java.util.List;

@Data
public class DataExportScheduleRequest {
    private boolean enabled;
    private boolean allDevices;
    private List<String> deviceIds;
    private List<String> keys;
    private List<String> attributeKeys;
    private boolean includeCalculatedFields = true;
    private boolean includeAttributes = true;
    private String email;
    private String period;
    private String timeOfDay;
    private String timezone;
    private String mode;
}