///
/// Copyright © 2016-2026 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
///

import { NgModule } from "@angular/core";

import { RouterModule, Routes } from "@angular/router";

import { ReportsPageComponent } from "./pages/reports-page.component";

import {
  ReportExecutionsPageComponent,
} from "./pages/report-executions-page.component";

import { ReportsAccessGuard } from "./reports-access.guard";

const routes: Routes = [
  {
    path: "reports",
    component: ReportsPageComponent,
    canActivate: [
      ReportsAccessGuard,
    ],
  },
  {
    path: "reports/executions",
    component: ReportExecutionsPageComponent,
    canActivate: [
      ReportsAccessGuard,
    ],
  },
];

@NgModule({
  imports: [
    RouterModule.forChild(
      routes,
    ),
  ],
  exports: [
    RouterModule,
  ],
})
export class ReportsRoutingModule {
}
