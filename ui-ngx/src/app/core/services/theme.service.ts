import { Injectable, Inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly storageKey = 'tb-ui-theme';
  private isDarkMode = false;

  constructor(@Inject(DOCUMENT) private document: Document) {
    this.initTheme();
  }

  initTheme(): void {
    const savedTheme = localStorage.getItem(this.storageKey);
    if (savedTheme === 'dark') {
      this.setDark(true);
    } else if (savedTheme === 'light') {
      this.setDark(false);
    } else {
      // Por defecto oscuro o claro según el sistema
      const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
      this.setDark(prefersDark);
    }
  }

  toggleTheme(): void {
    this.setDark(!this.isDarkMode);
  }

  isDark(): boolean {
    return this.isDarkMode;
  }

  private setDark(isDark: boolean): void {
    this.isDarkMode = isDark;
    localStorage.setItem(this.storageKey, isDark ? 'dark' : 'light');
    
    const html = this.document.documentElement;
    if (isDark) {
      html.classList.add('dark');
      // Mantenemos tb-dark por si algún script nativo de Thingsboard lo busca
      this.document.body.classList.add('tb-dark'); 
      this.document.body.classList.remove('tb-light');
    } else {
      html.classList.remove('dark');
      this.document.body.classList.add('tb-light');
      this.document.body.classList.remove('tb-dark');
    }
  }
}