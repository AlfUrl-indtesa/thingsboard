import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';

import { DataExportService } from './data-export.service';
import {
  ExportableDevice,
  DataExportPreviewResponse
} from './data-export.models';

@Component({
  selector: 'tb-data-export',
  standalone: false,
  templateUrl: './data-export.component.html',
  styleUrls: ['./data-export.component.scss']
})
export class DataExportComponent implements OnInit {
  loading = false;
  exporting = false;
  scheduling = false;

  devices: ExportableDevice[] = [];
  telemetryKeys: string[] = [];
  attributeKeys: string[] = [];

  form = this.fb.group({
    deviceIds: [[] as string[]],
    keys: [[] as string[]],
    attributeKeys: [[] as string[]],
    includeCalculatedFields: [true],
    includeAttributes: [true],

    startDateTime: [''],
    endDateTime: [this.toDateTimeLocal(Date.now()), Validators.required],
    autoDetectOldestTs: [true],
    format: ['csv' as 'csv', Validators.required],

    scheduleEnabled: [false],
    allDevices: [false],
    email: ['', Validators.email],
    period: ['WEEKLY' as 'WEEKLY'],
    timeOfDay: ['08:00', Validators.required],
    timezone: ['America/Monterrey'],
    mode: ['INCREMENTAL' as 'FULL' | 'INCREMENTAL']
  });

  constructor(
    private fb: FormBuilder,
    private dataExportService: DataExportService
  ) {}

  ngOnInit(): void {
    this.loadInitialData();
    this.loadSchedule();
  }

  loadInitialData(): void {
    this.loading = true;
    this.dataExportService.getInitialPreview()
      .pipe(finalize(() => this.loading = false))
      .subscribe({
        next: (res) => this.applyPreview(res),
        error: (err) => console.error('Error loading export preview', err)
      });
  }

  loadSchedule(): void {
    this.dataExportService.getSchedule().subscribe({
      next: (schedule) => {
        if (!schedule) {
          return;
        }
        this.form.patchValue({
          scheduleEnabled: schedule.enabled,
          allDevices: schedule.allDevices,
          email: schedule.email,
          period: schedule.period,
          timeOfDay: schedule.timeOfDay,
          timezone: schedule.timezone || 'America/Monterrey',
          mode: schedule.mode
        }, { emitEvent: false });
      },
      error: () => {}
    });
  }

  onDevicesChanged(): void {
    const deviceIds = this.form.get('deviceIds')?.value || [];
    this.dataExportService.preview({
      deviceIds,
      includeCalculatedFields: !!this.form.get('includeCalculatedFields')?.value,
      includeAttributes: !!this.form.get('includeAttributes')?.value
    }).subscribe({
      next: (res) => this.applyPreview(res),
      error: (err) => console.error('Error loading device keys', err)
    });
  }

  onIncludeFlagsChanged(): void {
    this.onDevicesChanged();
  }

  private applyPreview(res: DataExportPreviewResponse): void {
    this.devices = res.devices || [];
    this.telemetryKeys = res.keys || [];
    this.attributeKeys = res.attributeKeys || [];

    const patch: any = {
      endDateTime: this.toDateTimeLocal(res.defaultEndTs || Date.now())
    };

    if (res.defaultStartTs) {
      patch.startDateTime = this.toDateTimeLocal(res.defaultStartTs);
    }

    if (res.suggestedEmail) {
      patch.email = res.suggestedEmail;
    }

    this.form.patchValue(patch, { emitEvent: false });
  }

  exportData(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const startTs = this.form.get('autoDetectOldestTs')?.value
      ? null
      : this.fromDateTimeLocal(this.form.get('startDateTime')?.value || '');

    const endTs = this.fromDateTimeLocal(this.form.get('endDateTime')?.value || '');

    if (!endTs) {
      return;
    }

    if (!this.form.get('autoDetectOldestTs')?.value && startTs && startTs > endTs) {
      console.error('startTs cannot be greater than endTs');
      return;
    }

    this.exporting = true;
    this.dataExportService.exportCsv({
      deviceIds: this.form.get('deviceIds')?.value || [],
      keys: this.form.get('keys')?.value || [],
      attributeKeys: this.form.get('attributeKeys')?.value || [],
      includeCalculatedFields: !!this.form.get('includeCalculatedFields')?.value,
      includeAttributes: !!this.form.get('includeAttributes')?.value,
      startTs,
      endTs,
      autoDetectOldestTs: !!this.form.get('autoDetectOldestTs')?.value,
      format: 'csv'
    }).pipe(
      finalize(() => this.exporting = false)
    ).subscribe({
      next: (blob) => this.downloadBlob(blob, `thingsboard-export-${Date.now()}.csv`),
      error: (err) => console.error('Error exporting CSV', err)
    });
  }

  saveSchedule(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.scheduling = true;
    this.dataExportService.saveSchedule({
      enabled: !!this.form.get('scheduleEnabled')?.value,
      allDevices: !!this.form.get('allDevices')?.value,
      deviceIds: this.form.get('deviceIds')?.value || [],
      keys: this.form.get('keys')?.value || [],
      attributeKeys: this.form.get('attributeKeys')?.value || [],
      includeCalculatedFields: !!this.form.get('includeCalculatedFields')?.value,
      includeAttributes: !!this.form.get('includeAttributes')?.value,
      email: this.form.get('email')?.value || '',
      period: 'WEEKLY',
      timeOfDay: this.form.get('timeOfDay')?.value || '08:00',
      timezone: this.form.get('timezone')?.value || 'America/Monterrey',
      mode: this.form.get('mode')?.value || 'INCREMENTAL'
    }).pipe(
      finalize(() => this.scheduling = false)
    ).subscribe({
      next: () => console.log('Schedule saved'),
      error: (err) => console.error('Error saving schedule', err)
    });
  }

  private downloadBlob(blob: Blob, fileName: string): void {
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    window.URL.revokeObjectURL(url);
  }

  private toDateTimeLocal(ts: number): string {
    const d = new Date(ts);
    const pad = (n: number) => n.toString().padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  private fromDateTimeLocal(value: string): number | null {
    if (!value) {
      return null;
    }
    const parsed = new Date(value);
    const time = parsed.getTime();
    return Number.isNaN(time) ? null : time;
  }
}