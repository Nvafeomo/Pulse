export type Channel = 'EMAIL' | 'SMS';

export type NotificationStatus = 'PENDING' | 'PROCESSING' | 'DELIVERED' | 'FAILED';

export const STATUSES: NotificationStatus[] = ['PENDING', 'PROCESSING', 'DELIVERED', 'FAILED'];

export interface NotificationJob {
  jobId: string;
  status: NotificationStatus;
  channels: Channel[];
  subject: string | null;
  recipientEmail: string | null;
  recipientPhone: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface NotificationPage {
  content: NotificationJob[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateNotificationRequest {
  channels: Channel[];
  subject?: string;
  body: string;
  recipientEmail?: string;
  recipientPhone?: string;
  idempotencyKey?: string;
}
