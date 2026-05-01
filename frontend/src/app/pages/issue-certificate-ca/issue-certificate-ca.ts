import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, FormArray } from '@angular/forms';
import { Router } from '@angular/router';
import { CertificateService, CertificateDto, IssueCertificateRequest } from '../../services/certificate.service';

@Component({
  selector: 'app-issue-certificate',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './issue-certificate-ca.html'
})
export class IssueCertificateComponent implements OnInit {
  form!: FormGroup;
  issuers: CertificateDto[] = [];
  isLoading = false;
  isLoadingIssuers = true;
  successMessage = '';
  errorMessage = '';

  readonly KEY_USAGES = [
    { value: 'DIGITAL_SIGNATURE', label: 'Digital Signature' },
    { value: 'KEY_CERT_SIGN', label: 'Key Cert Sign' },
    { value: 'CRL_SIGN', label: 'CRL Sign' },
    { value: 'KEY_ENCIPHERMENT', label: 'Key Encipherment' },
    { value: 'DATA_ENCIPHERMENT', label: 'Data Encipherment' },
    { value: 'KEY_AGREEMENT', label: 'Key Agreement' },
    { value: 'NON_REPUDIATION', label: 'Non Repudiation' },
  ];

  constructor(
    private fb: FormBuilder,
    private certService: CertificateService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.buildForm();
    this.loadIssuers();
  }

  private buildForm(): void {
    const today = new Date().toISOString().split('T')[0];
    const nextYear = new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];

    this.form = this.fb.group({
      commonName: ['', [Validators.required, Validators.maxLength(64)]],
      organization: ['', [Validators.required, Validators.maxLength(64)]],
      organizationalUnit: ['', Validators.maxLength(64)],
      country: ['', [Validators.required, Validators.pattern(/^[A-Z]{2}$/)]],
      email: ['', [Validators.required, Validators.email]],
      validFrom: [today, Validators.required],
      validTo: [nextYear, Validators.required],
      issuerSerialNumber: ['', Validators.required],
      isCa: [false],
      keyUsages: this.fb.array([]),
    });
  }

  private loadIssuers(): void {
    this.isLoadingIssuers = true;
    this.certService.getAvailableIssuers().subscribe({
      next: (issuers) => {
        this.issuers = issuers;
        this.isLoadingIssuers = false;
      },
      error: () => {
        this.errorMessage = 'Failed to load available issuers.';
        this.isLoadingIssuers = false;
      },
    });
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

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.successMessage = '';
    this.errorMessage = '';

    const val = this.form.value;
    const request: IssueCertificateRequest = {
      commonName: val.commonName,
      organization: val.organization,
      organizationalUnit: val.organizationalUnit || '',
      country: val.country,
      email: val.email,
      validFrom: val.validFrom,
      validTo: val.validTo,
      issuerSerialNumber: val.issuerSerialNumber,
      type: val.isCa ? 'INTERMEDIATE' : 'END_ENTITY',
      keyUsages: val.keyUsages,
      isCa: val.isCa,
    };

    this.certService.issueCertificate(request).subscribe({
      next: (cert) => {
        this.isLoading = false;
        this.successMessage = `Certificate issued successfully. Serial: ${cert.serialNumber}`;
        this.form.reset();
        this.buildForm();
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