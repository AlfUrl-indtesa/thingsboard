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