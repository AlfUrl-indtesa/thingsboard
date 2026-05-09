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