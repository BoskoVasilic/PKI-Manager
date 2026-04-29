import { Routes } from '@angular/router';
import {ChangePasswordComponent} from './components/change-password/change-password.component';
import {CaUserRegisterComponent} from './components/ca-user-register/ca-user-register.component';

export const routes: Routes = [
  { path: 'activate', component: ChangePasswordComponent },
  { path: 'admin/register-ca', component: CaUserRegisterComponent}
];
