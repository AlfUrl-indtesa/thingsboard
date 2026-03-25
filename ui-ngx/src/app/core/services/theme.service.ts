import { Injectable } from '@angular/core';

export type AppTheme = 'light' | 'dark';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {

  private readonly storageKey = 'tb-ui-theme';
  private currentTheme: AppTheme = 'light';

  initTheme(): void {
    const savedTheme = this.getSavedTheme();
    if (savedTheme) {
      this.setTheme(savedTheme);
      return;
    }

    // Light por default
    this.setTheme('light');
  }

  setTheme(theme: AppTheme): void {
    this.currentTheme = theme;
    localStorage.setItem(this.storageKey, theme);

    const body = document.body;
    body.classList.remove('tb-light', 'tb-dark');
    body.classList.add(theme === 'dark' ? 'tb-dark' : 'tb-light');
  }

  toggleTheme(): void {
    this.setTheme(this.currentTheme === 'dark' ? 'light' : 'dark');
  }

  isDark(): boolean {
    return this.currentTheme === 'dark';
  }

  getTheme(): AppTheme {
    return this.currentTheme;
  }

  private getSavedTheme(): AppTheme | null {
    const value = localStorage.getItem(this.storageKey);
    return value === 'dark' || value === 'light' ? value : null;
  }
}