export interface ReportTemplate {
  id?: string;
  name: string;
  description?: string;
  type: 'operational' | 'executive' | 'compressed_air';
  entityFilter: ReportEntityFilter;
  sections: ReportSectionConfig[];
  branding?: ReportBrandingConfig;
  defaultTimeRange?: ReportTimeRangeConfig;
  outputFormat: 'pdf';
  enabled: boolean;
  createdTime?: number;
}

export interface ReportEntityFilter {
  entityType: 'DEVICE' | 'ASSET' | 'ENTITY_GROUP';
  entityIds?: string[];
  entityGroupId?: string;
}

export interface ReportSectionConfig {
  key: string;
  title: string;
  enabled: boolean;
  order: number;
  config?: any;
}

export interface ReportBrandingConfig {
  logoUrl?: string;
  primaryColor?: string;
  secondaryColor?: string;
  companyName?: string;
  footerText?: string;
}

export interface ReportTimeRangeConfig {
  mode: 'LAST_24_HOURS' | 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'CUSTOM';
}