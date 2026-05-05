import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {UserService} from '../../services/user.service';
import {FormsModule} from '@angular/forms';
import {NgClass} from '@angular/common';

@Component({
  selector: 'app-change-password',
  imports: [
    FormsModule,
    NgClass
  ],
  templateUrl: './change-password.component.html',
  styleUrl: './change-password.component.css',
})
export class ChangePasswordComponent implements OnInit {
  token = '';
  tokenInvalid = false;
  isSuccess = false;
  isLoading = false;
  errorMessage = '';
  showPassword = false;
  passwordStrength = 0;

  form = {
    password: '',
    confirmPassword: '',
  };

  passwordRequirements = [
    { label: 'Minimum 8 characters', met: false },
    { label: 'At least one capital letter', met: false },
    { label: 'At least one number', met: false },
    { label: 'At least one special character', met: false },
  ];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') || '';
    if (!this.token) {
      this.tokenInvalid = true;
      return;
    }

    this.userService.validateActivationToken(this.token).subscribe({
      error: () => { this.tokenInvalid = true; }
    });
  }

  checkPasswordStrength(): void {
    const p = this.form.password;
    this.passwordRequirements[0].met = p.length >= 8;
    this.passwordRequirements[1].met = /[A-Z]/.test(p);
    this.passwordRequirements[2].met = /[0-9]/.test(p);
    this.passwordRequirements[3].met = /[^A-Za-z0-9]/.test(p);
    this.passwordStrength = this.passwordRequirements.filter(r => r.met).length;
  }

  checkPasswordRequirements(): boolean {
    const p = this.form.password;
    this.passwordRequirements[0].met = p.length >= 8;
    this.passwordRequirements[1].met = /[A-Z]/.test(p);
    this.passwordRequirements[2].met = /[0-9]/.test(p);
    this.passwordRequirements[3].met = /[^A-Za-z0-9]/.test(p);

    return this.passwordRequirements.filter(r => r.met).length == 4;
  }

  getStrengthColor(): string {
    if (this.passwordStrength <= 1) return 'bg-red-500';
    if (this.passwordStrength === 2) return 'bg-yellow-500';
    if (this.passwordStrength === 3) return 'bg-blue-500';
    return 'bg-emerald-500';
  }

  getStrengthTextColor(): string {
    if (this.passwordStrength <= 1) return 'text-red-400';
    if (this.passwordStrength === 2) return 'text-yellow-400';
    if (this.passwordStrength === 3) return 'text-blue-400';
    return 'text-emerald-400';
  }

  getStrengthLabel(): string {
    if (this.passwordStrength <= 1) return 'Weak password';
    if (this.passwordStrength === 2) return 'Medium password';
    if (this.passwordStrength === 3) return 'Strong password';
    return 'Very strong password';
  }

  onSubmit(): void {
    if (this.form.password !== this.form.confirmPassword) return;
    if (!this.checkPasswordRequirements()) return;
    if (this.isLoading) return;

    this.errorMessage = '';
    this.isLoading = true;

    this.userService.activateCaUser(this.token, this.form.password).subscribe({
      next: () => {
        this.isLoading = false;
        this.isSuccess = true;
      },
      error: (err) => {
        this.isLoading = false;
        if (err?.status === 400 || err?.status === 404) {
          this.tokenInvalid = true;
        } else {
          this.errorMessage = err?.error?.message || 'Error while activating the user. Please try again.';
        }
      }
    });
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}
