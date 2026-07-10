///
/// Copyright © 2016-2026 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
///

import { Component, Inject, OnInit } from "@angular/core";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { forkJoin, of } from "rxjs";
import { ReportTemplate } from "../models/report.models";
import { ReportService } from "../services/report.service";

interface ReportTelemetrySource {
  entityType: string;
  entityId: string;
  key: string;
}

interface ReportTelemetrySourceGroup {
  entityType: string;
  entityId: string;
  keys: string[];
}

@Component({
  selector: "tb-generate-report-dialog",
  standalone: false,
  templateUrl: "./generate-report-dialog.component.html",
  styleUrls: ["./generate-report-dialog.component.scss"],
})
export class GenerateReportDialogComponent implements OnInit {
  form: FormGroup;
  generating = false;
  loadingAutomaticRange = false;
  automaticRangeApplied = false;

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

  ngOnInit(): void {
    this.applyAutomaticStartDate();
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

  private applyAutomaticStartDate(): void {
    const groups = this.groupTelemetrySources(this.extractTelemetrySources());

    if (!groups.length) {
      return;
    }

    this.loadingAutomaticRange = true;

    forkJoin(
      groups.map((group) =>
        this.reportService.getFirstTelemetryTs(
          group.entityType,
          group.entityId,
          group.keys,
        )
      ),
    ).subscribe({
      next: (timestamps) => {
        const validTimestamps = (timestamps || [])
          .filter((ts): ts is number => typeof ts === "number" && ts > 0);

        if (validTimestamps.length) {
          const firstTs = Math.min(...validTimestamps);

          this.form.patchValue({
            startTs: this.toLocalDateTimeInputValue(firstTs),
            endTs: this.toLocalDateTimeInputValue(Date.now()),
          });

          this.automaticRangeApplied = true;
        }

        this.loadingAutomaticRange = false;
      },
      error: () => {
        this.loadingAutomaticRange = false;
      },
    });
  }

  private extractTelemetrySources(): ReportTelemetrySource[] {
    const sections = this.template?.sections || [];
    const result: ReportTelemetrySource[] = [];

    for (const section of sections) {
      const variables = section?.config?.variables || [];

      if (Array.isArray(variables)) {
        for (const variable of variables) {
          if (variable?.enabled === false) {
            continue;
          }

          if (
            variable?.entityId?.entityType && variable?.entityId?.id &&
            variable?.key
          ) {
            result.push({
              entityType: variable.entityId.entityType,
              entityId: variable.entityId.id,
              key: variable.key,
            });
          }
        }
      }
    }

    if (result.length) {
      return this.deduplicateSources(result);
    }

    const entityIds = this.template?.entityFilter?.entityIds || [];
    const keys = this.extractKeysFromSections(sections);

    for (const entityId of entityIds) {
      for (const key of keys) {
        if (entityId?.entityType && entityId?.id && key) {
          result.push({
            entityType: entityId.entityType,
            entityId: entityId.id,
            key,
          });
        }
      }
    }

    return this.deduplicateSources(result);
  }

  private extractKeysFromSections(sections: any[]): string[] {
    const keys = new Set<string>();

    for (const section of sections || []) {
      const config = section?.config;

      if (!config) {
        continue;
      }

      if (Array.isArray(config.keys)) {
        config.keys.forEach((key) => {
          if (key) {
            keys.add(key);
          }
        });
      }

      if (Array.isArray(config.items)) {
        config.items.forEach((item) => {
          if (item?.key) {
            keys.add(item.key);
          }
        });
      }

      if (Array.isArray(config.columns)) {
        config.columns.forEach((column) => {
          if (column?.key) {
            keys.add(column.key);
          }
        });
      }
    }

    return Array.from(keys);
  }

  private groupTelemetrySources(
    sources: ReportTelemetrySource[],
  ): ReportTelemetrySourceGroup[] {
    const groups = new Map<string, ReportTelemetrySourceGroup>();

    for (const source of sources || []) {
      const groupKey = `${source.entityType}:${source.entityId}`;

      if (!groups.has(groupKey)) {
        groups.set(groupKey, {
          entityType: source.entityType,
          entityId: source.entityId,
          keys: [],
        });
      }

      const group = groups.get(groupKey);

      if (group && !group.keys.includes(source.key)) {
        group.keys.push(source.key);
      }
    }

    return Array.from(groups.values());
  }

  private deduplicateSources(
    sources: ReportTelemetrySource[],
  ): ReportTelemetrySource[] {
    const result: ReportTelemetrySource[] = [];
    const seen = new Set<string>();

    for (const source of sources || []) {
      const key = `${source.entityType}:${source.entityId}:${source.key}`;

      if (!seen.has(key)) {
        seen.add(key);
        result.push(source);
      }
    }

    return result;
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
