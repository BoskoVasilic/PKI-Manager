import { ChangeDetectorRef, Component } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { NgClass } from '@angular/common';
import { WebCryptoService } from '../../services/webCrypto.service';
import { AuthService } from '../../services/auth.service';
import { PasswordService } from '../../services/password.service';
import { PasswordStrengthComponent } from '../password-strength/password-strength.component';

@Component({
  selector: 'app-register',
  imports: [FormsModule, NgClass, RouterLink, PasswordStrengthComponent],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css',
})
export class RegisterComponent {
  form = {
    firstName: '',
    lastName: '',
    email: '',
    organizationName: '',
    password: '',
    confirmPassword: '',
    publicKeyPem: '',
  };

  isLoading = false;
  isSuccess = false;
  errorMessage = '';
  submittedEmail = '';
  showPassword = false;

  publicKeyStatus: 'idle' | 'validating' | 'valid' | 'invalid' = 'idle';
  private publicKeyTimer: any;

  private lastPasswordAnalysis: import('../../services/password.service').PasswordAnalysis | null = null;
  isPasswordAcceptable = false;
  isCheckingPassword = false;
  private passwordDebounce: any;

  constructor(
    private authApi: AuthService,
    private webCrypto: WebCryptoService,
    private passwordService: PasswordService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  onPasswordChange(): void {
    this.isPasswordAcceptable = false;
    this.isCheckingPassword = true;
    clearTimeout(this.passwordDebounce);

    if (!this.form.password) {
      this.isCheckingPassword = false;
      this.cdr.detectChanges();
      return;
    }

    this.passwordDebounce = setTimeout(async () => {
      this.lastPasswordAnalysis = await this.passwordService.analyze(this.form.password);
      this.isPasswordAcceptable = this.lastPasswordAnalysis.isAcceptable;
      this.isCheckingPassword = false;
      this.cdr.detectChanges();
    }, 600);
  }

  get canSubmit(): boolean {
    return (
      !!this.form.firstName &&
      !!this.form.lastName &&
      !!this.form.email &&
      !!this.form.organizationName &&
      this.isPasswordAcceptable &&
      !this.isCheckingPassword &&
      this.form.password === this.form.confirmPassword &&
      this.publicKeyStatus === 'valid' &&
      !this.isLoading
    );
  }

  onPrivateKeyFileUpload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = async (e) => {
      this.form.publicKeyPem = e.target?.result as string;
      await this.validatePublicKey();
    };
    reader.readAsText(file);
  }

  onPublicKeyChange(): void {
    this.publicKeyStatus = 'idle';
    clearTimeout(this.publicKeyTimer);
    if (!this.form.publicKeyPem.trim()) return;
    this.publicKeyStatus = 'validating';
    this.publicKeyTimer = setTimeout(async () => {
      const isValid = await this.webCrypto.validatePublicKey(this.form.publicKeyPem);
      this.publicKeyStatus = isValid ? 'valid' : 'invalid';
      this.cdr.detectChanges();
    }, 600);
  }

  private async validatePublicKey(): Promise<void> {
    const isValid = await this.webCrypto.validatePublicKey(this.form.publicKeyPem);
    this.publicKeyStatus = isValid ? 'valid' : 'invalid';
    this.cdr.detectChanges();
  }

  onSubmit(): void {
    if (!this.canSubmit) return;
    this.errorMessage = '';
    this.isLoading = true;
    this.submittedEmail = this.form.email;

    this.authApi.register({
      email: this.form.email,
      password: this.form.password,
      confirmPassword: this.form.confirmPassword,
      firstName: this.form.firstName,
      lastName: this.form.lastName,
      organizationName: this.form.organizationName,
      publicKeyPem: this.form.publicKeyPem,
    }).subscribe({
      next: () => {
        this.isLoading = false;
        this.isSuccess = true;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.error?.message || 'Registration error. Please try again.';
        this.cdr.detectChanges();
      }
    });
  }
}
