import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CertificateService, CsrResponse } from '../../services/certificate.service';

@Component({
  selector: 'app-upload-csr',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './upload-csr.html',
})
export class UploadCsrComponent {
  csrPem = '';
  caSerialNumber = '';
  validTo = '';

  loading = false;
  result: CsrResponse | null = null;
  error = '';

  constructor(
    private certService: CertificateService,
    private router: Router,
  ) {}

  goBack(): void {
    this.router.navigate(['/dashboard']);
  }

  onFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (e) => {
      this.csrPem = (e.target?.result as string) || '';
    };
    reader.readAsText(file);
  }

  submit(): void {
    if (!this.csrPem || !this.caSerialNumber || !this.validTo) {
      this.error = 'CSR PEM, CA Serial Number and Valid To are required.';
      return;
    }

    this.loading = true;
    this.error = '';
    this.result = null;

    this.certService.uploadCsr({
      csrPem: this.csrPem,
      caSerialNumber: this.caSerialNumber,
      validTo: this.validTo + ':00',
    }).subscribe({
      next: (res) => {
        this.result = res;
        this.loading = false;
      },
      error: (err) => {
        this.error = err.error?.message || 'Failed to sign CSR. Check the PEM format, CA serial and validity period.';
        this.loading = false;
      },
    });
  }

  downloadCert(): void {
    if (!this.result) return;
    const blob = new Blob([this.result.certificatePem], { type: 'application/x-pem-file' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = this.result.serialNumber + '.crt';
    a.click();
    URL.revokeObjectURL(url);
  }

  reset(): void {
    this.result = null;
    this.error = '';
    this.csrPem = '';
    this.caSerialNumber = '';
    this.validTo = '';
  }
}