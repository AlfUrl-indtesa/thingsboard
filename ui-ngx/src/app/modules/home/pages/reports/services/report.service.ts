import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  GenerateReportRequest,
  GenerateReportResponse,
  ReportExecution,
  ReportTemplate
} from '../models/report.models';

@Injectable({
  providedIn: 'root'
})
export class ReportService {

  constructor(private http: HttpClient) {}

  getReportTemplates(page = 0, pageSize = 10): Observable<any> {
    return this.http.get(`/api/report-templates?page=${page}&pageSize=${pageSize}`);
  }

  getReportTemplate(templateId: string): Observable<ReportTemplate> {
    return this.http.get<ReportTemplate>(`/api/report-templates/${templateId}`);
  }

  saveReportTemplate(template: ReportTemplate): Observable<ReportTemplate> {
    return this.http.post<ReportTemplate>('/api/report-templates', template);
  }

  deleteReportTemplate(templateId: string): Observable<void> {
    return this.http.delete<void>(`/api/report-templates/${templateId}`);
  }

  generateReport(templateId: string, request: GenerateReportRequest): Observable<GenerateReportResponse> {
    return this.http.post<GenerateReportResponse>(`/api/report-templates/${templateId}/generate`, request);
  }

  getReportExecutions(page = 0, pageSize = 10): Observable<any> {
    return this.http.get(`/api/report-executions?page=${page}&pageSize=${pageSize}`);
  }

  getReportExecutionsByTemplate(templateId: string, page = 0, pageSize = 10): Observable<any> {
    return this.http.get(`/api/report-executions/template/${templateId}?page=${page}&pageSize=${pageSize}`);
  }

  getReportExecution(executionId: string): Observable<ReportExecution> {
    return this.http.get<ReportExecution>(`/api/report-executions/${executionId}`);
  }

  downloadReport(executionId: string): Observable<Blob> {
    return this.http.get(`/api/report-executions/${executionId}/download`, {
      responseType: 'blob'
    });
  }
}