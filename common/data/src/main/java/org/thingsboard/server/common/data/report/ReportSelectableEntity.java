package org.thingsboard.server.common.data.report;

import lombok.Data;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;

@Data
public class ReportSelectableEntity {

    private EntityId id;

    private String name;

    private String label;

    private String type;

    private CustomerId customerId;

    public ReportSelectableEntity() {
    }

    public ReportSelectableEntity(EntityId id, String name, String label, String type, CustomerId customerId) {
        this.id = id;
        this.name = name;
        this.label = label;
        this.type = type;
        this.customerId = customerId;
    }
}