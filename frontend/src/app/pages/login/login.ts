import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

type Step = 'credentials' | 'twofa';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './login.html',
})
export class LoginComponent {
  step: Step = 'credentials';

  email = '';
  password = '';

  totpCode = '';

  errorMessage = '';
  isLoading = false;

  constructor(private authService: AuthService, private router: Router) {}
  
  onSubmit(): void {
    if (!this.email || !this.password) {
      this.errorMessage = 'Please fill in all fields.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.login({ email: this.email, password: this.password }).subscribe({
      next: (response) => {
        this.isLoading = false;
        if (response.twoFaRequired) {
          this.step = 'twofa';
        } else {
          this.router.navigate(['/dashboard']);
        }
      },
      error: (err) => {
        this.isLoading = false;
        if (err.status === 401) {
          this.errorMessage = 'Invalid email or password.';
        } else if (err.status === 403) {
          this.errorMessage = 'Account not activated. Please check your email.';
        } else {
          this.errorMessage = 'Something went wrong. Please try again.';
        }
      },
    });
  }

 onVerifyTwoFa(): void {
    if (!this.totpCode || this.totpCode.length !== 6) {
      this.errorMessage = 'Please enter the 6-digit code from your authenticator app.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.verifyTwoFa(this.totpCode).subscribe({
      next: () => {
        this.isLoading = false;
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.isLoading = false;
        if (err.status === 401) {
          this.errorMessage = 'Invalid code. Please try again.';
        } else {
          this.errorMessage = 'Something went wrong. Please try again.';
        }
        this.totpCode = '';
      },
    });
  }

  onTotpInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    input.value = input.value.replace(/\D/g, '').slice(0, 6);
    this.totpCode = input.value;
  }

  goBack(): void {
    this.step = 'credentials';
    this.totpCode = '';
    this.errorMessage = '';
  }
}