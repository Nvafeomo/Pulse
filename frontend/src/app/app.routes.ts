import { Routes } from '@angular/router';
import { JobDetailPage } from './pages/job-detail/job-detail-page';
import { JobsPage } from './pages/jobs/jobs-page';
import { SendPage } from './pages/send/send-page';

export const routes: Routes = [
  { path: '', component: JobsPage, title: 'Jobs · Pulse' },
  { path: 'send', component: SendPage, title: 'Send · Pulse' },
  { path: 'jobs/:id', component: JobDetailPage, title: 'Job · Pulse' },
  { path: '**', redirectTo: '' },
];
