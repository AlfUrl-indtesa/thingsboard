/*
 * Data Export Routing Module
 * Ubicación: ui-ngx/src/app/modules/home/pages/data-export/data-exportrouting.module.ts
 */
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DataExportPageComponent } from './data_export_page.component';
const routes: Routes = [
{
path: '',
component: DataExportPageComponent,
data: {
auth: ['CUSTOMER_USER'],
title: 'data-export.title'
}
}
];
@NgModule({
imports: [RouterModule.forChild(routes)],
exports: [RouterModule]
})
export class DataExportRoutingModule {}