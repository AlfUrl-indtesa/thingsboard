/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.common.data.report;

import lombok.Data;

@Data
public class ReportSeriesStatistics {

    /**
     * Indica si existe al menos un valor numérico válido.
     */
    private boolean hasData;

    /**
     * Cantidad total de elementos recibidos, incluyendo puntos
     * nulos o valores no numéricos.
     */
    private int totalPointCount;

    /**
     * Cantidad de valores numéricos finitos utilizados.
     */
    private int validPointCount;

    /**
     * Cantidad de puntos descartados.
     */
    private int invalidPointCount;

    private Double min;
    private Double max;
    private Double avg;
    private Double sum;

    private Double first;
    private Double last;
    private Double delta;

    private Long firstTs;
    private Long lastTs;
}