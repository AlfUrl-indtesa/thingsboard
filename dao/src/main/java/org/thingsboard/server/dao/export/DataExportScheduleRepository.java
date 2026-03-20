package org.thingsboard.server.dao.export;

import org.thingsboard.server.common.data.export.DataExportSchedule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataExportScheduleRepository {

    Optional<DataExportSchedule> findByUserId(UUID userId);

    List<DataExportSchedule> findAllEnabledWeekly();

    DataExportSchedule save(DataExportSchedule schedule);
}