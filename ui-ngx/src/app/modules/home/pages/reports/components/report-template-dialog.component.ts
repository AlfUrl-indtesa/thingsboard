///
/// Copyright © 2016-2026 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0
///
import { forkJoin, of } from "rxjs";
import { catchError } from "rxjs/operators";
import { Component, Inject, OnInit } from "@angular/core";
import { FormBuilder, FormControl, Validators } from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import {
  ReportSelectableEntity,
  ReportTemplate,
} from "../models/report.models";
import { ReportService } from "../services/report.service";
type ReportEntityType = "DEVICE" | "ASSET";

@Component({
  selector: "tb-report-template-dialog",
  standalone: false,
  templateUrl: "./report-template-dialog.component.html",
  styleUrls: ["./report-template-dialog.component.scss"],
})
export class ReportTemplateDialogComponent implements OnInit {
  entities: ReportSelectableEntity[] = [];
  telemetryKeys: string[] = [];
  loadingEntities = false;
  loadingKeys = false;

  form = this.fb.group({
    name: new FormControl<string>("", {
      nonNullable: true,
      validators: [Validators.required],
    }),
    description: new FormControl<string>("", {
      nonNullable: true,
    }),
    type: new FormControl<string>("TECHNICAL_VARIABLE", {
      nonNullable: true,
      validators: [Validators.required],
    }),
    status: new FormControl<string>("ACTIVE", {
      nonNullable: true,
      validators: [Validators.required],
    }),

    scopeType: new FormControl<string>("FIXED_ENTITIES", {
      nonNullable: true,
      validators: [Validators.required],
    }),
    entityType: new FormControl<ReportEntityType>("DEVICE", {
      nonNullable: true,
      validators: [Validators.required],
    }),
    selectedEntityIds: new FormControl<any[]>([], {
      nonNullable: true,
      validators: [Validators.required],
    }),
    selectedKeys: new FormControl<string[]>([], {
      nonNullable: true,
      validators: [Validators.required],
    }),

    companyName: new FormControl<string>("Eficentra", {
      nonNullable: true,
    }),
    footerText: new FormControl<string>("Reporte generado por Eficentra", {
      nonNullable: true,
    }),

    includeExecutiveSummary: [true],
    includeDataQuality: [true],
    includeGeneralStatistics: [true],
    includeTimeSeriesChart: [true],
    includeDailyPerformance: [true],
    includeDailyCharts: [true],
    includeAlarms: [true],
    includeConclusion: [true],
  });

  constructor(
    private fb: FormBuilder,
    private reportService: ReportService,
    private dialogRef: MatDialogRef<ReportTemplateDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { template?: ReportTemplate },
  ) {
  }

  ngOnInit(): void {
    this.loadEntities();

    this.form.get("entityType")?.valueChanges.subscribe(() => {
      this.form.patchValue({
        selectedEntityIds: [],
        selectedKeys: [],
      });
      this.telemetryKeys = [];
      this.loadEntities();
    });

    this.form.get("selectedEntityIds")?.valueChanges.subscribe(
      (entityIds: any[]) => {
        this.onSelectedEntitiesChanged(entityIds || []);
      },
    );

    if (this.data?.template) {
      this.patchTemplate(this.data.template);
    }
  }

  private normalizeEntityType(
    entityType: string | null | undefined,
  ): ReportEntityType {
    return entityType === "ASSET" ? "ASSET" : "DEVICE";
  }

  private patchTemplate(template: ReportTemplate): void {
    const entityIds = template.entityFilter?.entityIds || [];
    const entityType = this.normalizeEntityType(
      template.entityFilter?.entityType,
    );

    this.form.patchValue({
      name: template.name || "",
      description: template.description || "",
      type: template.type || "TECHNICAL_VARIABLE",
      status: template.status || "ACTIVE",
      scopeType: template.entityFilter?.scopeType || "FIXED_ENTITIES",
      entityType,
      selectedEntityIds: entityIds,
      selectedKeys: this.extractKeysFromSections(template.sections || []),
      companyName: template.branding?.companyName || "Eficentra",
      footerText: template.branding?.footerText ||
        "Reporte generado por Eficentra",
    });
  }

  loadEntities(textSearch = ""): void {
    const entityType = this.form.getRawValue().entityType;
    this.loadingEntities = true;

    this.reportService.getSelectableEntities(entityType, 0, 100, textSearch)
      .subscribe({
        next: (pageData) => {
          this.entities = pageData?.data || [];
          this.loadingEntities = false;
        },
        error: () => {
          this.entities = [];
          this.loadingEntities = false;
        },
      });
  }

  private onSelectedEntitiesChanged(entityIds: any[]): void {
    if (!entityIds.length) {
      this.telemetryKeys = [];
      this.form.patchValue({ selectedKeys: [] }, { emitEvent: false });
      return;
    }

    const validEntityIds = entityIds.filter((entityId) =>
      entityId?.entityType && entityId?.id
    );

    if (!validEntityIds.length) {
      this.telemetryKeys = [];
      this.form.patchValue({ selectedKeys: [] }, { emitEvent: false });
      return;
    }

    this.loadingKeys = true;

    const requests = validEntityIds.map((entityId) =>
      this.reportService.getSelectableEntityKeys(
        entityId.entityType,
        entityId.id,
      ).pipe(
        catchError(() => of([] as string[])),
      )
    );

    forkJoin(requests).subscribe({
      next: (results) => {
        const uniqueKeys = new Set<string>();

        results.forEach((keys) => {
          (keys || []).forEach((key) => uniqueKeys.add(key));
        });

        this.telemetryKeys = Array.from(uniqueKeys).sort();

        const selectedKeys = this.form.getRawValue().selectedKeys || [];
        const stillAvailableKeys = selectedKeys.filter((key) =>
          uniqueKeys.has(key)
        );

        this.form.patchValue({
          selectedKeys: stillAvailableKeys,
        }, { emitEvent: false });

        this.loadingKeys = false;
      },
      error: () => {
        this.telemetryKeys = [];
        this.form.patchValue({ selectedKeys: [] }, { emitEvent: false });
        this.loadingKeys = false;
      },
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const selectedEntityIds = raw.selectedEntityIds || [];
    const selectedKeys = raw.selectedKeys || [];

    if (!selectedKeys.length) {
      this.form.get("selectedKeys")?.setErrors({ required: true });
      this.form.markAllAsTouched();
      return;
    }

    const template: ReportTemplate = {
      ...(this.data?.template || {}),
      name: raw.name,
      description: raw.description,
      type: raw.type,
      status: raw.status,
      scopeType: raw.scopeType,
      entityFilter: {
        scopeType: raw.scopeType,
        entityType: raw.entityType,
        entityIds: selectedEntityIds,
      },
      sections: this.buildSections(selectedKeys),
      branding: {
        companyName: raw.companyName,
        footerText: raw.footerText,
      },
      outputFormat: "PDF",
      system: false,
    };

    this.reportService.saveReportTemplate(template).subscribe(() => {
      this.dialogRef.close(true);
    });
  }

  close(): void {
    this.dialogRef.close(false);
  }

  displayEntity(entityId: any): string {
    if (!entityId) {
      return "";
    }

    const entity = this.entities.find((item) =>
      item.id?.entityType === entityId.entityType && item.id?.id === entityId.id
    );

    return entity?.label || entity?.name || entityId.id || "";
  }

  private buildSections(selectedKeys: string[]): any[] {
    const sections = [];
    let order = 0;

    if (this.form.value.includeExecutiveSummary) {
      sections.push({
        key: "executive-summary",
        type: "EXECUTIVE_SUMMARY",
        title: "Resumen ejecutivo",
        order: order++,
        visible: true,
        pageBreakBefore: false,
        config: {},
      });
    }

    if (this.form.value.includeDataQuality) {
      sections.push({
        key: "data-quality",
        type: "DATA_QUALITY",
        title: "Calidad y tratamiento de datos",
        order: order++,
        visible: true,
        pageBreakBefore: false,
        config: {
          keys: selectedKeys,
        },
      });
    }

    if (this.form.value.includeGeneralStatistics) {
      sections.push({
        key: "general-statistics",
        type: "GENERAL_STATISTICS",
        title: "Estadística general del periodo",
        order: order++,
        visible: true,
        pageBreakBefore: false,
        config: {
          keys: selectedKeys,
          aggregation: "AVG",
        },
      });
    }

    if (this.form.value.includeTimeSeriesChart) {
      sections.push({
        key: "time-series-chart",
        type: "TIME_SERIES_CHART",
        title: "Serie temporal completa",
        order: order++,
        visible: true,
        pageBreakBefore: false,
        config: {
          keys: selectedKeys,
          aggregation: "AVG",
        },
      });
    }

    if (this.form.value.includeDailyPerformance) {
      sections.push({
        key: "daily-performance",
        type: "DAILY_PERFORMANCE",
        title: "Rendimiento diario",
        order: order++,
        visible: true,
        pageBreakBefore: true,
        config: {
          keys: selectedKeys,
        },
      });
    }

    if (this.form.value.includeDailyCharts) {
      sections.push({
        key: "daily-charts",
        type: "DAILY_CHARTS",
        title: "Gráficas por día",
        order: order++,
        visible: true,
        pageBreakBefore: false,
        config: {
          keys: selectedKeys,
        },
      });
    }

    if (this.form.value.includeAlarms) {
      sections.push({
        key: "alarms",
        type: "ALARM_SUMMARY",
        title: "Análisis de alarmas",
        order: order++,
        visible: true,
        pageBreakBefore: true,
        config: {},
      });
    }

    if (this.form.value.includeConclusion) {
      sections.push({
        key: "conclusion",
        type: "CONCLUSION",
        title: "Conclusión",
        order: order++,
        visible: true,
        pageBreakBefore: false,
        config: {},
      });
    }

    return sections;
  }

  private extractKeysFromSections(sections: any[]): string[] {
    const keys = new Set<string>();

    sections.forEach((section) => {
      const sectionKeys = section?.config?.keys || [];
      sectionKeys.forEach((key: string) => keys.add(key));
    });

    return Array.from(keys);
  }
}
