import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login';
import { DashboardComponent } from './pages/dashboard/dashboard';
import { CertificatesComponent } from './pages/certificates/certificates';
import { authGuard } from './guards/auth.guard';
import {ChangePasswordComponent} from './components/change-password/change-password.component';
import {CaUserRegisterComponent} from './components/ca-user-register/ca-user-register.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'activate', component: ChangePasswordComponent },
  { path: 'admin/register-ca', component: CaUserRegisterComponent}
  {
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [authGuard],
  },
  {
    path: 'certificates',
    component: CertificatesComponent,
    canActivate: [authGuard],
  },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' },
];
