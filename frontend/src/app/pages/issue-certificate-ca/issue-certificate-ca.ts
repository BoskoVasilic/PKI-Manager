import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, FormArray, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router } from '@angular/router';
import { CertificateService, CertificateDto, IssueCertificateRequest } from '../../services/certificate.service';
import { AuthService } from '../../services/auth.service';

/** Validates that pathLenConstraint is either empty or a non-negative integer. */
function pathLenValidator(control: AbstractControl): ValidationErrors | null {
  const val = control.value;
  if (val === null || val === '' || val === undefined) return null;
  const num = Number(val);
  if (!Number.isInteger(num) || num < 0 || num > 10) {
    return { pathLen: true };
  }
  return null;
}

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
      // null = unlimited (no pathLen extension set); a number means capped at that depth
      pathLenConstraint:   [null, pathLenValidator],
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

  onIsCAChange(value: boolean): void {
    this.form.get('isCA')?.setValue(value);
    const arr = this.keyUsagesArray;

    if (value) {
      // Switching TO Intermediate CA:
      // - ensure KEY_CERT_SIGN is present (mirrors admin auto-enable)
      if (!this.isKeyUsageSelected('KEY_CERT_SIGN')) {
        arr.push(this.fb.control('KEY_CERT_SIGN'));
      }
      // - remove serverAuth: it's an EE (TLS server) extension, not meaningful for a CA
      const serverAuthIdx = arr.controls.findIndex(c => c.value === 'serverAuth');
      if (serverAuthIdx >= 0) arr.removeAt(serverAuthIdx);
      // - remove keyEncipherment: not meaningful for CA certs
      const encipherIdx = arr.controls.findIndex(c => c.value === 'keyEncipherment');
      if (encipherIdx >= 0) arr.removeAt(encipherIdx);
    } else {
      // Switching TO End-Entity:
      // - remove KEY_CERT_SIGN (CAs only)
      const keyCertIdx = arr.controls.findIndex(c => c.value === 'KEY_CERT_SIGN');
      if (keyCertIdx >= 0) arr.removeAt(keyCertIdx);
      // - reset pathLen
      this.form.patchValue({ pathLenConstraint: null });
      // - restore sensible EE default: digitalSignature on
      if (!this.isKeyUsageSelected('digitalSignature')) {
        arr.push(this.fb.control('digitalSignature'));
      }
    }
  }

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

    const keyUsages: string[] = [...val.keyUsages];
    if (isCA && !keyUsages.includes('KEY_CERT_SIGN')) {
      keyUsages.push('KEY_CERT_SIGN');
    }

    // pathLenConstraint: send null when empty (unlimited) or not a CA cert
    const pathLengthConstraint: number =
      isCA && val.pathLenConstraint !== null && val.pathLenConstraint !== ''
        ? Number(val.pathLenConstraint)
        : -1;  // -1 = no limit, matching backend default

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
      pathLengthConstraint,
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