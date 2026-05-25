package org.thingsboard.server.common.data.report;

public enum ReportSectionType {

    /**
     * Secciones base / layout.
     */
    COVER,
    TEXT_BLOCK,
    KPI_GRID,
    CHART,
    TABLE,
    ALARM_LIST,
    ENTITY_SUMMARY,
    IMAGE_BLOCK,
    BULLET_LIST,
    TWO_COLUMN,

    /**
     * Secciones analíticas para reportes PDF técnicos.
     */
    EXECUTIVE_SUMMARY,
    DATA_QUALITY,
    GENERAL_STATISTICS,
    TIME_SERIES_CHART,
    DAILY_PERFORMANCE,
    DAILY_CHARTS,
    ALARM_SUMMARY,
    CONCLUSION
}