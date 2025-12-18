import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type DataExportFormat = 'CSV' | 'JSON';

export interface DataExportBulkRequest {
  entityType: 'DEVICE';
  deviceIds: string[];
  keys: string[];
  startTs: number;
  endTs: number;
  format?: DataExportFormat;
}

export interface DataExportScheduleRequest {
  jobId?: string;
  allDevices: boolean;
  deviceIds?: string[];
  keys: string[];
  lookbackMs: number;
  cron: string;
  email?: string;          // <- opcional (backend resuelve)
  format?: DataExportFormat; // <- futuro (cuando corrijas backend)
}

@Injectable({ providedIn: 'root' })
export class DataExportService {
  constructor(private http: HttpClient) {}

  exportBulk(req: DataExportBulkRequest): Observable<Blob> {
    const deviceIds = (req.deviceIds || []).map(encodeURIComponent).join(',');
    const keys = (req.keys || []).map(encodeURIComponent).join(',');

    let params = new HttpParams()
      .set('deviceIds', deviceIds)
      .set('keys', keys)
      .set('startTs', String(req.startTs))
      .set('endTs', String(req.endTs));

    if (req.format) {
      params = params.set('format', req.format);
    }

    const url = `/api/data_export/bulk/${req.entityType}`;
    return this.http.get(url, { params, responseType: 'blob' });
  }

  scheduleExport(req: DataExportScheduleRequest): Observable<void> {
    const url = `/api/data_export/schedule`;
    // Si email es '', mejor omitirlo para que el backend use el del usuario
    const payload: any = { ...req };
    if (!payload.email) {
      delete payload.email;
    }
    return this.http.post<void>(url, payload);
  }

  // Futuro:
  // deleteSchedule(jobId: string): Observable<void> {
  //   return this.http.delete<void>(`/api/data_export/schedule/${jobId}`);
  // }
}
