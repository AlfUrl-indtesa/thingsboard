/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.report;

import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.report.ReportVariableMetadata;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class DefaultReportVariableMetadataService implements ReportVariableMetadataService {

    private static final Map<String, MetadataDefinition> DEFAULTS = new HashMap<>();

    static {
        register("Presión", "psi",
                "pressure",
                "pressure_psi",
                "presion",
                "presión",
                "presiã³n",
                "presiÃ³n",
                "Bytes_Pressure",
                "Ch_A1_A1a_psi",
                "psi");

        register("Decimal", "",
                "decimal");

        register("Unidad", "",
                "unidad",
                "unidades",
                "Unidades");

        register("Punto de rocío", "°C",
                "TempRocio",
                "temp_rocio",
                "temperatura_rocio",
                "dew_point",
                "dewpoint");

        register("Punto de rocío / escarcha", "°C",
                "TempRocio/Escarcha",
                "temp_rocio_escarcha",
                "dew_frost_point");

        register("Punto de rocío / escarcha a 1 atm", "°C",
                "TempRocio/Escarcha1atm",
                "temp_rocio_escarcha_1atm",
                "dew_frost_point_1atm");

        register("Agua en PPM", "ppm",
                "AguaPPM",
                "agua_ppm",
                "water_ppm",
                "h2o_ppm");

        register("Temperatura", "°C",
                "temperature",
                "temperature_c",
                "temp",
                "temp_c",
                "ams_temperature_c");

        register("Humedad", "%",
                "humidity",
                "humedad",
                "relative_humidity",
                "rh");

        register("Flujo", "L/min",
                "flow",
                "flow_lpm",
                "flujo");

        register("Flujo instantáneo", "L/min",
                "instant_flow",
                "instant_flow_lpm",
                "flujo_instantaneo",
                "flujo_instantáneo",
                "ams_instant_flow_lpm");

        register("Consumo acumulado", "L",
                "cumulative_flow",
                "cumulative_flow_l",
                "flujo_acumulado",
                "consumo_acumulado",
                "ams_cumulative_flow_l");

        register("Potencia", "kW",
                "power",
                "power_kw",
                "potencia");

        register("Potencia activa", "kW",
                "active_power",
                "active_power_kw",
                "potencia_activa");

        register("Energía", "kWh",
                "energy",
                "energy_kwh",
                "energia",
                "energía");

        register("Voltaje", "V",
                "voltage",
                "voltaje");

        register("Corriente", "A",
                "current",
                "corriente");

        register("Frecuencia", "Hz",
                "frequency",
                "frecuencia");

        register("Vibración", "mm/s",
                "vibration",
                "vibracion",
                "vibración");

        register("Velocidad", "rpm",
                "rpm",
                "speed",
                "velocidad");
    }

    @Override
    public ReportVariableMetadata resolve(String key, String providedLabel, String providedUnit) {
        String safeKey = key != null ? key.trim() : "";

        MetadataDefinition fallback = DEFAULTS.get(normalizeLookupKey(safeKey));

        String label = resolveLabel(safeKey, providedLabel, fallback);
        String unit = resolveUnit(providedUnit, fallback);

        return new ReportVariableMetadata(safeKey, label, unit);
    }

    private static void register(String label, String unit, String... aliases) {
        MetadataDefinition definition = new MetadataDefinition(label, unit);

        if (aliases == null) {
            return;
        }

        for (String alias : aliases) {
            DEFAULTS.put(normalizeLookupKey(alias), definition);
        }
    }

    private static String resolveLabel(String key, String providedLabel, MetadataDefinition fallback) {
        String cleanedProvidedLabel = cleanProvidedLabel(providedLabel);

        if (cleanedProvidedLabel != null) {
            MetadataDefinition labelDefinition = DEFAULTS.get(normalizeLookupKey(cleanedProvidedLabel));

            if (labelDefinition != null) {
                return labelDefinition.label;
            }

            if (!looksTechnical(cleanedProvidedLabel)) {
                return cleanedProvidedLabel;
            }
        }

        if (fallback != null && !isBlank(fallback.label)) {
            return fallback.label;
        }

        return humanizeKey(key);
    }

    private static String resolveUnit(String providedUnit, MetadataDefinition fallback) {
        if (!isBlank(providedUnit)) {
            return providedUnit.trim();
        }

        if (fallback != null && fallback.unit != null) {
            return fallback.unit;
        }

        return "";
    }

    private static String cleanProvidedLabel(String label) {
        if (isBlank(label)) {
            return null;
        }

        String cleaned = fixCommonEncodingIssues(label.trim())
                .replace("_", " ")
                .replace("-", " ")
                .replaceAll("\\s+", " ")
                .trim();

        cleaned = removeAggregationPrefix(cleaned);

        if (isBlank(cleaned)) {
            return null;
        }

        return cleaned;
    }

    private static String normalizeLookupKey(String value) {
        if (value == null) {
            return "";
        }

        String normalized = fixCommonEncodingIssues(value.trim());

        normalized = removeAggregationSuffix(normalized);
        normalized = removeDevicePrefix(normalized);

        normalized = normalized
                .replace("-", "_")
                .replace(" ", "_")
                .trim()
                .toLowerCase(Locale.ROOT);

        normalized = stripAccents(normalized);

        return normalized;
    }

    private static String removeAggregationSuffix(String key) {
        if (key == null) {
            return "";
        }

        String result = key.trim();

        String[] suffixes = {
                ".avg",
                ".min",
                ".max",
                ".sum",
                ".count",
                ".first",
                ".last",
                ".none",
                "_avg",
                "_min",
                "_max",
                "_sum",
                "_count",
                "_first",
                "_last",
                "_none"
        };

        String lower = result.toLowerCase(Locale.ROOT);

        for (String suffix : suffixes) {
            if (lower.endsWith(suffix)) {
                return result.substring(0, result.length() - suffix.length());
            }
        }

        return result;
    }

    private static String removeDevicePrefix(String key) {
        if (key == null) {
            return "";
        }

        String result = key.trim();

        int dotIndex = result.lastIndexOf('.');

        if (dotIndex >= 0 && dotIndex < result.length() - 1) {
            return result.substring(dotIndex + 1);
        }

        return result;
    }

    private static String removeAggregationPrefix(String label) {
        if (label == null) {
            return "";
        }

        String result = label.trim();

        String[][] prefixes = {
                { "Promedio de ", "" },
                { "Promedio ", "" },
                { "Mínimo de ", "" },
                { "Mínimo ", "" },
                { "Minimo de ", "" },
                { "Minimo ", "" },
                { "Máximo de ", "" },
                { "Máximo ", "" },
                { "Maximo de ", "" },
                { "Maximo ", "" },
                { "Suma de ", "" },
                { "Suma ", "" },
                { "Conteo de ", "" },
                { "Conteo ", "" },
                { "Último de ", "" },
                { "Último ", "" },
                { "Ultimo de ", "" },
                { "Ultimo ", "" },
                { "Primero de ", "" },
                { "Primero ", "" },
                { "Average of ", "" },
                { "Average ", "" },
                { "Minimum of ", "" },
                { "Minimum ", "" },
                { "Maximum of ", "" },
                { "Maximum ", "" },
                { "Sum of ", "" },
                { "Sum ", "" },
                { "Count of ", "" },
                { "Count ", "" },
                { "Last of ", "" },
                { "Last ", "" },
                { "First of ", "" },
                { "First ", "" }
        };

        for (String[] prefix : prefixes) {
            if (startsWithIgnoreCase(result, prefix[0])) {
                return result.substring(prefix[0].length()).trim();
            }
        }

        return result;
    }

    private static boolean looksTechnical(String value) {
        if (isBlank(value)) {
            return false;
        }

        String normalized = value.trim();

        if (normalized.contains(".")) {
            return true;
        }

        if (normalized.contains("_")) {
            return true;
        }

        if (normalized.matches(".*[A-Za-z]+[0-9]+.*")) {
            return true;
        }

        String lookupKey = normalizeLookupKey(normalized);

        return DEFAULTS.containsKey(lookupKey);
    }

    private static String humanizeKey(String key) {
        if (isBlank(key)) {
            return "Variable";
        }

        String normalized = fixCommonEncodingIssues(key.trim());

        normalized = removeAggregationSuffix(normalized);
        normalized = removeDevicePrefix(normalized);

        MetadataDefinition definition = DEFAULTS.get(normalizeLookupKey(normalized));

        if (definition != null && !isBlank(definition.label)) {
            return definition.label;
        }

        String text = normalized
                .replace("_", " ")
                .replace("-", " ")
                .replace("/", " / ")
                .replaceAll("\\s+", " ")
                .trim();

        if (isBlank(text)) {
            return "Variable";
        }

        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String fixCommonEncodingIssues(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("Ã¡", "á")
                .replace("Ã©", "é")
                .replace("Ã­", "í")
                .replace("Ã³", "ó")
                .replace("Ãº", "ú")
                .replace("Ã±", "ñ")
                .replace("Ã", "Á")
                .replace("Ã‰", "É")
                .replace("Ã", "Í")
                .replace("Ã“", "Ó")
                .replace("Ãš", "Ú")
                .replace("Ã‘", "Ñ")
                .replace("ã¡", "á")
                .replace("ã©", "é")
                .replace("ã­", "í")
                .replace("ã³", "ó")
                .replace("ãº", "ú")
                .replace("ã±", "ñ");
    }

    private static String stripAccents(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);

        return normalized.replaceAll("\\p{M}", "");
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        if (value == null || prefix == null) {
            return false;
        }

        return value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class MetadataDefinition {
        private final String label;
        private final String unit;

        private MetadataDefinition(String label, String unit) {
            this.label = label;
            this.unit = unit;
        }
    }
}