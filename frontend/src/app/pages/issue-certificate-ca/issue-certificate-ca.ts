import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, FormArray } from '@angular/forms';
import { Router } from '@angular/router';
import { CertificateService, CertificateDto, IssueCertificateRequest } from '../../services/certificate.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-issue-certificate',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './issue-certificate-ca.html'
})
export class IssueCertificateComponent implements OnInit {
  form!: FormGroup;
  issuers: CertificateDto[] = [];
  selectedIssuer: CertificateDto | null = null;

  isLoading = false;
  isLoadingIssuers = true;
  successMessage = '';
  errorMessage = '';

  today: string = new Date().toISOString().split('T')[0];

  // Extensions available to CA users.
  // keyCertSign and basicConstraintsCA are toggled automatically based on isCA,
  // matching the same logic as the admin form's onTypeChange().
  readonly EXTENSIONS = [
    { key: 'cRLSign',           label: 'cRLSign',          caOnly: false },
    { key: 'digitalSignature',  label: 'digitalSignature', caOnly: false },
    { key: 'keyEncipherment',   label: 'keyEncipherment',  caOnly: false },
    { key: 'serverAuth',        label: 'serverAuth (EKU)', caOnly: false },
  ];

  constructor(
    private fb: FormBuilder,
    private certService: CertificateService,
    private router: Router,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.buildForm();
    this.loadIssuers();
  }

  private buildForm(): void {
    const nextYear = new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    const organizationName = this.authService.getCurrentUserOrganization();

    this.form = this.fb.group({
      commonName:          ['', [Validators.required, Validators.maxLength(64)]],
      organization:        [{ value: organizationName, disabled: true }, [Validators.required, Validators.maxLength(64)]],
      organizationalUnit:  ['', Validators.maxLength(64)],
      country:             ['', [Validators.required, Validators.pattern(/^[A-Z]{2}$/)]],
      email:               ['', [Validators.required, Validators.email]],
      validFrom:           [this.today, Validators.required],
      validTo:             [nextYear, Validators.required],
      issuerSerialNumber:  ['', Validators.required],
      isCA:                [false],
      keyUsages:           this.fb.array([]),
    });
  }

  private loadIssuers(): void {
    this.isLoadingIssuers = true;
    this.certService.getAvailableIssuers().subscribe({
      next: (issuers) => {
        this.issuers = issuers;
        this.isLoadingIssuers = false;
      },
      error: (err) => {
        console.error('ISSUER ERROR:', err);
        this.errorMessage = 'Failed to load available issuers.';
        this.isLoadingIssuers = false;
      },
    });
  }


  onIssuerChange(serialNumber: string): void {
    this.selectedIssuer = this.issuers.find(i => i.serialNumber === serialNumber) ?? null;
    if (this.selectedIssuer && this.form.value.validTo > this.selectedIssuer.validTo) {
      this.form.patchValue({ validTo: this.selectedIssuer.validTo.split('T')[0] });
    }
  }

  // ── isCA toggle (mirrors admin onTypeChange) ──────────────────────────────

  onIsCAChange(value: boolean): void {
    this.form.get('isCA')?.setValue(value);
    // When CA: auto-enable keyCertSign + basicConstraints; when END_ENTITY: disable both
    // This matches admin's onTypeChange() behaviour exactly.
    if (!value) {
      // Remove keyCertSign from keyUsages if present
      const arr = this.keyUsagesArray;
      const idx = arr.controls.findIndex(c => c.value === 'KEY_CERT_SIGN');
      if (idx >= 0) arr.removeAt(idx);
    }
  }

  // ── Key usage helpers ─────────────────────────────────────────────────────

  get keyUsagesArray(): FormArray {
    return this.form.get('keyUsages') as FormArray;
  }

  toggleKeyUsage(value: string): void {
    const arr = this.keyUsagesArray;
    const idx = arr.controls.findIndex(c => c.value === value);
    if (idx >= 0) {
      arr.removeAt(idx);
    } else {
      arr.push(this.fb.control(value));
    }
  }

  isKeyUsageSelected(value: string): boolean {
    return this.keyUsagesArray.controls.some(c => c.value === value);
  }

  // ── X500Name preview (mirrors admin's getX500Preview) ────────────────────

  getX500Preview(): string {
    const v = this.form.getRawValue();
    const parts: string[] = [];
    if (v.commonName)         parts.push(`CN=${v.commonName}`);
    if (v.organizationalUnit) parts.push(`OU=${v.organizationalUnit}`);
    if (v.organization)       parts.push(`O=${v.organization}`);
    if (v.country)            parts.push(`C=${v.country}`);
    if (v.email)              parts.push(`E=${v.email}`);
    return parts.length > 0 ? parts.join(', ') : 'CN=..., O=..., C=...';
  }

  // ── Submit ────────────────────────────────────────────────────────────────

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.successMessage = '';
    this.errorMessage = '';

    const val = this.form.getRawValue();
    const isCA: boolean = val.isCA;

    // Build keyUsages: if isCA, always include KEY_CERT_SIGN (mirrors admin keyCertSign auto-set)
    const keyUsages: string[] = [...val.keyUsages];
    if (isCA && !keyUsages.includes('KEY_CERT_SIGN')) {
      keyUsages.push('KEY_CERT_SIGN');
    }

    const request: IssueCertificateRequest = {
      commonName:          val.commonName,
      organization:        val.organization,
      organizationalUnit:  val.organizationalUnit || '',
      country:             val.country,
      email:               val.email,
      validFrom:           val.validFrom,
      validTo:             val.validTo,
      issuerSerialNumber:  val.issuerSerialNumber,
      type:                isCA ? 'INTERMEDIATE' : 'END_ENTITY',
      keyUsages,
      isCa:                isCA,
    };

    this.certService.issueCertificate(request).subscribe({
      next: (cert) => {
        this.isLoading = false;
        this.successMessage = `Certificate issued successfully. Serial: ${cert.serialNumber}`;
        this.form.reset();
        this.buildForm();
        this.selectedIssuer = null;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.error?.message || 'Failed to issue certificate. Please check your inputs.';
      },
    });
  }

  hasError(field: string): boolean {
    const c = this.form.get(field);
    return !!(c && c.invalid && c.touched);
  }

  goToDashboard(): void {
    this.router.navigate(['/dashboard']);
  }
}