/*
 * Data Export Module
 * Ubicación: ui-ngx/src/app/modules/home/pages/data_export/data_export.module.ts
 */
import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';

import { SharedModule } from '@shared/shared.module';
import { HomeComponentsModule } from '@home/components/home-components.module';

import { DataExportRoutingModule } from './data_export_routing.module';
import { DataExportPageComponent } from './data_export_page.component';

@NgModule({
  declarations: [DataExportPageComponent],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    TranslateModule,
    SharedModule,
    HomeComponentsModule,
    DataExportRoutingModule
  ]
})
export class DataExportModule {}