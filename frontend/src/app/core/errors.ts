import { HttpErrorResponse } from '@angular/common/http';

/** Turns an API error into a short message for the UI. */
export function describeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Could not reach the Pulse API. Is the Spring Boot app running on port 8080?';
    }
    if (error.status === 401) {
      return 'The API key was rejected. Check the key in the header bar.';
    }
    const body = error.error as { error?: string; fields?: Record<string, string> } | null;
    if (body?.fields) {
      return Object.entries(body.fields)
        .map(([field, message]) => `${field}: ${message}`)
        .join(' · ');
    }
    if (body?.error) {
      return body.error;
    }
    return `Request failed (${error.status}).`;
  }
  return 'Something went wrong.';
}
