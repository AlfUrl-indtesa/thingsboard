export interface ExportableDevice {
  id: string;
  name: string;
  type?: string;
  label?: string;
}

export interface DataExportPreviewRequest {
  deviceIds: string[];
  includeCalculatedFields: boolean;
  includeAttributes?: boolean;
}

export interface DataExportPreviewResponse {
  devices: ExportableDevice[];
  keys: string[];
  attributeKeys?: string[];
  suggestedEmail?: string | null;
  emailRequired: boolean;
  defaultStartTs?: number | null;
  defaultEndTs: number;
}

export interface DataExportRequest {
  deviceIds: string[];
  keys: string[];
  attributeKeys?: string[];
  includeCalculatedFields: boolean;
  includeAttributes: boolean;
  startTs?: number | null;
  endTs: number;
  autoDetectOldestTs: boolean;
  format: 'csv';
}

export interface DataExportScheduleRequest {
  enabled: boolean;
  allDevices: boolean;
  deviceIds: string[];
  keys: string[];
  attributeKeys?: string[];
  includeCalculatedFields: boolean;
  includeAttributes: boolean;
  email: string;
  period: 'WEEKLY';
  timeOfDay: string;
  timezone?: string;
  mode: 'FULL' | 'INCREMENTAL';
}

export interface DataExportSchedule {
  enabled: boolean;
  allDevices: boolean;
  deviceIds: string[];
  keys: string[];
  attributeKeys?: string[];
  includeCalculatedFields: boolean;
  includeAttributes: boolean;
  email: string;
  period: 'WEEKLY';
  timeOfDay: string;
  timezone?: string;
  mode: 'FULL' | 'INCREMENTAL';
}