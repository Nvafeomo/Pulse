import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, switchMap, takeWhile, timer } from 'rxjs';
import { describeError } from '../../core/errors';
import { NotificationJob } from '../../core/models';
import { NotificationsService } from '../../core/notifications.service';
import { StatusBadge } from '../../shared/status-badge';

const POLL_INTERVAL_MS = 2000;

@Component({
  selector: 'app-job-detail-page',
  imports: [DatePipe, RouterLink, StatusBadge],
  templateUrl: './job-detail-page.html',
  styleUrl: './job-detail-page.css',
})
export class JobDetailPage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly notifications = inject(NotificationsService);
  private subscription?: Subscription;

  protected readonly job = signal<NotificationJob | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const jobId = this.route.snapshot.paramMap.get('id')!;

    // Poll until the job reaches a final state so the page shows delivery
    // without a manual refresh.
    this.subscription = timer(0, POLL_INTERVAL_MS)
      .pipe(
        switchMap(() => this.notifications.get(jobId)),
        takeWhile((job) => job.status === 'PENDING' || job.status === 'PROCESSING', true),
      )
      .subscribe({
        next: (job) => this.job.set(job),
        error: (err) => this.error.set(describeError(err)),
      });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
