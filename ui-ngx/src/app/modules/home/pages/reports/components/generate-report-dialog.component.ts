import { Component, Inject } from "@angular/core";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { ReportTemplate } from "../models/report.models";
import { ReportService } from "../services/report.service";

@Component({
  selector: "tb-generate-report-dialog",
  standalone: false,
  templateUrl: "./generate-report-dialog.component.html",
  styleUrls: ["./generate-report-dialog.component.scss"],
})
export class GenerateReportDialogComponent {
  form: FormGroup;

  constructor(
    private fb: FormBuilder,
    private reportService: ReportService,
    private dialogRef: MatDialogRef<GenerateReportDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public template: ReportTemplate,
  ) {
    const now = new Date();
    const weekAgo = new Date();
    weekAgo.setDate(now.getDate() - 7);

    this.form = this.fb.group({
      startTs: [weekAgo.toISOString().slice(0, 16), Validators.required],
      endTs: [now.toISOString().slice(0, 16), Validators.required],
      timezone: ["America/Monterrey"],
      locale: ["es-MX"],
    });
  }

  generate(): void {
    const templateId = this.templateUuid();

    if (this.form.invalid || !templateId) {
      this.form.markAllAsTouched();
      return;
    }

    const request = {
      startTs: new Date(this.form.value.startTs).getTime(),
      endTs: new Date(this.form.value.endTs).getTime(),
      timezone: this.form.value.timezone,
      locale: this.form.value.locale,
    };

    this.reportService.generateReport(templateId, request).subscribe(() => {
      this.dialogRef.close(true);
    });
  }

  close(): void {
    this.dialogRef.close(false);
  }

  private templateUuid(): string | null {
    const id: any = this.template?.id;

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
