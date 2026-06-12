import { Component, OnInit } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { finalize } from "rxjs/operators";
import { ReportTemplate } from "../models/report.models";
import { ReportService } from "../services/report.service";
import { ReportTemplateDialogComponent } from "../components/report-template-dialog.component";
import { GenerateReportDialogComponent } from "../components/generate-report-dialog.component";

@Component({
  selector: "tb-reports-page",
  standalone: false,
  templateUrl: "./reports-page.component.html",
  styleUrls: ["./reports-page.component.scss"],
})
export class ReportsPageComponent implements OnInit {
  displayedColumns = ["name", "type", "status", "actions"];
  executionDisplayedColumns = ["status", "fileName", "createdTime", "actions"];

  templates: ReportTemplate[] = [];
  executions: any[] = [];

  loading = false;
  loadingExecutions = false;

  constructor(
    private reportService: ReportService,
    private dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loadTemplates();
    this.loadExecutions();
  }

  loadTemplates(): void {
    this.loading = true;

    this.reportService.getReportTemplates(0, 50)
      .pipe(finalize(() => this.loading = false))
      .subscribe((pageData) => {
        this.templates = pageData.data || pageData.content || [];
      });
  }

  loadExecutions(): void {
    this.loadingExecutions = true;

    this.reportService.getReportExecutions(0, 20)
      .pipe(finalize(() => this.loadingExecutions = false))
      .subscribe((pageData) => {
        this.executions = pageData.data || pageData.content || [];
      });
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open(ReportTemplateDialogComponent, {
      width: "900px",
      data: {},
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.refresh();
      }
    });
  }

  openEditDialog(template: ReportTemplate): void {
    const dialogRef = this.dialog.open(ReportTemplateDialogComponent, {
      width: "900px",
      data: {
        template,
      },
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.refresh();
      }
    });
  }

  openGenerateDialog(template: ReportTemplate): void {
    const dialogRef = this.dialog.open(GenerateReportDialogComponent, {
      width: "600px",
      data: template,
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.refresh();
      }
    });
  }

  deleteTemplate(template: ReportTemplate): void {
    const templateId = this.templateUuid(template);

    if (!templateId) {
      return;
    }

    if (!confirm(`¿Eliminar el reporte "${template.name}"?`)) {
      return;
    }

    this.reportService.deleteReportTemplate(templateId).subscribe(() => {
      this.refresh();
    });
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
        `¿Eliminar el reporte generado "${execution.fileName || executionId}"?`,
      )
    ) {
      return;
    }

    this.reportService.deleteReportExecution(executionId).subscribe(() => {
      this.refresh();
    });
  }

  private templateUuid(template: ReportTemplate): string | null {
    const id: any = template?.id;

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

  executionsForTemplate(template: ReportTemplate): any[] {
    const templateId = this.templateUuid(template);

    if (!templateId) {
      return [];
    }

    return this.executions.filter((execution) => {
      const executionTemplateId = this.executionTemplateUuid(execution);
      return executionTemplateId === templateId;
    });
  }

  private executionTemplateUuid(execution: any): string | null {
    const id: any = execution?.templateId;

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
