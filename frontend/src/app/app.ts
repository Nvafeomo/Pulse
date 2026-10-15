import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ApiKeyService } from './core/api-key.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly apiKey = inject(ApiKeyService);
  protected readonly draftKey = signal('');

  protected saveKey(): void {
    this.apiKey.set(this.draftKey());
    this.draftKey.set('');
  }
}
