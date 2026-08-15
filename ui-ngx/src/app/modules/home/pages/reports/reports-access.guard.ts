///
/// Copyright © 2016-2026 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
///

import {
  Injectable
} from "@angular/core";

import {
  CanActivate,
  Router,
  UrlTree
} from "@angular/router";

import {
  MatSnackBar
} from "@angular/material/snack-bar";

import {
  Store
} from "@ngrx/store";

import {
  AppState
} from "@core/core.state";

import {
  getCurrentAuthState
} from "@core/auth/auth.selectors";

import {
  Authority
} from "@shared/models/authority.enum";

@Injectable({
  providedIn: "root"
})
export class ReportsAccessGuard
  implements CanActivate {

  constructor(
    private store: Store<AppState>,
    private router: Router,
    private snackBar: MatSnackBar
  ) {
  }

  canActivate():
    boolean | UrlTree {

    const authState =
      getCurrentAuthState(
        this.store
      );

    const authority =
      authState?.authUser?.authority;

    const hasAccess =
      authority ===
        Authority.TENANT_ADMIN ||
      authority ===
        Authority.CUSTOMER_USER;

    if (hasAccess) {
      return true;
    }

    this.snackBar.open(
      "No tienes permisos para acceder a Reportes.",
      "Cerrar",
      {
        duration: 5000,
        horizontalPosition: "center",
        verticalPosition: "top"
      }
    );

    return this.router.parseUrl(
      "/home"
    );
  }
}