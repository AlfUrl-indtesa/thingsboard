import { Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { finalize } from 'rxjs/operators';
import { ReportTemplate } from '../models/report.models';
import { ReportService } from '../services/report.service';
import { ReportTemplateDialogComponent } from '../components/report-template-dialog.component';
import { GenerateReportDialogComponent } from '../components/generate-report-dialog.component';

@Component({
  selector: 'tb-reports-page',
  templateUrl: './reports-page.component.html',
  styleUrls: ['./reports-page.component.scss']
})
export class ReportsPageComponent implements OnInit {

  displayedColumns = ['name', 'type', 'status', 'actions'];
  templates: ReportTemplate[] = [];
  loading = false;

  constructor(
    private reportService: ReportService,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.loadTemplates();
  }

  loadTemplates(): void {
    this.loading = true;
    this.reportService.getReportTemplates(0, 50)
      .pipe(finalize(() => this.loading = false))
      .subscribe(pageData => {
        this.templates = pageData.data || pageData.content || [];
      });
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open(ReportTemplateDialogComponent, {
      width: '900px',
      data: null
    });

    dialogRef.afterClosed().subscribe(saved => {
      if (saved) {
        this.loadTemplates();
      }
    });
  }

  openEditDialog(template: ReportTemplate): void {
    const dialogRef = this.dialog.open(ReportTemplateDialogComponent, {
      width: '900px',
      data: template
    });

    dialogRef.afterClosed().subscribe(saved => {
      if (saved) {
        this.loadTemplates();
      }
    });
  }

  openGenerateDialog(template: ReportTemplate): void {
    const dialogRef = this.dialog.open(GenerateReportDialogComponent, {
      width: '500px',
      data: template
    });

    dialogRef.afterClosed().subscribe(done => {
      if (done) {
        this.loadTemplates();
      }
    });
  }

  deleteTemplate(template: ReportTemplate): void {
    if (!template.id) {
      return;
    }
    if (!confirm(`¿Eliminar la plantilla "${template.name}"?`)) {
      return;
    }

    this.reportService.deleteReportTemplate(template.id).subscribe(() => {
      this.loadTemplates();
    });
  }
}