import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ReportService } from '../services/report.service';
import { ReportTemplate } from '../models/report.models';

@Component({
  selector: 'tb-report-template-dialog',
  templateUrl: './report-template-dialog.component.html',
  styleUrls: ['./report-template-dialog.component.scss']
})
export class ReportTemplateDialogComponent implements OnInit {

  form: FormGroup;

  constructor(
    private fb: FormBuilder,
    private reportService: ReportService,
    private dialogRef: MatDialogRef<ReportTemplateDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public template: ReportTemplate | null
  ) {
    this.form = this.fb.group({
      name: ['', Validators.required],
      description: [''],
      type: ['COMPRESSED_AIR', Validators.required],
      status: ['ACTIVE', Validators.required],
      scopeType: ['FIXED_ENTITIES', Validators.required],
      entityType: ['DEVICE'],
      entityIdsJson: ['[]', Validators.required],
      companyName: ['Eficentra'],
      footerText: ['Reporte generado por Eficentra']
    });
  }

  ngOnInit(): void {
    if (this.template) {
      this.form.patchValue({
        name: this.template.name,
        description: this.template.description,
        type: this.template.type,
        status: this.template.status,
        scopeType: this.template.scopeType,
        entityType: this.template.entityFilter?.entityType || 'DEVICE',
        entityIdsJson: JSON.stringify(this.template.entityFilter?.entityIds || [], null, 2),
        companyName: this.template.branding?.companyName || '',
        footerText: this.template.branding?.footerText || ''
      });
    }
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    let entityIds: any[] = [];
    try {
      entityIds = JSON.parse(this.form.value.entityIdsJson || '[]');
    } catch {
      alert('El JSON de entityIds no es válido');
      return;
    }

    const template: ReportTemplate = {
      ...this.template,
      name: this.form.value.name,
      description: this.form.value.description,
      type: this.form.value.type,
      status: this.form.value.status,
      scopeType: this.form.value.scopeType,
      entityFilter: {
        scopeType: this.form.value.scopeType,
        entityType: this.form.value.entityType,
        entityIds
      },
      sections: this.template?.sections?.length ? this.template.sections : [
        {
          key: 'main-kpis',
          type: 'KPI_GRID',
          title: 'Indicadores principales',
          order: 0,
          visible: true,
          pageBreakBefore: false,
          config: {
            items: [
              {
                key: 'pressure',
                label: 'Presión promedio',
                unit: 'psig',
                aggregation: 'AVG',
                combineEntities: true
              }
            ]
          }
        }
      ],
      branding: {
        companyName: this.form.value.companyName,
        footerText: this.form.value.footerText
      },
      outputFormat: 'PDF',
      system: false
    };

    this.reportService.saveReportTemplate(template).subscribe(() => {
      this.dialogRef.close(true);
    });
  }

  close(): void {
    this.dialogRef.close(false);
  }
}