import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL } from './config';
import { ApiKeyService } from './api-key.service';

/** Adds the X-API-Key header to every request bound for the Pulse API. */
export const apiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  const apiKey = inject(ApiKeyService).apiKey();
  if (!apiKey || !req.url.startsWith(API_BASE_URL)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'X-API-Key': apiKey } }));
};
