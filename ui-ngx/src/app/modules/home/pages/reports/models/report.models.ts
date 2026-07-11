export interface ReportSectionConfig {
  key: string;
  type: string;
  title: string;
  order: number;
  visible: boolean;
  pageBreakBefore: boolean;
  config?: any;
}

export interface ReportEntityFilter {
  scopeType: string;
  entityType?: string;
  entityIds?: Array<{ entityType: string; id: string }>;
  entityGroupId?: string;
  criteria?: any;
}

export interface ReportBrandingConfig {
  companyName?: string;
  logoResourceKey?: string;
  primaryColor?: string;
  secondaryColor?: string;
  accentColor?: string;
  textColor?: string;
  footerText?: string;
  footerLogoResourceKey?: string;
  customerName?: string;
  siteName?: string;

  coverTitle?: string;
  coverSubtitle?: string;

  logoUrl?: string;
  confidentialityText?: string;

  showPageNumbers?: boolean;
  showGeneratedDate?: boolean;
}

export interface ReportTimeRangeConfig {
  mode: string;
  defaultStartTs?: number;
  defaultEndTs?: number;
  lastValue?: number;
  lastUnit?: string;
  allowCustomOverride: boolean;
}

export interface ReportGenerationOptions {
  outputFormat: string;
  paperSize: string;
  orientation: string;
  includeCover: boolean;
  includeSummary: boolean;
  includePageNumbers: boolean;
  includeGeneratedAt: boolean;
  fileNamePattern?: string;
}

export interface ReportTemplate {
  id?: string;
  createdTime?: number;
  tenantId?: { entityType: string; id: string };
  customerId?: { entityType: string; id: string };
  name: string;
  description?: string;
  type: string;
  status: string;
  scopeType: string;
  entityFilter: ReportEntityFilter;
  sections: ReportSectionConfig[];
  branding?: ReportBrandingConfig;
  defaultTimeRange?: ReportTimeRangeConfig;
  generationOptions?: ReportGenerationOptions;
  outputFormat: string;
  system: boolean;
  createdBy?: string;
  updatedTime?: number;
  updatedBy?: string;
}

export interface GenerateReportRequest {
  startTs: number;
  endTs: number;
  entityIds?: Array<{ entityType: string; id: string }>;
  locale?: string;
  timezone?: string;
}

export interface GenerateReportResponse {
  executionId: string;
  status: string;
}

export interface ReportExecution {
  id: string;
  createdTime?: number;
  tenantId?: { entityType: string; id: string };
  customerId?: { entityType: string; id: string };
  templateId: string;
  templateNameSnapshot: string;
  reportType: string;
  status: string;
  requestedBy?: string;
  requestedTime?: number;
  startedTime?: number;
  finishedTime?: number;
  executionRequest?: any;
  payloadSnapshot?: any;
  fileName?: string;
  mimeType?: string;
  storageType?: string;
  filePath?: string;
  externalFileId?: string;
  fileSize?: number;
  checksum?: string;
  errorCode?: string;
  errorMessage?: string;
  executionMetadata?: any;
}

export interface ReportSelectableEntity {
  id: {
    entityType: string;
    id: string;
  };
  name: string;
  label?: string;
  type?: string;
  customerId?: {
    entityType: string;
    id: string;
  };
}

export type ReportChartGranularity = "FULL" | "DAY" | "WEEK" | "MONTH";

export interface ReportVariableStatsConfig {
  min: boolean;
  max: boolean;
  avg: boolean;
  count: boolean;
  sum: boolean;
  first: boolean;
  last: boolean;
  delta: boolean;
}

export type ReportPerformanceDirection =
  | "TARGET_RANGE"
  | "HIGHER_IS_BETTER"
  | "LOWER_IS_BETTER"
  | "NEUTRAL";

export interface ReportVariableRangeConfig {
  enabled: boolean;
  min: number | null;
  max: number | null;
}

export interface ReportVariableAnalysisConfig {
  enabled: boolean;

  expectedRange: ReportVariableRangeConfig;
  warningRange: ReportVariableRangeConfig;

  performanceDirection: ReportPerformanceDirection;

  comparePreviousPeriod: boolean;
  detectTrend: boolean;
  detectOutliers: boolean;

  minimumCoveragePct: number;
}

export interface ReportVariableConfig {
  entityId: {
    entityType: string;
    id: string;
  };
  entityName?: string;
  key: string;
  enabled: boolean;
  label: string;
  unit?: string;
  scale: number;
  offset: number;
  chartEnabled: boolean;
  tableEnabled: boolean;
  granularity: ReportChartGranularity;
  stats: ReportVariableStatsConfig;
  analysis: ReportVariableAnalysisConfig;
}

export interface PageData<T> {
  data: T[];
  totalPages: number;
  totalElements: number;
  hasNext: boolean;
}
