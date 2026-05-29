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
  templates: ReportTemplate[] = [];
  loading = false;

  constructor(
    private reportService: ReportService,
    private dialog: MatDialog,
  ) {}

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

  ngOnInit(): void {
    this.loadTemplates();
  }

  loadTemplates(): void {
    this.loading = true;
    this.reportService.getReportTemplates(0, 50)
      .pipe(finalize(() => this.loading = false))
      .subscribe((pageData) => {
        this.templates = pageData.data || pageData.content || [];
      });
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open(ReportTemplateDialogComponent, {
      width: "900px",
      data: {},
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.loadTemplates();
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
        this.loadTemplates();
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
        this.loadTemplates();
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
      this.loadTemplates();
    });
  }
}
