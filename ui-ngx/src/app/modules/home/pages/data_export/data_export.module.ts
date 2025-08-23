import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule }  from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatDividerModule } from '@angular/material/divider';

import { DataExportRoutingModule } from './data_export_routing.module';
import { DataExportPageComponent } from './data_export_page.component';

@NgModule({
  declarations: [DataExportPageComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatButtonModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatSlideToggleModule,
    MatDividerModule,
    DataExportRoutingModule
  ]
})
export class DataExportModule {}
