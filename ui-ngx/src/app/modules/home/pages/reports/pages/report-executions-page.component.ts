import { Component, OnInit } from "@angular/core";
import { finalize } from "rxjs/operators";
import { ReportExecution } from "../models/report.models";
import { ReportService } from "../services/report.service";

@Component({
    selector: "tb-report-executions-page",
    templateUrl: "./report-executions-page.component.html",
    styleUrls: ["./report-executions-page.component.scss"],
})
export class ReportExecutionsPageComponent implements OnInit {
    displayedColumns = [
        "templateNameSnapshot",
        "status",
        "requestedTime",
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

    download(execution: ReportExecution): void {
        this.reportService.downloadReport(execution.id).subscribe((blob) => {
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = execution.fileName || "report.pdf";
            a.click();
            window.URL.revokeObjectURL(url);
        });
    }
}
