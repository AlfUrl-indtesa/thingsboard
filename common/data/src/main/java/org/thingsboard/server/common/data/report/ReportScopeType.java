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

public enum ReportScopeType {

    /**
     * Reporte sobre entidades seleccionadas explícitamente.
     * Ejemplo: Device A, Device B, Device C.
     */
    FIXED_ENTITIES,

    /**
     * Reporte sobre todas las entidades del tenant.
     * Sólo debe permitirse para TENANT_ADMIN.
     */
    TENANT_ENTITIES,

    /**
     * Reporte sobre todas las entidades de un customer específico.
     * Para TENANT_ADMIN permite elegir customer.
     * Para CUSTOMER_USER sólo debe permitir su propio customer.
     */
    CUSTOMER_ENTITIES,

    /**
     * Reporte sobre todas las entidades accesibles para el customer autenticado.
     * Pensado principalmente para CUSTOMER_USER.
     */
    CURRENT_CUSTOMER_ENTITIES
}
