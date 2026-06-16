import {CanActivateFn, Router} from '@angular/router';
import {AuthService} from '../services/auth.service';
import {inject} from '@angular/core';

export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.getCurrentUserRole() === 'ADMIN') {
    return true;
  }

  router.navigate(['/dashboard']);
  return false;
};
