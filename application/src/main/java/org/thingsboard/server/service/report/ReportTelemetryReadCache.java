/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.report.ReportAggregationType;
import org.thingsboard.server.common.data.report.ReportMetricPoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
public class ReportTelemetryReadCache {

    /**
     * Cada generación de reporte mantiene su propio contexto.
     *
     * ThreadLocal es válido en el flujo actual porque la recolección
     * de datos de un reporte ocurre sin cambiar de hilo.
     */
    private final ThreadLocal<Deque<ScopeState>> scopes =
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Abre una caché temporal para la generación de un reporte.
     *
     * Debe utilizarse con try-with-resources.
     */
    public Scope openScope() {
        ScopeState state = new ScopeState();

        scopes.get().push(state);

        return new Scope(
                this,
                state
        );
    }

    /**
     * Obtiene los puntos desde la caché del reporte actual o ejecuta
     * la consulta cuando todavía no existe una entrada equivalente.
     */
    public List<ReportMetricPoint> getOrLoad(
            TenantId tenantId,
            EntityId entityId,
            String key,
            Long startTs,
            Long endTs,
            Long interval,
            Integer limit,
            ReportAggregationType aggregation,
            String orderBy,
            Supplier<List<ReportMetricPoint>> loader) {

        ScopeState state =
                currentScope();

        /*
         * Algunos procesos podrían llamar al lector fuera de la
         * generación normal de reportes. En ese caso se conserva
         * el comportamiento directo anterior.
         */
        if (state == null) {
            return copyPoints(
                    loader.get()
            );
        }

        QueryKey queryKey =
                new QueryKey(
                        tenantId,
                        entityId,
                        key,
                        startTs,
                        endTs,
                        interval,
                        limit,
                        aggregation,
                        orderBy
                );

        List<ReportMetricPoint> cached =
                state.entries.get(queryKey);

        if (cached != null) {
            state.hits++;

            return new ArrayList<>(cached);
        }

        state.misses++;

        List<ReportMetricPoint> loaded =
                immutablePoints(
                        loader.get()
                );

        state.entries.put(
                queryKey,
                loaded
        );

        return new ArrayList<>(loaded);
    }

    private ScopeState currentScope() {
        Deque<ScopeState> stack =
                scopes.get();

        return stack.isEmpty()
                ? null
                : stack.peek();
    }

    private void closeScope(
            ScopeState expectedState) {

        Deque<ScopeState> stack =
                scopes.get();

        if (stack.isEmpty()) {
            scopes.remove();
            return;
        }

        ScopeState currentState =
                stack.peek();

        if (currentState == expectedState) {
            stack.pop();
        } else {
            /*
             * Este caso no debería ocurrir. Se elimina únicamente
             * el contexto esperado para no dejar datos retenidos.
             */
            log.warn(
                    "Report telemetry cache scopes were closed out of order"
            );

            stack.remove(expectedState);
        }

        log.info(
                "Report telemetry cache completed: " +
                        "queries={}, hits={}, misses={}",
                expectedState.entries.size(),
                expectedState.hits,
                expectedState.misses
        );

        if (stack.isEmpty()) {
            scopes.remove();
        }
    }

    private List<ReportMetricPoint> copyPoints(
            List<ReportMetricPoint> points) {

        if (points == null
                || points.isEmpty()) {
            return new ArrayList<>();
        }

        List<ReportMetricPoint> result =
                new ArrayList<>();

        for (ReportMetricPoint point : points) {
            if (point != null) {
                result.add(point);
            }
        }

        return result;
    }

    private List<ReportMetricPoint> immutablePoints(
            List<ReportMetricPoint> points) {

        List<ReportMetricPoint> copied =
                copyPoints(points);

        if (copied.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(
                copied
        );
    }

    public static final class Scope
            implements AutoCloseable {

        private final ReportTelemetryReadCache owner;
        private final ScopeState state;

        private boolean closed;

        private Scope(
                ReportTelemetryReadCache owner,
                ScopeState state) {

            this.owner = owner;
            this.state = state;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }

            closed = true;

            owner.closeScope(state);
        }
    }

    private static final class ScopeState {

        private final Map<QueryKey, List<ReportMetricPoint>> entries =
                new HashMap<>();

        private int hits;
        private int misses;
    }

    private static final class QueryKey {

        private final UUID tenantId;

        private final String entityType;
        private final UUID entityId;

        private final String key;

        private final Long startTs;
        private final Long endTs;

        private final Long interval;
        private final Integer limit;

        private final ReportAggregationType aggregation;

        private final String orderBy;

        private QueryKey(
                TenantId tenantId,
                EntityId entityId,
                String key,
                Long startTs,
                Long endTs,
                Long interval,
                Integer limit,
                ReportAggregationType aggregation,
                String orderBy) {

            this.tenantId =
                    tenantId != null
                            ? tenantId.getId()
                            : null;

            this.entityType =
                    entityId != null
                            && entityId.getEntityType() != null
                            ? entityId.getEntityType().name()
                            : null;

            this.entityId =
                    entityId != null
                            ? entityId.getId()
                            : null;

            this.key =
                    key != null
                            ? key.trim()
                            : null;

            this.startTs = startTs;
            this.endTs = endTs;

            this.interval = interval;
            this.limit = limit;

            this.aggregation =
                    aggregation != null
                            ? aggregation
                            : ReportAggregationType.NONE;

            this.orderBy =
                    normalizeOrderBy(orderBy);
        }

        private static String normalizeOrderBy(
                String orderBy) {

            if (orderBy == null
                    || orderBy.isBlank()) {
                return "ASC";
            }

            return orderBy
                    .trim()
                    .toUpperCase(Locale.ROOT);
        }

        @Override
        public boolean equals(
                Object object) {

            if (this == object) {
                return true;
            }

            if (!(object instanceof QueryKey other)) {
                return false;
            }

            return Objects.equals(
                    tenantId,
                    other.tenantId
            ) && Objects.equals(
                    entityType,
                    other.entityType
            ) && Objects.equals(
                    entityId,
                    other.entityId
            ) && Objects.equals(
                    key,
                    other.key
            ) && Objects.equals(
                    startTs,
                    other.startTs
            ) && Objects.equals(
                    endTs,
                    other.endTs
            ) && Objects.equals(
                    interval,
                    other.interval
            ) && Objects.equals(
                    limit,
                    other.limit
            ) && aggregation == other.aggregation
                    && Objects.equals(
                    orderBy,
                    other.orderBy
            );
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    tenantId,
                    entityType,
                    entityId,
                    key,
                    startTs,
                    endTs,
                    interval,
                    limit,
                    aggregation,
                    orderBy
            );
        }
    }
}