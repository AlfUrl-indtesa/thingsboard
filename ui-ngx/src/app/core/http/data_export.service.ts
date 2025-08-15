import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DataExportQuery } from '@app/shared/models/data_export.models;

@Injectable({ providedIn: 'root' })
export class DataExportService {
  constructor(private http: HttpClient) {}

  public exportData(query: DataExportQuery): Observable<Blob> {
    const url = `/api/data-export${query.toQuery()}`;
    return this.http.get(url, { responseType: 'blob' });
  }
}
