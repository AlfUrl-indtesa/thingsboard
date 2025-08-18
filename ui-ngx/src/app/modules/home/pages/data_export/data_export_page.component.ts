/*
 * Copyright © 2016-2025 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Data Export Page Component
 * Ubicación: ui-ngx/src/app/modules/home/pages/data_export/data_export_page.component.ts
 */

import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
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

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private deviceService: DeviceService,
    private dataExportService: DataExportService
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      deviceId: [null, Validators.required],
      keys: [[], Validators.required],
      startDate: [null, Validators.required],
      endDate: [null, Validators.required],
      interval: [null], // ms (opcional)
      aggregation: ['NONE', Validators.required],
      format: [DataExportFormat.CSV, Validators.required]
    });

    // Obtener el usuario actual para derivar el customerId
    this.http.get<{ customerId?: { id: string } }>('/api/auth/user').subscribe(user => {
      this.customerId = user?.customerId?.id;
      this.loadCustomerDevices();
    });

    // Recargar keys al cambiar de dispositivo
    this.form.get('deviceId')!.valueChanges.subscribe((deviceId: string | null) => {
      this.availableKeys = [];
      this.form.patchValue({ keys: [] }, { emitEvent: false });
      if (deviceId) {
        this.fetchKeysForDevice(deviceId);
      }
    });
  }

  private loadCustomerDevices(): void {
    if (!this.customerId) { return; }
    this.loadingDevices = true;

    const pageLink = new PageLink(100); // pagina si hay muchos
    this.deviceService.getCustomerDeviceInfos(this.customerId, pageLink).subscribe({
      next: (page: PageData<DeviceInfo>) => {
        this.devices = page?.data || [];
        // Autoseleccionar si solo hay uno
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

  export(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { deviceId, keys, startDate, endDate, interval, aggregation, format } = this.form.value as {
      deviceId: string;
      keys: string[];
      startDate: string | Date;
      endDate: string | Date;
      interval: number | null;
      aggregation: 'NONE' | 'MIN' | 'MAX' | 'AVG' | 'SUM';
      format: DataExportFormat;
    };

    const startTs = new Date(startDate).getTime();
    const endTs = new Date(endDate).getTime();

    const query = new DataExportQuery({
      entityId: { entityType: EntityType.DEVICE, id: deviceId },
      keys,
      startTs,
      endTs,
      interval: interval || undefined,
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
}
