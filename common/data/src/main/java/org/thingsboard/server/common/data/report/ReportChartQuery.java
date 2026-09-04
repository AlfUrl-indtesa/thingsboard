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
public class ReportChartQuery {
    private String key;

    private String label;

    private String unit;

    /**
     * Whether to merge all selected entities into a single logical chart group.
     * The actual rendering layer may decide how to display the returned series.
     */
    private Boolean combineEntities = Boolean.FALSE;

    /**
     * Telemetry aggregation to use at query time.
     * Example: NONE, AVG, MIN, MAX, SUM, COUNT
     */
    private ReportAggregationType aggregation = ReportAggregationType.NONE;

    /**
     * Optional interval in milliseconds for aggregated queries.
     */
    private Long interval;

    /**
     * Optional maximum number of returned points.
     */
    private Integer limit;

    /**
     * ASC or DESC.
     */
    private String orderBy = "ASC";
}
