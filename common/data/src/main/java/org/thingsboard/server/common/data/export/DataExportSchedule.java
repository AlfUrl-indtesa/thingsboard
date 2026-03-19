package main.java.org.thingsboard.server.common.data.export;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class DataExportSchedule {
    private UUID id;
    private UUID tenantId;
    private UUID userId;

    private boolean enabled;
    private boolean allDevices;
    private List<String> deviceIds;
    private List<String> keys;
    private List<String> attributeKeys;

    private boolean includeCalculatedFields;
    private boolean includeAttributes;

    private String email;
    private String period;
    private String timeOfDay;
    private String timezone;
    private String mode;

    private Long lastSuccessTs;
    private Long createdTime;
}