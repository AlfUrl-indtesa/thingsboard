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
  ReportVariableConfig,
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
  variableConfigs: ReportVariableConfig[] = [];
  loadingKeysByEntityId: Record<string, boolean> = {};

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

    this.variableConfigs = this.extractVariablesFromSections(
      template.sections || [],
    );

    if (!this.variableConfigs.length) {
      const keys = this.extractKeysFromSections(template.sections || []);
      this.variableConfigs = [];

      entityIds.forEach((entityId) => {
        keys.forEach((key) => {
          this.variableConfigs.push(this.buildVariableConfig(entityId, key));
        });
      });
    }

    this.syncSelectedKeysFromVariableConfigs();
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
      this.variableConfigs = [];
      this.form.patchValue({ selectedKeys: [] }, { emitEvent: false });
      return;
    }

    const validEntityIds = entityIds.filter((entityId) =>
      entityId?.entityType && entityId?.id
    );

    if (!validEntityIds.length) {
      this.telemetryKeys = [];
      this.variableConfigs = [];
      this.form.patchValue({ selectedKeys: [] }, { emitEvent: false });
      return;
    }

    const selectedUids = new Set(
      validEntityIds.map((entityId) => this.entityUid(entityId)),
    );

    this.variableConfigs = this.variableConfigs.filter((config) =>
      selectedUids.has(this.entityUid(config.entityId))
    );

    validEntityIds.forEach((entityId) => {
      this.loadKeysForEntity(entityId);
    });

    this.syncSelectedKeysFromVariableConfigs();
  }

  private loadKeysForEntity(entityId: any): void {
    const uid = this.entityUid(entityId);

    if (!uid) {
      return;
    }

    this.loadingKeysByEntityId[uid] = true;
    this.loadingKeys = true;

    this.reportService.getSelectableEntityKeys(entityId.entityType, entityId.id)
      .pipe(catchError(() => of([] as string[])))
      .subscribe((keys) => {
        const existingKeys = new Set(
          this.variableConfigs
            .filter((config) => this.entityUid(config.entityId) === uid)
            .map((config) => config.key),
        );

        (keys || []).forEach((key) => {
          if (!existingKeys.has(key)) {
            this.variableConfigs.push(this.buildVariableConfig(entityId, key));
          }
        });

        const allKeys = new Set<string>();
        this.variableConfigs.forEach((config) => allKeys.add(config.key));
        this.telemetryKeys = Array.from(allKeys).sort();

        this.loadingKeysByEntityId[uid] = false;
        this.loadingKeys = Object.values(this.loadingKeysByEntityId).some(
          Boolean,
        );

        this.syncSelectedKeysFromVariableConfigs();
      });
  }

  variablesForEntity(entityId: any): ReportVariableConfig[] {
    const uid = this.entityUid(entityId);

    return this.variableConfigs.filter((config) =>
      this.entityUid(config.entityId) === uid
    );
  }

  private buildVariableConfig(
    entityId: any,
    key: string,
  ): ReportVariableConfig {
    return {
      entityId,
      entityName: this.displayEntity(entityId),
      key,
      enabled: true,
      label: this.defaultVariableLabel(key),
      unit: this.defaultVariableUnit(key),
      scale: 1,
      offset: 0,
      chartEnabled: true,
      tableEnabled: true,
      granularity: "FULL",
      stats: {
        min: true,
        max: true,
        avg: true,
        count: true,
        sum: false,
        first: false,
        last: false,
        delta: false,
      },
    };
  }

  private syncSelectedKeysFromVariableConfigs(): void {
    const selectedKeys = Array.from(
      new Set(
        this.variableConfigs
          .filter((config) => config.enabled)
          .map((config) => config.key),
      ),
    );

    this.form.patchValue({ selectedKeys }, { emitEvent: false });
  }

  onVariableEnabledChanged(config: ReportVariableConfig, event: Event): void {
    config.enabled = (event.target as HTMLInputElement).checked;
    this.syncSelectedKeysFromVariableConfigs();
  }

  onVariableBooleanChanged(
    config: ReportVariableConfig,
    field: "chartEnabled" | "tableEnabled",
    event: Event,
  ): void {
    config[field] = (event.target as HTMLInputElement).checked;
  }

  onVariableStatChanged(
    config: ReportVariableConfig,
    stat: keyof ReportVariableConfig["stats"],
    event: Event,
  ): void {
    config.stats[stat] = (event.target as HTMLInputElement).checked;
  }

  private entityUid(entityId: any): string {
    if (!entityId) {
      return "";
    }

    return `${entityId.entityType}:${entityId.id}`;
  }

  private defaultVariableLabel(key: string): string {
    const normalized = (key || "").toLowerCase();

    const labels: Record<string, string> = {
      pressure: "Presión",
      "presión": "Presión",
      "presiã³n": "Presión",
      temprocio: "Punto de rocío",
      temp_rocio: "Punto de rocío",
      dew_point: "Punto de rocío",
      ams_instant_flow_lpm: "Flujo instantáneo",
      ams_cumulative_flow_l: "Consumo acumulado",
      ams_temperature_c: "Temperatura",
      temperature: "Temperatura",
      humidity: "Humedad",
      power: "Potencia",
      energy: "Energía",
      voltage: "Voltaje",
      current: "Corriente",
    };

    return labels[normalized] || this.humanizeKey(key);
  }

  private defaultVariableUnit(key: string): string {
    const normalized = (key || "").toLowerCase();

    const units: Record<string, string> = {
      pressure: "psi",
      "presión": "psi",
      "presiã³n": "psi",
      temprocio: "°C",
      temp_rocio: "°C",
      dew_point: "°C",
      ams_instant_flow_lpm: "L/min",
      ams_cumulative_flow_l: "L",
      ams_temperature_c: "°C",
      temperature: "°C",
      humidity: "%",
      power: "kW",
      energy: "kWh",
      voltage: "V",
      current: "A",
    };

    return units[normalized] || "";
  }

  private humanizeKey(key: string): string {
    if (!key) {
      return "Variable";
    }

    const text = key.replace(/[_-]/g, " ").trim();

    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const selectedEntityIds = raw.selectedEntityIds || [];
    const selectedVariables = this.variableConfigs.filter((config) =>
      config.enabled
    );
    const selectedKeys = Array.from(
      new Set(selectedVariables.map((config) => config.key)),
    );

    if (!selectedVariables.length) {
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
      sections: this.buildSections(selectedKeys, selectedVariables),
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

  private buildSections(
    selectedKeys: string[],
    selectedVariables: ReportVariableConfig[],
  ): any[] {
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
          variables: selectedVariables,
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
          variables: selectedVariables,
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
          variables: selectedVariables,
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
          variables: selectedVariables,
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
          variables: selectedVariables,
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

  private extractVariablesFromSections(
    sections: any[],
  ): ReportVariableConfig[] {
    const variables: ReportVariableConfig[] = [];

    (sections || []).forEach((section) => {
      const sectionVariables = section?.config?.variables || [];

      if (Array.isArray(sectionVariables)) {
        sectionVariables.forEach((variable) => {
          if (variable?.entityId && variable?.key) {
            variables.push({
              entityId: variable.entityId,
              entityName: variable.entityName,
              key: variable.key,
              enabled: variable.enabled !== false,
              label: variable.label || this.defaultVariableLabel(variable.key),
              unit: variable.unit || this.defaultVariableUnit(variable.key),
              scale: variable.scale ?? 1,
              offset: variable.offset ?? 0,
              chartEnabled: variable.chartEnabled !== false,
              tableEnabled: variable.tableEnabled !== false,
              granularity: variable.granularity || "FULL",
              stats: {
                min: variable.stats?.min !== false,
                max: variable.stats?.max !== false,
                avg: variable.stats?.avg !== false,
                count: variable.stats?.count !== false,
                sum: variable.stats?.sum === true,
                first: variable.stats?.first === true,
                last: variable.stats?.last === true,
                delta: variable.stats?.delta === true,
              },
            });
          }
        });
      }
    });

    const unique = new Map<string, ReportVariableConfig>();

    variables.forEach((variable) => {
      unique.set(
        `${this.entityUid(variable.entityId)}:${variable.key}`,
        variable,
      );
    });

    return Array.from(unique.values());
  }
}
