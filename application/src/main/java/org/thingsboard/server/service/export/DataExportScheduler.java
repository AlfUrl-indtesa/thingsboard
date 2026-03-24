package org.thingsboard.server.service.export;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataExportScheduler {

    private final DataExportService dataExportService;

    // Corre cada 5 minutos; el servicio decide qué jobs realmente deben dispararse
    @Scheduled(cron = "0 */5 * * * *")
    public void processSchedules() {
        try {
            dataExportService.runSchedules();
        } catch (Exception e) {
            log.error("Error processing data export schedules", e);
        }
    }
}