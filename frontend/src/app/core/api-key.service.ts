import { Injectable, computed, signal } from '@angular/core';

const STORAGE_KEY = 'pulse.apiKey';

/**
 * Holds the API key for this browser tab. sessionStorage keeps it across
 * page reloads but clears it when the tab closes.
 */
@Injectable({ providedIn: 'root' })
export class ApiKeyService {
  private readonly key = signal<string | null>(this.read());

  readonly apiKey = this.key.asReadonly();
  readonly hasKey = computed(() => !!this.key());

  set(value: string): void {
    const trimmed = value.trim();
    this.key.set(trimmed || null);
    try {
      if (trimmed) {
        sessionStorage.setItem(STORAGE_KEY, trimmed);
      } else {
        sessionStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      // Storage can be unavailable (private mode); the in-memory key still works.
    }
  }

  clear(): void {
    this.set('');
  }

  private read(): string | null {
    try {
      return sessionStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  }
}
