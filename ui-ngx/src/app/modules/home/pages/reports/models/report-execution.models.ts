export interface ReportExecution {
  id: string;
  templateId: string;
  templateName: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
  requestedBy?: string;
  startedTime?: number;
  finishedTime?: number;
  fileId?: string;
  fileName?: string;
  errorMessage?: string;
}