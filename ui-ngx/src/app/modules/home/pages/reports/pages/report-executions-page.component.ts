import { Component, OnInit } from "@angular/core";
import { finalize } from "rxjs/operators";
import { ReportExecution } from "../models/report.models";
import { ReportService } from "../services/report.service";

@Component({
    selector: "tb-report-executions-page",
    standalone: false,
    templateUrl: "./report-executions-page.component.html",
    styleUrls: ["./report-executions-page.component.scss"],
})
export class ReportExecutionsPageComponent implements OnInit {
    displayedColumns = [
        "templateNameSnapshot",
        "status",
        "requestedTime",
        "finishedTime",
        "actions",
    ];
    executions: ReportExecution[] = [];
    loading = false;

    constructor(private reportService: ReportService) {}

    ngOnInit(): void {
        this.loadExecutions();
    }

    loadExecutions(): void {
        this.loading = true;
        this.reportService.getReportExecutions(0, 50)
            .pipe(finalize(() => this.loading = false))
            .subscribe((pageData) => {
                this.executions = pageData.data || pageData.content || [];
            });
    }

    refresh(): void {
        this.loadExecutions();
    }

    downloadExecution(execution: any): void {
        const executionId = this.executionUuid(execution);

        if (!executionId) {
            return;
        }

        this.reportService.downloadReportExecution(executionId).subscribe(
            (blob) => {
                const fileName = execution.fileName || "report.pdf";
                const url = window.URL.createObjectURL(blob);

                const anchor = document.createElement("a");
                anchor.href = url;
                anchor.download = fileName;
                anchor.click();

                window.URL.revokeObjectURL(url);
            },
        );
    }

    deleteExecution(execution: any): void {
        const executionId = this.executionUuid(execution);

        if (!executionId) {
            return;
        }

        if (
            !confirm(
                `¿Eliminar el reporte generado "${
                    execution.fileName || executionId
                }"?`,
            )
        ) {
            return;
        }

        this.reportService.deleteReportExecution(executionId).subscribe(() => {
            this.refresh();
        });
    }
    private executionUuid(execution: any): string | null {
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
