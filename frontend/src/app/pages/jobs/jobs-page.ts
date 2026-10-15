import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiKeyService } from '../../core/api-key.service';
import { describeError } from '../../core/errors';
import { NotificationPage, NotificationStatus, STATUSES } from '../../core/models';
import { NotificationsService } from '../../core/notifications.service';
import { StatusBadge } from '../../shared/status-badge';

const PAGE_SIZE = 15;

@Component({
  selector: 'app-jobs-page',
  imports: [DatePipe, RouterLink, StatusBadge],
  templateUrl: './jobs-page.html',
  styleUrl: './jobs-page.css',
})
export class JobsPage implements OnInit {
  private readonly notifications = inject(NotificationsService);
  protected readonly apiKey = inject(ApiKeyService);

  protected readonly statuses = STATUSES;
  protected readonly status = signal<NotificationStatus | null>(null);
  protected readonly page = signal(0);
  protected readonly result = signal<NotificationPage | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected filterBy(status: NotificationStatus | null): void {
    this.status.set(status);
    this.page.set(0);
    this.load();
  }

  protected goTo(page: number): void {
    this.page.set(page);
    this.load();
  }

  protected load(): void {
    if (!this.apiKey.hasKey()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.notifications.list(this.page(), PAGE_SIZE, this.status()).subscribe({
      next: (result) => {
        this.result.set(result);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(describeError(err));
        this.loading.set(false);
      },
    });
  }

  protected recipient(job: { recipientEmail: string | null; recipientPhone: string | null }): string {
    return [job.recipientEmail, job.recipientPhone].filter(Boolean).join(', ') || '—';
  }
}
