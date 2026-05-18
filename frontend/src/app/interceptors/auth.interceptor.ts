import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  const authReq = token
    ? req.clone({ headers: req.headers.set('Authorization', `Bearer ${token}`) })
    : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !req.url.includes('/auth/refresh')) {
        return authService.refresh().pipe(
          switchMap((response) => {
            const retried = req.clone({
              headers: req.headers.set('Authorization', `Bearer ${response.accessToken}`),
            });
            return next(retried);
          }),
          catchError((refreshError) => {
            return throwError(() => refreshError);
          })
        );
      }
      return throwError(() => error);
    })
  );
};