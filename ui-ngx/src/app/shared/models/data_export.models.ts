import { EntityId } from '@shared/models/id/entity-id';

export enum DataExportFormat { CSV = 'CSV', JSON = 'JSON' }

export interface DataExportFilter {
  entityId: EntityId;
  keys: string[];
  startTs: number;
  endTs: number;
  interval?: number; // ms
  aggregation?: 'NONE' | 'MIN' | 'MAX' | 'AVG' | 'SUM';
}

export class DataExportQuery {
  constructor(public filter: DataExportFilter,
              public format: DataExportFormat = DataExportFormat.CSV) {}
  public toQuery(): string {
    const { entityId, keys, startTs, endTs, interval, aggregation } = this.filter;
    let q = `/${entityId.entityType}/${entityId.id}`;
    q += `?keys=${keys.map(encodeURIComponent).join(',')}`;
    q += `&startTs=${startTs}&endTs=${endTs}`;
    if (interval) q += `&interval=${interval}`;
    if (aggregation) q += `&agg=${aggregation}`;
    q += `&format=${this.format}`;
    return q;
  }
}
