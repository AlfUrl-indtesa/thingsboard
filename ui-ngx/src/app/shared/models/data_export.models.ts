import { EntityId } from '@shared/models/id/entity-id';
import { TimePageLink } from '@shared/models/page/page-link';

export enum DataExportFormat {
  CSV = 'CSV',
  JSON = 'JSON'
}

export interface DataExportFilter {
  entityId: EntityId;
  keys: string[];
  startTs: number;
  endTs: number;
  interval?: number; // en ms, si agregas downsampling
  aggregation?: 'NONE' | 'MIN' | 'MAX' | 'AVG' | 'SUM';
}

export class DataExportQuery {
  constructor(
    public filter: DataExportFilter,
    public format: DataExportFormat = DataExportFormat.CSV
  ) {}

  public toQuery(): string {
    let query = `/${this.filter.entityId.entityType}/${this.filter.entityId.id}`;
    query += `?keys=${this.filter.keys.map(encodeURIComponent).join(',')}`;
    query += `&startTs=${this.filter.startTs}`;
    query += `&endTs=${this.filter.endTs}`;
    if (this.filter.interval) {
      query += `&interval=${this.filter.interval}`;
    }
    if (this.filter.aggregation) {
      query += `&agg=${this.filter.aggregation}`;
    }
    query += `&format=${this.format}`;
    return query;
  }
}
