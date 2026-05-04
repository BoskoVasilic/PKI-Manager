import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

type SetupStep = 'idle' | 'scan' | 'confirm' | 'done';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './profile.html',
})
export class ProfileComponent implements OnInit {
  userEmail = '';
  userName = '';
  userInitial = '';

  twoFaEnabled = false;

  // Setup flow
  setupStep: SetupStep = 'idle';
  qrCodeDataUri = '';
  tempSecret = '';
  confirmCode = '';

  // Disable flow
  showDisableForm = false;
  disableCode = '';

  // UI state
  isLoading = false;
  successMessage = '';
  errorMessage = '';

  constructor(private authService: AuthService) {}

  ngOnInit(): void {
    const token = this.authService.getToken();
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        this.userEmail = payload.sub || payload.email || '';
        this.userName = payload.name || this.userEmail.split('@')[0] || 'User';
        this.userInitial = this.userName.charAt(0).toUpperCase();
        this.twoFaEnabled = payload.twoFaEnabled ?? false;
      } catch {
        this.userInitial = 'U';
      }
    }
  }

  logout(): void {
    this.authService.logout();
  }

  startSetup(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.authService.setup2Fa().subscribe({
      next: (response) => {
        this.isLoading = false;
        this.qrCodeDataUri = response.qrCodeDataUri;
        this.tempSecret = response.secret;
        this.setupStep = 'scan';
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'Failed to start setup. Please try again.';
      },
    });
  }

  onScanned(): void {
    this.setupStep = 'confirm';
  }

  onConfirmCode(): void {
    if (!this.confirmCode || this.confirmCode.length !== 6) {
      this.errorMessage = 'Enter the 6-digit code from your authenticator app.';
      return;
    }
    this.isLoading = true;
    this.errorMessage = '';

    this.authService.enable2Fa(this.tempSecret, this.confirmCode).subscribe({
      next: () => {
        this.isLoading = false;
        this.twoFaEnabled = true;
        this.setupStep = 'done';
        this.successMessage = 'Two-factor authentication is now active.';
        this.confirmCode = '';
        this.tempSecret = '';
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.status === 401 ? 'Invalid code. Try again.' : 'Something went wrong.';
        this.confirmCode = '';
      },
    });
  }

  onDisable(): void {
    if (!this.disableCode || this.disableCode.length !== 6) {
      this.errorMessage = 'Enter your current authenticator code to confirm.';
      return;
    }
    this.isLoading = true;
    this.errorMessage = '';

    this.authService.disable2Fa(this.disableCode).subscribe({
      next: () => {
        this.isLoading = false;
        this.twoFaEnabled = false;
        this.showDisableForm = false;
        this.disableCode = '';
        this.setupStep = 'idle';
        this.successMessage = 'Two-factor authentication has been disabled.';
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.status === 401 ? 'Invalid code. Try again.' : 'Something went wrong.';
        this.disableCode = '';
      },
    });
  }

  onCodeInput(event: Event, field: 'confirmCode' | 'disableCode'): void {
    const input = event.target as HTMLInputElement;
    input.value = input.value.replace(/\D/g, '').slice(0, 6);
    this[field] = input.value;
  }

  resetSetup(): void {
    this.setupStep = 'idle';
    this.confirmCode = '';
    this.tempSecret = '';
    this.qrCodeDataUri = '';
    this.errorMessage = '';
  }
}