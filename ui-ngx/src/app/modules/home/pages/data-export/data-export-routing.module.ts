import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Authority } from '@shared/models/authority.enum';
import { MenuId } from '@core/services/menu.models';
import { DataExportComponent } from './data-export.component';

const routes: Routes = [
  {
    path: 'data-export',
    component: DataExportComponent,
    data: {
      auth: [Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
      title: 'data_export.title',
      breadcrumb: {
        menuId: MenuId.data_export
      }
    }
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class DataExportRoutingModule { }