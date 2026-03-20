import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  DataExportPreviewRequest,
  DataExportPreviewResponse,
  DataExportRequest,
  DataExportScheduleRequest
} from './data-export.models';

@Injectable({
  providedIn: 'root'
})
export class DataExportService {

  constructor(private http: HttpClient) {}

  getInitialPreview(): Observable<DataExportPreviewResponse> {
    return this.http.post<DataExportPreviewResponse>(
      '/api/data-export/preview',
      {
        deviceIds: [],
        includeCalculatedFields: true,
        includeAttributes: true
      }
    );
  }

  preview(request: DataExportPreviewRequest): Observable<DataExportPreviewResponse> {
    return this.http.post<DataExportPreviewResponse>('/api/data-export/preview', request);
  }

  exportCsv(request: DataExportRequest): Observable<Blob> {
    return this.http.post('/api/data-export/csv', request, {
      responseType: 'blob' as 'json'
    }) as Observable<Blob>;
  }

  saveSchedule(request: DataExportScheduleRequest): Observable<any> {
    return this.http.post('/api/data-export/schedule', request);
  }

  getSchedule(): Observable<any> {
    return this.http.get('/api/data-export/schedule');
  }
}