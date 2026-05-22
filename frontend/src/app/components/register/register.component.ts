import {ChangeDetectorRef, Component} from '@angular/core';
import {Router, RouterLink} from '@angular/router';
import {WebCryptoService} from '../../services/webCrypto.service';
import {AuthService} from '../../services/auth.service';
import {FormsModule} from '@angular/forms';
import {NgClass} from '@angular/common';

@Component({
  selector: 'app-register',
  imports: [
    FormsModule,
    NgClass,
    RouterLink
  ],
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

  passwordStrength = 0;
  passwordRequirements = [
    { label: 'Min. 8 characters', met: false },
    { label: 'Uppercase letter',   met: false },
    { label: 'Lowercase letter',   met: false },
    { label: 'Number',             met: false },
    { label: 'Special character',  met: false },
    { label: 'Max. 128 chars',     met: true  },
  ];

  constructor(
    private authApi: AuthService,
    private webCrypto: WebCryptoService,
    private router: Router,
    private cdf: ChangeDetectorRef
  ) {}

  onPasswordChange(): void {
    const p = this.form.password;
    this.passwordRequirements[0].met = p.length >= 8;
    this.passwordRequirements[1].met = /[A-Z]/.test(p);
    this.passwordRequirements[2].met = /[a-z]/.test(p);
    this.passwordRequirements[3].met = /[0-9]/.test(p);
    this.passwordRequirements[4].met = /[^A-Za-z0-9]/.test(p);
    this.passwordRequirements[5].met = p.length <= 128;
    this.passwordStrength = this.passwordRequirements.filter(r => r.met).length;
  }

  checkPasswordRequirements(): boolean {
    const p = this.form.password;
    this.passwordRequirements[0].met = p.length >= 8;
    this.passwordRequirements[1].met = /[A-Z]/.test(p);
    this.passwordRequirements[2].met = /[a-z]/.test(p);
    this.passwordRequirements[3].met = /[0-9]/.test(p);
    this.passwordRequirements[4].met = /[^A-Za-z0-9]/.test(p);
    this.passwordRequirements[5].met = p.length <= 128;

    return this.passwordRequirements.filter(r => r.met).length == 6;
  }

  getStrengthBarColor(): string {
    if (this.passwordStrength <= 3) return 'bg-red-500';
    if (this.passwordStrength <= 4) return 'bg-yellow-500';
    if (this.passwordStrength <= 5) return 'bg-blue-500';
    return 'bg-emerald-500';
  }

  getStrengthTextColor(): string {
    if (this.passwordStrength <= 3) return 'text-red-400';
    if (this.passwordStrength <= 4) return 'text-yellow-400';
    if (this.passwordStrength <= 5) return 'text-blue-400';
    return 'text-emerald-400';
  }

  getStrengthLabel(): string {
    if (this.passwordStrength <= 3) return 'Weak password';
    if (this.passwordStrength <= 4) return 'Medium password';
    if (this.passwordStrength <= 5) return 'Strong password';
    return 'Very strong password';
  }

  onPrivateKeyFileUpload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = async (e) => {
      this.form.publicKeyPem = e.target?.result as string;
      await this.validatePublicKey()
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
      this.cdf.detectChanges()
    }, 600);
  }

  private async validatePublicKey(): Promise<void> {
    const isValid = await this.webCrypto.validatePublicKey(this.form.publicKeyPem);
    this.publicKeyStatus = isValid ? 'valid' : 'invalid';
    this.cdf.detectChanges()
  }

  onSubmit(): void {
    if (this.isLoading) return;
    if (!this.checkPasswordRequirements()) return;
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
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.error?.message || 'Registration error. Please try again.';
      }
    });
  }
}
