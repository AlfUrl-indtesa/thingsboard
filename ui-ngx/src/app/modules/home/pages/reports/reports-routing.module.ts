import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ReportsPageComponent } from './pages/reports-page.component';
import { ReportExecutionsPageComponent } from './pages/report-executions-page.component';

const routes: Routes = [
  {
    path: 'reports',
    component: ReportsPageComponent
  },
  {
    path: 'reports/executions',
    component: ReportExecutionsPageComponent
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class ReportsRoutingModule {}