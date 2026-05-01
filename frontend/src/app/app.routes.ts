import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login';
import { DashboardComponent } from './pages/dashboard/dashboard';
import { IssueCertificateComponent } from './pages/issue-certificate-ca/issue-certificate-ca';
import { CertificatesComponent } from './pages/certificates/certificates';
import { authGuard } from './guards/auth.guard';
import {ChangePasswordComponent} from './components/change-password/change-password.component';
import {CaUserRegisterComponent} from './components/ca-user-register/ca-user-register.component';
import {AuthService} from './services/auth.service';
import {IssueCertificateAdminComponent} from './components/issue-certificate-admin/issue-certificate-admin.component';
import {AdminCertificatesViewComponent} from './components/admin-certificates-view/admin-certificates-view.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'activate', component: ChangePasswordComponent },
  { path: 'admin/ca-users', component: CaUserRegisterComponent, canActivate: [authGuard] },
  { path: 'admin/issue-certificate', component: IssueCertificateAdminComponent, canActivate: [authGuard] },
  { path: 'admin/certificates', component: AdminCertificatesViewComponent, canActivate: [authGuard] },
  { path: 'admin/register-ca', component: CaUserRegisterComponent},
  {
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'issue-certificate',
        component: IssueCertificateComponent,
      },
    ],
  },
  {
    path: 'certificates',
    component: CertificatesComponent,
    canActivate: [authGuard],
  },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' },
];
