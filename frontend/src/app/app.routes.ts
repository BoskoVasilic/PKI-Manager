import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login';
import { DashboardComponent } from './pages/dashboard/dashboard';
import { IssueCertificateComponent } from './pages/issue-certificate-ca/issue-certificate-ca';
import { UserCertificatesViewComponent } from './components/user-certificates-view/user-certificates-view';
import { GenerateCertificateComponent } from './pages/generate-certificates/generate-certificate';
import { UploadCsrComponent } from './pages/upload-csr/upload-csr';
import { authGuard } from './guards/auth.guard';
import { ChangePasswordComponent } from './components/change-password/change-password.component';
import { CaUserRegisterComponent } from './components/ca-user-register/ca-user-register.component';
import { IssueCertificateAdminComponent } from './components/issue-certificate-admin/issue-certificate-admin.component';
import { AdminCertificatesViewComponent } from './components/admin-certificates-view/admin-certificates-view.component';
import { ProfileComponent } from './components/profile/profile';
import { CertificateDownloadComponent } from './components/download-certificate/download-certificate';
import {RegisterComponent} from './components/register/register.component';
import {ActivateAccountComponent} from './components/activate-account/activate-account.component';
import {ForgotPasswordComponent} from './components/forgot-password/forgot-password.component';
import {CrlViewComponent} from './components/crl-view/crl-view.component';
import { CaCertificatesViewComponent } from './components/ca-certificates-view/ca-certificates-view';
import {adminGuard} from './guards/admin.guard';
import {userGuard} from './guards/user.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'activate', component: ChangePasswordComponent },
  { path: 'admin/ca-users', component: CaUserRegisterComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/issue-certificate', component: IssueCertificateAdminComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/certificates', component: AdminCertificatesViewComponent, canActivate: [authGuard, adminGuard] },
  { path: 'register', component: RegisterComponent },
  { path: 'verify-account', component: ActivateAccountComponent },
  { path: 'forgot-password', component: ForgotPasswordComponent },
  { path: 'certificates/revoked', component: CrlViewComponent, canActivate: [authGuard, adminGuard] },
  { path: 'ca/certificates', component: CaCertificatesViewComponent, canActivate: [authGuard] },
  {
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [authGuard],
  },
  {
    path: 'profile',
    component: ProfileComponent,
    canActivate: [authGuard],
  },
  {
    path: 'certificates/issue',
    component: IssueCertificateComponent,
    canActivate: [authGuard],
  },
  {
    path: 'certificates',
    component: UserCertificatesViewComponent,
    canActivate: [authGuard],
  },
  {
    path: 'certificates/generate',
    component: GenerateCertificateComponent,
    canActivate: [userGuard],
  },
  {
    path: 'certificates/csr',
    component: UploadCsrComponent,
    canActivate: [userGuard],
  },
  {
    path: 'certificates/download',
    component: CertificateDownloadComponent,
    canActivate: [authGuard],
  },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'dashboard' },
];
