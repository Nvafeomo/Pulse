import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiKeyService } from '../../core/api-key.service';
import { describeError } from '../../core/errors';
import { Channel } from '../../core/models';
import { NotificationsService } from '../../core/notifications.service';

@Component({
  selector: 'app-send-page',
  imports: [ReactiveFormsModule],
  templateUrl: './send-page.html',
  styleUrl: './send-page.css',
})
export class SendPage {
  private readonly notifications = inject(NotificationsService);
  private readonly router = inject(Router);
  protected readonly apiKey = inject(ApiKeyService);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: true,
    sms: false,
    recipientEmail: ['', Validators.email],
    recipientPhone: [''],
    subject: [''],
    body: ['', Validators.required],
    idempotencyKey: [''],
  });

  protected submit(): void {
    const value = this.form.getRawValue();
    const channels: Channel[] = [];
    if (value.email) channels.push('EMAIL');
    if (value.sms) channels.push('SMS');

    if (channels.length === 0) {
      this.error.set('Pick at least one channel.');
      return;
    }
    if (value.email && !value.recipientEmail) {
      this.error.set('Email delivery needs a recipient email.');
      return;
    }
    if (value.sms && !value.recipientPhone) {
      this.error.set('SMS delivery needs a recipient phone number.');
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set(null);
    this.notifications
      .create({
        channels,
        body: value.body,
        subject: value.subject || undefined,
        recipientEmail: value.recipientEmail || undefined,
        recipientPhone: value.recipientPhone || undefined,
        idempotencyKey: value.idempotencyKey || undefined,
      })
      .subscribe({
        next: (job) => this.router.navigate(['/jobs', job.jobId]),
        error: (err) => {
          this.error.set(describeError(err));
          this.submitting.set(false);
        },
      });
  }
}
