package org.thingsboard.server.service.report;

import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.report.ReportVariableMetadata;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class DefaultReportVariableMetadataService implements ReportVariableMetadataService {

    private static final Map<String, ReportVariableMetadata> DEFAULTS = new HashMap<>();

    static {
        register("pressure", "Presión", "psi");
        register("presion", "Presión", "psi");
        register("presión", "Presión", "psi");
        register("presiã³n", "Presión", "psi");

        register("TempRocio", "Punto de rocío", "°C");
        register("temp_rocio", "Punto de rocío", "°C");
        register("dew_point", "Punto de rocío", "°C");

        register("temperature", "Temperatura", "°C");
        register("temp", "Temperatura", "°C");
        register("ams_temperature_c", "Temperatura", "°C");

        register("humidity", "Humedad", "%");
        register("humedad", "Humedad", "%");

        register("flow", "Flujo", "L/min");
        register("instant_flow", "Flujo instantáneo", "L/min");
        register("ams_instant_flow_lpm", "Flujo instantáneo", "L/min");

        register("cumulative_flow", "Consumo acumulado", "L");
        register("ams_cumulative_flow_l", "Consumo acumulado", "L");

        register("power", "Potencia", "kW");
        register("active_power", "Potencia activa", "kW");
        register("energy", "Energía", "kWh");
        register("voltage", "Voltaje", "V");
        register("current", "Corriente", "A");
        register("frequency", "Frecuencia", "Hz");

        register("vibration", "Vibración", "mm/s");
        register("rpm", "Velocidad", "rpm");
    }

    private static void register(String key, String label, String unit) {
        DEFAULTS.put(normalize(key), new ReportVariableMetadata(key, label, unit));
    }

    @Override
    public ReportVariableMetadata resolve(String key, String providedLabel, String providedUnit) {
        String safeKey = key != null ? key : "";

        ReportVariableMetadata fallback = DEFAULTS.get(normalize(safeKey));

        String label = firstNotBlank(
                providedLabel,
                fallback != null ? fallback.getLabel() : null,
                humanizeKey(safeKey)
        );

        String unit = firstNotBlank(
                providedUnit,
                fallback != null ? fallback.getUnit() : null,
                ""
        );

        return new ReportVariableMetadata(safeKey, label, unit);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private static String humanizeKey(String key) {
        if (key == null || key.isBlank()) {
            return "Variable";
        }

        String text = key
                .replace("_", " ")
                .replace("-", " ")
                .trim();

        if (text.isBlank()) {
            return key;
        }

        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}