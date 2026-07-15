/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.common.data.report;

import lombok.Data;

@Data
public class ReportKpi {

    /**
     * Clave original de telemetría.
     * Ejemplo: flow_lpm
     */
    private String key;

    /**
     * Etiqueta visible configurada por el usuario.
     * Ejemplo: Flujo instantáneo
     */
    private String label;

    /**
     * Nombre de la entidad correspondiente al KPI.
     * Ejemplo: Thermostat T1
     *
     * Puede ser null cuando el KPI combina varias entidades.
     */
    private String entityName;

    /**
     * Operación utilizada para calcular el KPI.
     * Ejemplo: AVG, MIN, MAX, SUM.
     */
    private ReportAggregationType aggregation;

    private Double value;

    private String formattedValue;

    private String unit;

    private String status;
}