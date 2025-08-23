import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '@core/guards/auth.guard';
import { DataExportPageComponent } from './data_export_page.component';

const routes: Routes = [
  {
    path: 'data_export',
    component: DataExportPageComponent,
    canActivate: [AuthGuard],
    data: {
      title: 'data_export.export',
      breadcrumb: { skip: false }
    }
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class DataExportRoutingModule {}
