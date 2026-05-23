import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { NgClass } from '@angular/common';
import { WebCryptoService } from '../../services/webCrypto.service';
import { AuthService } from '../../services/auth.service';
import { PasswordService, PasswordAnalysis } from '../../services/password.service';
import { PasswordStrengthComponent } from '../password-strength/password-strength.component';

type Mode = 'registration' | 'password-reset';
type PageState = 'loading' | 'invalid-token' | 'verify' | 'set-password' | 'success';

@Component({
  selector: 'app-activate-account',
  imports: [NgClass, FormsModule, PasswordStrengthComponent],
  templateUrl: './activate-account.component.html',
  styleUrl: './activate-account.component.css',
})
export class ActivateAccountComponent implements OnInit {

  mode: Mode = 'registration';
  pageState: PageState = 'loading';
  errorMessage = '';
  isLoading = false;

  private token = '';
  private encryptedChallenge = '';
  private decryptedChallenge = '';

  privateKeyPem = '';
  privateKeyStatus: 'idle' | 'valid' | 'invalid' = 'idle';

  newPassword = '';
  confirmPassword = '';
  showPassword = false;

  isPasswordAcceptable = false;
  isCheckingPassword = false;
  private passwordDebounce: any;

  get passwordsMatch(): boolean {
    return this.newPassword === this.confirmPassword;
  }

  get canSubmitPassword(): boolean {
    return (
      this.isPasswordAcceptable &&
      !this.isCheckingPassword &&
      this.passwordsMatch &&
      !!this.confirmPassword &&
      !this.isLoading
    );
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authApi: AuthService,
    private webCrypto: WebCryptoService,
    private passwordService: PasswordService,
    private cdr: ChangeDetectorRef,
  ) {}


  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') || '';
    const typeParam = this.route.snapshot.queryParamMap.get('type');
    this.mode = typeParam === 'password-reset' ? 'password-reset' : 'registration';

    if (!this.token) {
      this.pageState = 'invalid-token';
      return;
    }

    this.fetchChallenge();
  }

  private fetchChallenge(): void {
    const request$ = this.mode === 'registration'
      ? this.authApi.getActivationChallenge(this.token)
      : this.authApi.getForgotPasswordChallenge(this.token);

    request$.subscribe({
      next: (res) => {
        this.encryptedChallenge = res.encryptedChallenge;
        this.pageState = 'verify';
      },
      error: () => {
        this.pageState = 'invalid-token';
      }
    });
  }

  onPrivateKeyFileUpload(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = async (e) => {
      this.privateKeyPem = e.target?.result as string;
      await this.validatePrivateKey();
    };
    reader.readAsText(file);
  }

  async onPrivateKeyChange(): Promise<void> {
    if (!this.privateKeyPem.trim()) {
      this.privateKeyStatus = 'idle';
      return;
    }
    await this.validatePrivateKey();
  }

  private async validatePrivateKey(): Promise<void> {
    const isValid = await this.webCrypto.validatePrivateKey(this.privateKeyPem);
    this.privateKeyStatus = isValid ? 'valid' : 'invalid';
    this.cdr.detectChanges();
  }

  async decryptAndVerify(): Promise<void> {
    if (this.isLoading || this.privateKeyStatus !== 'valid') return;
    this.isLoading = true;
    this.errorMessage = '';

    try {
      this.decryptedChallenge = await this.webCrypto.decryptChallenge(
        this.encryptedChallenge,
        this.privateKeyPem
      );

      this.privateKeyPem = '';
      this.privateKeyStatus = 'idle';
      this.isLoading = false;

      if (this.mode === 'registration') {
        this.submitActivation();
      } else {
        this.pageState = 'set-password';
      }

    } catch (err: any) {
      this.isLoading = false;
      this.errorMessage = err?.message || 'Decryption failed. Make sure you used the correct private key.';
    }
  }

  private submitActivation(): void {
    this.isLoading = true;

    this.authApi.activateAccount(this.token, this.decryptedChallenge).subscribe({
      next: () => {
        this.isLoading = false;
        this.decryptedChallenge = '';
        this.pageState = 'success';
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.error?.message || 'Verification failed. Please check the private key.';
        this.pageState = 'verify';
        this.cdr.detectChanges();
      }
    });
  }

  onPasswordChange(): void {
    this.isPasswordAcceptable = false;
    this.isCheckingPassword = true;
    clearTimeout(this.passwordDebounce);

    if (!this.newPassword) {
      this.isCheckingPassword = false;
      this.cdr.detectChanges();
      return;
    }

    this.passwordDebounce = setTimeout(async () => {
      const analysis = await this.passwordService.analyze(this.newPassword);
      this.isPasswordAcceptable = analysis.isAcceptable;
      this.isCheckingPassword = false;
      this.cdr.detectChanges();
    }, 600);
  }

  submitPasswordReset(): void {
    if (!this.canSubmitPassword || this.isLoading) return;
    this.isLoading = true;
    this.errorMessage = '';

    this.authApi.resetPassword(
      this.token,
      this.decryptedChallenge,
      this.newPassword,
      this.confirmPassword
    ).subscribe({
      next: () => {
        this.isLoading = false;
        this.decryptedChallenge = '';
        this.newPassword = '';
        this.confirmPassword = '';
        this.pageState = 'success';
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.error?.message || 'Password reset failed. Please try again.';
        this.cdr.detectChanges();
      }
    });
  }

  get headerTitle(): string {
    return this.mode === 'registration' ? 'Account activation' : 'Password recovery';
  }

  get headerSubtitle(): string {
    return this.mode === 'registration'
      ? 'Confirm your identity with your private key'
      : 'Confirm your identity to reset your password';
  }

  get successTitle(): string {
    return this.mode === 'registration' ? 'Account activated!' : 'Password changed!';
  }

  get successMessage(): string {
    return this.mode === 'registration'
      ? 'Identity confirmed. You can now log in.'
      : 'Password has been changed successfully. You can now log in.';
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}
