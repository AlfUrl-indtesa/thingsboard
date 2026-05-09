/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0
 */

package org.thingsboard.server.service.report;

import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.report.ReportSelectableEntity;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.List;

public interface ReportEntitySelectionService {

    PageData<ReportSelectableEntity> findSelectableEntities(SecurityUser user,
                                                            EntityType entityType,
                                                            CustomerId customerId,
                                                            String textSearch,
                                                            int page,
                                                            int pageSize);

    List<String> findSelectableEntityKeys(SecurityUser user,
                                          EntityId entityId);
}