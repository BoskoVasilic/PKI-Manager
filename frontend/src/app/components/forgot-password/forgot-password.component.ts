import { Component } from '@angular/core';
import {AuthService} from '../../services/auth.service';
import {FormsModule} from '@angular/forms';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-forgot-password',
  imports: [
    FormsModule,
    RouterLink
  ],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.css',
})
export class ForgotPasswordComponent {
  email = '';
  isLoading = false;
  isEmailSent = false;

  constructor(private authApi: AuthService) {}

  sendResetEmail(): void {
    if (!this.email || this.isLoading) return;
    this.isLoading = true;

    this.authApi.forgotPassword(this.email).subscribe({
      next: () => {
        this.isLoading = false;
        this.isEmailSent = true;
      },
      error: () => {
        this.isLoading = false;
        this.isEmailSent = true;
      }
    });
  }
}
