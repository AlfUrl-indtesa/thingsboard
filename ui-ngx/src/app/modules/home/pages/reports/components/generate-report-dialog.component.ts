///
/// Copyright © 2016-2026 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
///

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
  generating = false;

  constructor(
    private fb: FormBuilder,
    private reportService: ReportService,
    private dialogRef: MatDialogRef<GenerateReportDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public template: ReportTemplate,
  ) {
    const now = Date.now();
    const weekAgo = now - 7 * 24 * 60 * 60 * 1000;

    this.form = this.fb.group({
      startTs: [this.toLocalDateTimeInputValue(weekAgo), Validators.required],
      endTs: [this.toLocalDateTimeInputValue(now), Validators.required],
      timezone: [
        Intl.DateTimeFormat().resolvedOptions().timeZone || "America/Monterrey",
      ],
      locale: [navigator.language || "es-MX"],
    });
  }

  generate(): void {
    const templateId = this.templateUuid();

    if (this.form.invalid || !templateId) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();

    const startTs = this.fromLocalDateTimeInputValue(raw.startTs);
    const endTs = this.fromLocalDateTimeInputValue(raw.endTs);

    if (!startTs || !endTs || startTs >= endTs) {
      this.form.get("startTs")?.setErrors({ invalidRange: true });
      this.form.get("endTs")?.setErrors({ invalidRange: true });
      this.form.markAllAsTouched();
      return;
    }

    const request = {
      startTs,
      endTs,
      timezone: raw.timezone,
      locale: raw.locale,
    };

    this.generating = true;

    this.reportService.generateReport(templateId, request).subscribe({
      next: () => {
        this.generating = false;
        this.dialogRef.close(true);
      },
      error: () => {
        this.generating = false;
      },
    });
  }

  close(): void {
    this.dialogRef.close(false);
  }

  openNativeDateTimePicker(event: Event): void {
    const input = event.target as HTMLInputElement;

    if (input && typeof input.showPicker === "function") {
      input.showPicker();
    }
  }

  private toLocalDateTimeInputValue(ts: number): string {
    const date = new Date(ts);
    const pad = (value: number) => String(value).padStart(2, "0");

    const year = date.getFullYear();
    const month = pad(date.getMonth() + 1);
    const day = pad(date.getDate());
    const hours = pad(date.getHours());
    const minutes = pad(date.getMinutes());

    return `${year}-${month}-${day}T${hours}:${minutes}`;
  }

  private fromLocalDateTimeInputValue(value: string): number {
    if (!value) {
      return null;
    }

    return new Date(value).getTime();
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
