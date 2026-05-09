package org.thingsboard.server.common.data.report;

import lombok.Data;

import java.util.UUID;

@Data
public class ReportTargetEntity {

    private UUID entityId;
    private String entityType;
    private String name;
    private String label;
}

