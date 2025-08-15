/*
 * Data Export Page Component
 * Ubicación: ui-ngx/src/app/modules/home/pages/data-export/data-exportpage.component.ts
 */
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { DeviceService } from '@core/http/device.service';
import { DataExportService } from '@core/http/data_export.service';
2
import { PageData } from '@shared/models/page/page-data';
import { PageLink } from '@shared/models/page/page-link';
import { EntityType } from '@shared/models/entity-type.models';
import { Device } from '@shared/models/device.models';
import { CustomerId } from '@shared/models/id/customer-id';
import { DataExportFormat, DataExportQuery } from '@shared/models/data_export.models';
@Component({
selector: 'tb-data-export-page',
templateUrl: './data_export_page.html',
styleUrls: ['./data_export_page.scss']
})
export class DataExportPageComponent implements OnInit {
form!: FormGroup;
loadingDevices = false;
devices: Device[] = [];
availableKeys: string[] = [];
loadingKeys = false;
readonly formats = Object.values(DataExportFormat);
readonly aggregations = ['NONE', 'MIN', 'MAX', 'AVG', 'SUM'];
private customerId?: CustomerId;
constructor(
private fb: FormBuilder,
private http: HttpClient,
private authService: AuthService,
private deviceService: DeviceService,
private dataExportService: DataExportService
) {}
ngOnInit(): void {
const authUser = this.authService.getAuthUser();
this.customerId = authUser?.customerId;
this.form = this.fb.group({
deviceId: [null, Validators.required],
keys: [[], Validators.required],
startDate: [null, Validators.required],
endDate: [null, Validators.required],
interval: [null], // ms
aggregation: ['NONE', Validators.required],
format: [DataExportFormat.CSV, Validators.required]
});
this.loadCustomerDevices();
3
// Cuando cambia el dispositivo, recargar keys
this.form.get('deviceId')!.valueChanges.subscribe((deviceId: string |
null) => {
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
const pageLink = new PageLink(100); // ajusta o pagina si tienes muchos
this.deviceService.getCustomerDevices(this.customerId.id,
pageLink).subscribe({
next: (page: PageData<Device>) => {
this.devices = page.data || [];
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
const url = `/api/plugins/telemetry/${EntityType.DEVICE}/${deviceId}/
keys/timeseries`;
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
const {
deviceId, keys, startDate, endDate, interval, aggregation, format
} = this.form.value as {
4
deviceId: string; keys: string[]; startDate: Date; endDate: Date;
interval: number | null; aggregation: string; format: DataExportFormat;
};
const startTs = new Date(startDate).getTime();
const endTs = new Date(endDate).getTime();
const query = new DataExportQuery({
entityId: { entityType: EntityType.DEVICE, id: deviceId },
keys,
startTs,
endTs,
interval: interval || undefined,
aggregation: (aggregation || 'NONE') as any
}, format);
this.dataExportService.exportData(query).subscribe((blob: Blob) => {
const url = window.URL.createObjectURL(blob);
const a = document.createElement('a');
a.href = url;
a.download = `data_export_${new Date().toISOString().slice(0,10)}.$
{format.toLowerCase()}`;
a.click();
window.URL.revokeObjectURL(url);
});
}
}
