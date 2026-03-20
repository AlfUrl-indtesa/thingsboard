import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';

import { SharedModule } from '@shared/shared.module';
import { HomeComponentsModule } from '@modules/home/components/home-components.module';

import { DataExportRoutingModule } from './data-export-routing.module';
import { DataExportComponent } from './data-export.component';

@NgModule({
  declarations: [
    DataExportComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    SharedModule,
    HomeComponentsModule,
    DataExportRoutingModule
  ]
})
export class DataExportModule { }