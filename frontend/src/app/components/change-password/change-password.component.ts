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
    { label: 'Minimalno 8 karaktera', met: false },
    { label: 'Barem jedno veliko slovo', met: false },
    { label: 'Barem jedan broj', met: false },
    { label: 'Barem jedan specijalni karakter', met: false },
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
    if (this.passwordStrength <= 1) return 'Slaba lozinka';
    if (this.passwordStrength === 2) return 'Srednja lozinka';
    if (this.passwordStrength === 3) return 'Jaka lozinka';
    return 'Vrlo jaka lozinka';
  }

  onSubmit(): void {
    if (this.form.password !== this.form.confirmPassword) return;
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
          this.errorMessage = err?.error?.message || 'Greška pri aktivaciji. Pokušajte ponovo.';
        }
      }
    });
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}
