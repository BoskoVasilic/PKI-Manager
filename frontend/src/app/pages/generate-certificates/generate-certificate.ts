import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CertificateService, CsrResponse } from '../../services/certificate.service';

@Component({
  selector: 'app-generate-certificate',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './generate-certificate.html',
})
export class GenerateCertificateComponent {
  // Form fields
  cn = '';
  organization = '';
  organizationUnit = '';
  country = '';
  email = '';
  caSerialNumber = '';
  validFrom = '';
  validTo = '';

  // State
  loading = false;
  result: CsrResponse | null = null;
  error = '';
  privateKeyDownloaded = false;

  constructor(
    private certService: CertificateService,
    private router: Router,
  ) {}

  goBack(): void {
    this.router.navigate(['/dashboard']);
  }

  submit(): void {
    if (!this.cn || !this.caSerialNumber || !this.validFrom || !this.validTo) {
      this.error = 'CN, CA Serial Number, Valid From and Valid To are required.';
      return;
    }

    this.loading = true;
    this.error = '';
    this.result = null;
    this.privateKeyDownloaded = false;

    this.certService.autogenerate({
      cn: this.cn,
      organization: this.organization || undefined,
      organizationUnit: this.organizationUnit || undefined,
      country: this.country || undefined,
      email: this.email || undefined,
      caSerialNumber: this.caSerialNumber,
      validFrom: this.validFrom + ':00', // append seconds for LocalDateTime
      validTo: this.validTo + ':00',
    }).subscribe({
      next: (res) => {
        this.result = res;
        this.loading = false;
      },
      error: (err) => {
        this.error = err.error?.message || 'Failed to generate certificate. Check CA serial number and validity period.';
        this.loading = false;
      },
    });
  }

  downloadPem(content: string, filename: string): void {
    const blob = new Blob([content], { type: 'application/x-pem-file' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
    if (filename.includes('key')) this.privateKeyDownloaded = true;
  }

  reset(): void {
    this.result = null;
    this.error = '';
    this.cn = '';
    this.organization = '';
    this.organizationUnit = '';
    this.country = '';
    this.email = '';
    this.caSerialNumber = '';
    this.validFrom = '';
    this.validTo = '';
    this.privateKeyDownloaded = false;
  }
}