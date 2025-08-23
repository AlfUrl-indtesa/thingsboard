/*
 * Data Export Page Component (RAW, sin agregación)
 */
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import { PageLink } from '@shared/models/page/page-link';
import { DeviceInfo } from '@shared/models/device.models';
import { EntityType } from '@shared/models/entity-type.models';
import { DataExportService, DataExportBulkRequest, DataExportScheduleRequest, DataExportFormat } from '@core/http/data_export.service';

function combineTs(date: Date, hhmm: string): number {
  const [hh, mm] = (hhmm || '00:00').split(':').map(Number);
  const d = new Date(date);
  d.setHours(hh ?? 0, mm ?? 0, 0, 0);
  return d.getTime();
}

function rangeValidator(ctrl: AbstractControl): ValidationErrors | null {
  const sd = ctrl.get('startDate')?.value as Date | null;
  const st = ctrl.get('startTime')?.value as string | null;
  const ed = ctrl.get('endDate')?.value as Date | null;
  const et = ctrl.get('endTime')?.value as string | null;
  if (!sd || !st || !ed || !et) return null;
  const start = combineTs(sd, st);
  const end = combineTs(ed, et);
  if (start > end) return { startAfterEnd: true };
  if (end > Date.now()) return { endInFuture: true };
  return null;
}

@Component({
  selector: 'tb-data-export-page',
  templateUrl: './data_export_page.html',
  styleUrls: ['./data_export_page.scss']
})
export class DataExportPageComponent implements OnInit {
  form!: FormGroup;
  scheduleForm!: FormGroup;

  loadingDevices = false;
  devices: DeviceInfo[] = [];
  availableKeys: string[] = [];
  loadingKeys = false;

  readonly formats: DataExportFormat[] = ['CSV','JSON'];
  readonly periods = [
    { id: 'DAILY',  label: 'data_export.daily',  cron: (hh: number, mm: number) => `0 ${mm} ${hh} * * *` },
    { id: 'WEEKLY', label: 'data_export.weekly', cron: (hh: number, mm: number) => `0 ${mm} ${hh} * * 1` },
    { id: 'MONTHLY',label: 'data_export.monthly',cron: (hh: number, mm: number) => `0 ${mm} ${hh} 1 * *` }
  ];

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private dataExportService: DataExportService
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      deviceIds: [[], Validators.required],
      keys: [[], Validators.required],
      startDate: [null, Validators.required],
      startTime: ['00:00', Validators.required],
      endDate: [new Date(), Validators.required],
      endTime: [this.defaultNowHHMM(), Validators.required],
      format: ['CSV', Validators.required]
    }, { validators: rangeValidator });

    this.scheduleForm = this.fb.group({
      allDevices: [true],
      deviceIds: [[]],
      email: ['', [Validators.required, Validators.email]],
      lookbackHours: [24, [Validators.required, Validators.min(1)]],
      period: ['DAILY', Validators.required],
      time: [this.defaultNowHHMM(), Validators.required]
    });

    ['startDate','startTime','endDate','endTime'].forEach(n =>
      this.form.get(n)!.valueChanges.subscribe(() => this.form.updateValueAndValidity({emitEvent:false}))
    );

    this.loadCurrentUserAndDevices();

    this.form.get('deviceIds')!.valueChanges.subscribe((ids: string[]) => {
      this.availableKeys = [];
      this.form.patchValue({ keys: [] }, { emitEvent: false });
      if (ids?.length) this.fetchKeysForDevices(ids);
    });
  }

  private defaultNowHHMM(): string {
    const d = new Date();
    const hh = String(d.getHours()).padStart(2,'0');
    const mm = String(d.getMinutes()).padStart(2,'0');
    return `${hh}:${mm}`;
  }

  private loadCurrentUserAndDevices(): void {
    this.loadingDevices = true;
    this.http.get<any>('/api/auth/user').subscribe({
      next: (user) => {
        const customerId: string | undefined = user?.customerId?.id;
        if (customerId) {
          this.loadCustomerDevices(customerId);
        } else {
          this.loadingDevices = false;
        }
      },
      error: () => this.loadingDevices = false
    });
  }

  private loadCustomerDevices(customerId: string): void {
    const pageSize = 200;
    const devices: DeviceInfo[] = [];
    const loadPage = (pageLink: PageLink) => {
      this.http.get<PageData<DeviceInfo>>(`/api/customer/${customerId}/devices?pageSize=${pageLink.pageSize}&page=${pageLink.page}&textSearch=${pageLink.textSearch || ''}`)
        .subscribe({
          next: (page) => {
            devices.push(...(page.data || []));
            if (page.hasNext) {
              loadPage(pageLink.nextPageLink());
            } else {
              this.devices = devices;
              this.loadingDevices = false;
            }
          },
          error: () => this.loadingDevices = false
        });
    };
    loadPage(new PageLink(pageSize));
  }

  private fetchKeysForDevices(deviceIds: string[]): void {
    this.loadingKeys = true;
    const reqs = deviceIds.map(id =>
      this.http.get<string[]>(`/api/plugins/telemetry/${EntityType.DEVICE}/${id}/keys/timeseries`)
    );
    forkJoin(reqs).subscribe({
      next: (arrs) => {
        const s = new Set<string>();
        arrs.forEach(a => (a || []).forEach(k => s.add(k)));
        this.availableKeys = Array.from(s).sort();
      },
      error: () => this.loadingKeys = false,
      complete: () => this.loadingKeys = false
    });
  }

  export(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.value as any;
    const startTs = combineTs(v.startDate, v.startTime);
    const endTs   = combineTs(v.endDate, v.endTime);

    const req: DataExportBulkRequest = {
      entityType: 'DEVICE',
      deviceIds: v.deviceIds,
      keys: v.keys,
      startTs,
      endTs,
      format: v.format
    };
    this.dataExportService.exportBulk(req).subscribe((zip: Blob) => {
      const url = URL.createObjectURL(zip);
      const a = document.createElement('a');
      a.href = url;
      a.download = `data_export_${new Date().toISOString().slice(0,10)}.zip`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  schedule(): void {
    if (this.scheduleForm.invalid) {
      this.scheduleForm.markAllAsTouched();
      return;
    }
    // Deben existir claves seleccionadas en el formulario principal
    const keys: string[] = this.form.get('keys')?.value || [];
    if (!keys.length) {
      alert('Selecciona al menos una clave antes de programar.');
      return;
    }
    const s = this.scheduleForm.value as any;
    const [hh, mm] = (s.time || '02:00').split(':').map((x: string) => parseInt(x, 10));
    const period = this.periods.find(p => p.id === s.period)!;
    const cron = period.cron(hh || 2, mm || 0);

    const req: DataExportScheduleRequest = {
      allDevices: !!s.allDevices,
      deviceIds: s.allDevices ? [] : (s.deviceIds || []),
      keys,
      lookbackMs: Math.max(1, (s.lookbackHours || 24) * 3600_000),
      cron,
      email: s.email
    };
    this.dataExportService.scheduleExport(req).subscribe(() => {
      alert('Backup programado correctamente.');
    });
  }
}
