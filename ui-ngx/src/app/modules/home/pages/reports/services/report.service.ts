import { HttpParams } from "@angular/common/http";
import { PageData, ReportSelectableEntity } from "../models/report.models";
import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import {
  GenerateReportRequest,
  GenerateReportResponse,
  ReportExecution,
  ReportTemplate,
} from "../models/report.models";
import { catchError, map } from "rxjs/operators";

@Injectable({
  providedIn: "root",
})
export class ReportService {
  constructor(private http: HttpClient) {}

  getReportTemplates(page = 0, pageSize = 10): Observable<any> {
    return this.http.get(
      `/api/report-templates?page=${page}&pageSize=${pageSize}`,
    );
  }

  getReportTemplate(templateId: string): Observable<ReportTemplate> {
    return this.http.get<ReportTemplate>(`/api/report-templates/${templateId}`);
  }

  saveReportTemplate(template: ReportTemplate): Observable<ReportTemplate> {
    return this.http.post<ReportTemplate>("/api/report-templates", template);
  }

  deleteReportTemplate(templateId: string): Observable<void> {
    return this.http.delete<void>(`/api/report-templates/${templateId}`);
  }

  generateReport(
    templateId: string,
    request: GenerateReportRequest,
  ): Observable<GenerateReportResponse> {
    return this.http.post<GenerateReportResponse>(
      `/api/report-templates/${templateId}/generate`,
      request,
    );
  }

  getReportExecutions(page = 0, pageSize = 20): Observable<any> {
    return this.http.get(
      `/api/report-executions?page=${page}&pageSize=${pageSize}`,
    );
  }

  getReportExecutionsByTemplate(
    templateId: string,
    page = 0,
    pageSize = 10,
  ): Observable<any> {
    return this.http.get(
      `/api/report-executions/template/${templateId}?page=${page}&pageSize=${pageSize}`,
    );
  }

  getReportExecution(executionId: string): Observable<ReportExecution> {
    return this.http.get<ReportExecution>(
      `/api/report-executions/${executionId}`,
    );
  }

  downloadReportExecution(executionId: string): Observable<Blob> {
    return this.http.get(`/api/report-executions/${executionId}/download`, {
      responseType: "blob",
    });
  }

  deleteReportExecution(executionId: string): Observable<void> {
    return this.http.delete<void>(`/api/report-executions/${executionId}`);
  }

  getSelectableEntities(
    entityType: "DEVICE" | "ASSET",
    page: number = 0,
    pageSize: number = 50,
    textSearch: string = "",
    customerId?: string,
  ): Observable<PageData<ReportSelectableEntity>> {
    let params = new HttpParams()
      .set("entityType", entityType)
      .set("page", page)
      .set("pageSize", pageSize);

    if (textSearch) {
      params = params.set("textSearch", textSearch);
    }

    if (customerId) {
      params = params.set("customerId", customerId);
    }

    return this.http.get<PageData<ReportSelectableEntity>>(
      "/api/reports/selectable-entities",
      { params },
    );
  }

  getSelectableEntityKeys(
    entityType: string,
    entityId: string,
  ): Observable<string[]> {
    const params = new HttpParams()
      .set("entityType", entityType)
      .set("entityId", entityId);

    return this.http.get<string[]>("/api/reports/selectable-entity-keys", {
      params,
    });
  }

  getFirstTelemetryTs(
    entityType: string,
    entityId: string,
    keys: string[],
  ): Observable<number | null> {
    const safeKeys = Array.from(new Set((keys || []).filter((key) => !!key)));

    if (!entityType || !entityId || !safeKeys.length) {
      return of(null);
    }

    const params = new HttpParams()
      .set("keys", safeKeys.join(","))
      .set("startTs", "0")
      .set("endTs", String(Date.now()))
      .set("interval", "0")
      .set("limit", "1")
      .set("agg", "NONE")
      .set("orderBy", "ASC");

    return this.http.get<Record<string, Array<{ ts: number; value: any }>>>(
      `/api/plugins/telemetry/${entityType}/${entityId}/values/timeseries`,
      { params },
    ).pipe(
      map((data) => {
        let firstTs: number | null = null;

        Object.values(data || {}).forEach((points) => {
          (points || []).forEach((point) => {
            if (point?.ts && (firstTs === null || point.ts < firstTs)) {
              firstTs = point.ts;
            }
          });
        });

        return firstTs;
      }),
      catchError(() => of(null)),
    );
  }
}
