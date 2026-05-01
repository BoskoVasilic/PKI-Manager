import {ChangeDetectorRef, Component} from '@angular/core';
import {Router} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {NgClass} from '@angular/common';
import {UserService} from '../../services/user.service';

interface Organization {
  id: number;
  name: string;
}

@Component({
  selector: 'app-ca-user-register',
  imports: [
    FormsModule,
    NgClass,
  ],
  templateUrl: './ca-user-register.component.html',
  styleUrl: './ca-user-register.component.css',
})
export class CaUserRegisterComponent {
  organizations: Organization[] = [];
  orgMode: 'existing' | 'new' = 'existing';

  isLoading = false;
  isSuccess = false;
  errorMessage = '';

  form = {
    email: '',
    firstName: '',
    lastName: '',
    organizationId: '',
    organizationName: '',
  };

  constructor(
    private userService: UserService,
    private router: Router,
    private ref: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadOrganizations();
  }

  loadOrganizations(): void {
    this.userService.getOrganizations().subscribe({
      next: (orgs) => { this.organizations = orgs; this.ref.detectChanges(); },
      error: (err) => console.error('Greška pri učitavanju organizacija', err)
    });
  }

  onSubmit(): void {
    if (this.isLoading) return;
    this.errorMessage = '';
    this.isLoading = true;

    const payload = {
      email: this.form.email,
      firstName: this.form.firstName,
      lastName: this.form.lastName,
      organizationId: this.orgMode === 'existing' ? this.form.organizationId : null,
      organizationName: this.orgMode === 'new' ? this.form.organizationName : null,
    };

    this.userService.createCaUser(payload).subscribe({
      next: () => {
        this.isLoading = false;
        this.isSuccess = true;
        this.ref.detectChanges();
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.error?.message || 'Greška pri kreiranju CA korisnika.';
        this.ref.detectChanges();
      }
    });
  }

  resetForm(): void {
    this.isSuccess = false;
    this.errorMessage = '';
    this.form = {
      email: '',
      firstName: '',
      lastName: '',
      organizationId: '',
      organizationName: '',
    };
  }

  goBack(): void {
    this.router.navigate(['/admin/certificates']);
  }
}
