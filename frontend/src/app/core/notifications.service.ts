import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './config';
import {
  CreateNotificationRequest,
  NotificationJob,
  NotificationPage,
  NotificationStatus,
} from './models';

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/notifications`;

  list(page: number, size: number, status?: NotificationStatus | null): Observable<NotificationPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<NotificationPage>(this.url, { params });
  }

  get(jobId: string): Observable<NotificationJob> {
    return this.http.get<NotificationJob>(`${this.url}/${jobId}`);
  }

  create(request: CreateNotificationRequest): Observable<NotificationJob> {
    return this.http.post<NotificationJob>(this.url, request);
  }
}
