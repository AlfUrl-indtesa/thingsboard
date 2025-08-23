/*
 * Data Export HTTP service (RAW, sin agregación)
 */
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
  format?: DataExportFormat; // default CSV
}

export interface DataExportScheduleRequest {
  jobId?: string;
  allDevices: boolean;
  deviceIds?: string[];
  keys: string[];
  lookbackMs: number;
  cron: string;
  email: string;
}

@Injectable({ providedIn: 'root' })
export class DataExportService {
  constructor(private http: HttpClient) {}

  exportBulk(req: DataExportBulkRequest): Observable<Blob> {
    let params = new HttpParams()
      .set('deviceIds', req.deviceIds.join(','))
      .set('keys', req.keys.join(','))
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
    return this.http.post<void>(url, req);
  }
}
