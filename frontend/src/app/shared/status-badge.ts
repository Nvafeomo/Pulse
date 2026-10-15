import { Component, input } from '@angular/core';
import { NotificationStatus } from '../core/models';

@Component({
  selector: 'app-status-badge',
  template: `<span class="badge" [attr.data-status]="status()">{{ status() }}</span>`,
  styles: `
    .badge {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.02em;
      background: var(--badge-pending-bg);
      color: var(--badge-pending-fg);
    }
    .badge[data-status='PROCESSING'] {
      background: var(--badge-processing-bg);
      color: var(--badge-processing-fg);
    }
    .badge[data-status='DELIVERED'] {
      background: var(--badge-delivered-bg);
      color: var(--badge-delivered-fg);
    }
    .badge[data-status='FAILED'] {
      background: var(--badge-failed-bg);
      color: var(--badge-failed-fg);
    }
  `,
})
export class StatusBadge {
  readonly status = input.required<NotificationStatus>();
}
