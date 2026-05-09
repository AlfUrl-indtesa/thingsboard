package org.thingsboard.server.common.data.report;

import lombok.Data;
import org.thingsboard.server.common.data.id.CustomerId;

@Data
public class ReportSelectableEntitiesRequest {

    /**
     * DEVICE o ASSET.
     */
    private String entityType;

    /**
     * Opcional. Si se envía, intenta limitar a entidades del customer.
     */
    private CustomerId customerId;

    private String textSearch;

    private int page = 0;

    private int pageSize = 50;
}

