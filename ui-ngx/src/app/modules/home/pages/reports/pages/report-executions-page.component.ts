///
/// Copyright © 2016-2026 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
///

import { Component, OnDestroy, OnInit } from "@angular/core";
import { Subscription, timer } from "rxjs";
import { finalize, switchMap } from "rxjs/operators";

import { ReportExecution } from "../models/report.models";

import { ReportService } from "../services/report.service";

@Component({
    selector: "tb-report-executions-page",
    standalone: false,
    templateUrl: "./report-executions-page.component.html",
    styleUrls: [
        "./report-executions-page.component.scss",
    ],
})
export class ReportExecutionsPageComponent implements OnInit, OnDestroy {
    displayedColumns = [
        "templateNameSnapshot",
        "status",
        "requestedTime",
        "finishedTime",
        "actions",
    ];

    executions: ReportExecution[] = [];

    loading = false;

    private executionPollingSubscription?: Subscription;

    private readonly executionPollingIntervalMs = 2000;

    constructor(
        private reportService: ReportService,
    ) {
    }

    ngOnInit(): void {
        this.loadExecutions();
    }

    ngOnDestroy(): void {
        this.stopExecutionPolling();
    }

    loadExecutions(
        showLoading = true,
    ): void {
        if (showLoading) {
            this.loading = true;
        }

        this.reportService
            .getReportExecutions(
                0,
                50,
            )
            .pipe(
                finalize(() => {
                    if (showLoading) {
                        this.loading = false;
                    }
                }),
            )
            .subscribe({
                next: (pageData) => {
                    this.executions = pageData.data ||
                        pageData.content ||
                        [];

                    this.syncExecutionPolling();
                },
                error: () => {
                    this.stopExecutionPolling();
                },
            });
    }

    refresh(): void {
        this.loadExecutions();
    }

    private syncExecutionPolling(): void {
        if (this.hasActiveExecutions()) {
            this.startExecutionPolling();
        } else {
            this.stopExecutionPolling();
        }
    }

    private startExecutionPolling(): void {
        if (
            this.executionPollingSubscription &&
            !this.executionPollingSubscription.closed
        ) {
            return;
        }

        this.executionPollingSubscription = timer(
            this.executionPollingIntervalMs,
            this.executionPollingIntervalMs,
        )
            .pipe(
                switchMap(() =>
                    this.reportService
                        .getReportExecutions(
                            0,
                            50,
                        )
                ),
            )
            .subscribe({
                next: (pageData) => {
                    this.executions = pageData.data ||
                        pageData.content ||
                        [];

                    if (!this.hasActiveExecutions()) {
                        this.stopExecutionPolling();
                    }
                },
                error: () => {
                    this.stopExecutionPolling();
                },
            });
    }

    private stopExecutionPolling(): void {
        if (this.executionPollingSubscription) {
            this.executionPollingSubscription
                .unsubscribe();

            this.executionPollingSubscription = undefined;
        }
    }

    private hasActiveExecutions(): boolean {
        return this.executions.some(
            (execution) =>
                execution?.status === "PENDING" ||
                execution?.status === "RUNNING",
        );
    }

    downloadExecution(
        execution: any,
    ): void {
        const executionId = this.executionUuid(
            execution,
        );

        if (!executionId) {
            return;
        }

        this.reportService
            .downloadReportExecution(
                executionId,
            )
            .subscribe((blob) => {
                const fileName = execution.fileName ||
                    "report.pdf";

                const url = window.URL
                    .createObjectURL(blob);

                const anchor = document.createElement(
                    "a",
                );

                anchor.href = url;
                anchor.download = fileName;

                anchor.click();

                window.URL.revokeObjectURL(
                    url,
                );
            });
    }

    deleteExecution(
        execution: any,
    ): void {
        const executionId = this.executionUuid(
            execution,
        );

        if (!executionId) {
            return;
        }

        if (
            !confirm(
                `¿Eliminar el reporte generado "${
                    execution.fileName ||
                    executionId
                }"?`,
            )
        ) {
            return;
        }

        this.reportService
            .deleteReportExecution(
                executionId,
            )
            .subscribe(() => {
                this.refresh();
            });
    }

    private executionUuid(
        execution: any,
    ): string | null {
        const id: any = execution?.id;

        if (!id) {
            return null;
        }

        if (typeof id === "string") {
            return id;
        }

        if (id.id) {
            return id.id;
        }

        return null;
    }
}
