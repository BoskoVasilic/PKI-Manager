import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login';
import { DashboardComponent } from './pages/dashboard/dashboard';
import { CertificatesComponent } from './pages/certificates/certificates';
import { GenerateCertificateComponent } from './pages/generate-certificates/generate-certificate';
import { UploadCsrComponent } from './pages/upload-csr/upload-csr';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
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
  {
    path: 'certificates/generate',
    component: GenerateCertificateComponent,
    canActivate: [authGuard],
  },
  {
    path: 'certificates/csr',
    component: UploadCsrComponent,
    canActivate: [authGuard],
  },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' },
];