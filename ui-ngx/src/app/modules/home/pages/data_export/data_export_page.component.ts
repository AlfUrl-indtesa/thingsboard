/*
 * Copyright © 2016-2025 The Thingsboard Authors
 * Licensed under the Apache License, Version 2.0
 */

/*
 * Data Export Page Component
 * Ubicación: ui-ngx/src/app/modules/home/pages/data_export/data_export_page.component.ts
 */

import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DeviceService } from '@core/http/device.service';
import { DataExportService } from '@core/http/data_export.service';
import { PageData } from '@shared/models/page/page-data';
import { PageLink } from '@shared/models/page/page-link';
import { EntityType } from '@shared/models/entity-type.models';
import { DeviceInfo } from '@shared/models/device.models';
import { DataExportFormat, DataExportQuery } from '@shared/models/data_export.models';

@Component({
  selector: 'tb-data-export-page',
  templateUrl: './data_export_page.html',
  styleUrls: ['./data_export_page.scss']
})
export class DataExportPageComponent implements OnInit {
  form!: FormGroup;

  loadingDevices = false;
  devices: DeviceInfo[] = [];

  availableKeys: string[] = [];
  loadingKeys = false;

  readonly formats = Object.values(DataExportFormat);
  readonly aggregations: Array<'NONE' | 'MIN' | 'MAX' | 'AVG' | 'SUM'> = ['NONE', 'MIN', 'MAX', 'AVG', 'SUM'];

  private customerId?: string;
  // límites dinámicos
  minStartTs?: number; // primera medición (estimada)
  readonly now = Date.now();

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private deviceService: DeviceService,
    private dataExportService: DataExportService
  ) {}

  ngOnInit(): void {
    const today = new Date();
    const yesterday = new Date(Date.now() - 24 * 3600 * 1000);

    this.form = this.fb.group({
      deviceId: [null, Validators.required],
      keys: [[], Validators.required],
      // date + time separados para selector cómodo
      startDate: [yesterday, Validators.required],
      startTime: ['00:00', Validators.required],
      endDate: [today, Validators.required],
      endTime: [this.formatTime(today), Validators.required],
      // intervalo en segundos
      intervalSec: [null, [Validators.min(1)]],
      aggregation: ['NONE', Validators.required],
      format: [DataExportFormat.CSV, Validators.required]
    }, { validators: this.dateRangeValidator.bind(this) });

    // Obtener usuario para customerId y cargar dispositivos
    this.http.get<{ customerId?: { id: string } }>('/api/auth/user').subscribe(user => {
      this.customerId = user?.customerId?.id;
      this.loadCustomerDevices();
    });

    // Cuando cambia el dispositivo, recarga keys y resetea ventana de tiempo mínima
    this.form.get('deviceId')!.valueChanges.subscribe((deviceId: string | null) => {
      this.availableKeys = [];
      this.form.patchValue({ keys: [] }, { emitEvent: false });
      this.minStartTs = undefined;
      if (deviceId) {
        this.fetchKeysForDevice(deviceId);
      }
    });

    // Cuando cambian las keys, intenta calcular primera medición (opcional)
    this.form.get('keys')!.valueChanges.subscribe((keys: string[]) => {
      const deviceId = this.form.get('deviceId')!.value as string | null;
      if (deviceId && keys?.length) {
        this.estimateFirstMeasurement(deviceId, keys[0]); // usa la primera key seleccionada
      } else {
        this.minStartTs = undefined;
      }
    });
  }

  // Valida: start ≤ end y end ≤ now; si minStartTs existe, start ≥ minStartTs
  private dateRangeValidator(group: AbstractControl) {
    const startDate = group.get('startDate')?.value as Date;
    const endDate = group.get('endDate')?.value as Date;
    const startTime = group.get('startTime')?.value as string;
    const endTime = group.get('endTime')?.value as string;

    if (!startDate || !endDate || !startTime || !endTime) return null;

    const startTs = this.combineDateTime(startDate, startTime);
    const endTs = this.combineDateTime(endDate, endTime);

    if (endTs > Date.now()) {
      return { endInFuture: true };
    }
    if (startTs > endTs) {
      return { startAfterEnd: true };
    }
    if (this.minStartTs && startTs < this.minStartTs) {
      return { startBeforeFirstMeasurement: true };
    }
    return null;
  }

  private loadCustomerDevices(): void {
    if (!this.customerId) { return; }
    this.loadingDevices = true;

    const pageLink = new PageLink(100);
    this.deviceService.getCustomerDeviceInfos(this.customerId, pageLink).subscribe({
      next: (page: PageData<DeviceInfo>) => {
        this.devices = page?.data || [];
        if (this.devices.length === 1) {
          this.form.patchValue({ deviceId: this.devices[0].id.id });
        }
      },
      error: () => {},
      complete: () => { this.loadingDevices = false; }
    });
  }

  private fetchKeysForDevice(deviceId: string): void {
    this.loadingKeys = true;
    const url = `/api/plugins/telemetry/${EntityType.DEVICE}/${deviceId}/keys/timeseries`;
    this.http.get<string[]>(url).subscribe({
      next: (keys) => { this.availableKeys = keys || []; },
      error: () => {},
      complete: () => { this.loadingKeys = false; }
    });
  }

  // Intenta estimar la primera medición de una 'key' para fijar minStartTs
  private estimateFirstMeasurement(deviceId: string, key: string): void {
    const end = Date.now();
    // Pedimos 1 dato desde 1970 a ahora, orden ascendente (si el backend lo soporta)
    const url = `/api/plugins/telemetry/${EntityType.DEVICE}/${deviceId}/values/timeseries` +
      `?keys=${encodeURIComponent(key)}&startTs=0&endTs=${end}&limit=1&agg=NONE&orderBy=ASC`;
    this.http.get<any>(url).subscribe({
      next: (res) => {
        const arr = res?.[key] as Array<{ ts: number; value: any }>;
        if (arr?.length) {
          this.minStartTs = arr[0].ts;
          // Si el usuario puso una fecha anterior, la ajustamos al mínimo permitido
          const startDate = this.form.get('startDate')!.value as Date;
          const startTime = this.form.get('startTime')!.value as string;
          const currentStartTs = this.combineDateTime(startDate, startTime);
          if (currentStartTs < this.minStartTs) {
            const d = new Date(this.minStartTs);
            this.form.patchValue({
              startDate: d,
              startTime: this.formatTime(d)
            });
          }
        }
      },
      error: () => {
        // si falla, no bloqueamos; solo dejamos sin minStartTs
        this.minStartTs = undefined;
      }
    });
  }

  export(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const {
      deviceId, keys, startDate, startTime, endDate, endTime, intervalSec, aggregation, format
    } = this.form.value as {
      deviceId: string;
      keys: string[];
      startDate: Date;
      startTime: string;
      endDate: Date;
      endTime: string;
      intervalSec: number | null;
      aggregation: 'NONE' | 'MIN' | 'MAX' | 'AVG' | 'SUM';
      format: DataExportFormat;
    };

    const startTs = this.combineDateTime(startDate, startTime);
    const endTs = this.combineDateTime(endDate, endTime);
    const intervalMs = intervalSec ? intervalSec * 1000 : undefined;

    const query = new DataExportQuery({
      entityId: { entityType: EntityType.DEVICE, id: deviceId },
      keys,
      startTs,
      endTs,
      interval: intervalMs,
      aggregation: aggregation || 'NONE'
    }, format);

    this.dataExportService.exportData(query).subscribe((blob: Blob) => {
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `data_export_${new Date().toISOString().slice(0, 10)}.${format.toLowerCase()}`;
      a.click();
      window.URL.revokeObjectURL(url);
    });
  }

  // helpers de fecha/hora
  private combineDateTime(date: Date, hhmm: string): number {
    const [h, m] = hhmm.split(':').map(x => parseInt(x, 10));
    const d = new Date(date);
    d.setHours(h || 0, m || 0, 0, 0);
    return d.getTime();
  }

  private formatTime(d: Date): string {
    const hh = `${d.getHours()}`.padStart(2, '0');
    const mm = `${d.getMinutes()}`.padStart(2, '0');
    return `${hh}:${mm}`;
  }
}
