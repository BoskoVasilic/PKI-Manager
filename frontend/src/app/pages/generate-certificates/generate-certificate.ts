import { Component, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CertificateService, CertificateDto, CsrResponse } from '../../services/certificate.service';

@Component({
  selector: 'app-generate-certificate',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './generate-certificate.html',
})
export class GenerateCertificateComponent implements OnInit {
  // X500Name fields
  cn = '';
  organization = '';
  organizationUnit = '';
  country = '';
  email = '';

  // Issuance parameters
  selectedCaSerial = '';
  validFrom = '';
  validTo = '';

  // State
  availableCas: CertificateDto[] = [];
  loadingCas = true;
  loading = false;
  result: CsrResponse | null = null;
  error = '';

  // Custom dropdown state
  caDropdownOpen = false;

  /** True once a result containing a private key has been dismissed */
  keyDismissed = false;

  constructor(
    private certService: CertificateService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    // Pre-fill validFrom with now
    this.validFrom = new Date().toISOString().slice(0, 16);

    this.certService.listAvailableCas().subscribe({
      next: (cas) => {
        this.availableCas = cas;
        this.loadingCas = false;
      },
      error: () => {
        this.error = 'Failed to load available CA certificates.';
        this.loadingCas = false;
      },
    });
  }

  get selectedCa(): CertificateDto | undefined {
    return this.availableCas.find(c => c.serialNumber === this.selectedCaSerial);
  }

  get maxValidTo(): string {
    const ca = this.selectedCa;
    if (!ca) return '';
    return new Date(ca.validTo).toISOString().slice(0, 16);
  }

  get minValidFrom(): string {
    const ca = this.selectedCa;
    if (!ca) return new Date().toISOString().slice(0, 16);
    // validFrom cannot be before the CA's own validFrom
    const caFrom = new Date(ca.validFrom);
    const now = new Date();
    return (caFrom > now ? caFrom : now).toISOString().slice(0, 16);
  }


  toggleDropdown(): void {
    this.caDropdownOpen = !this.caDropdownOpen;
  }

  selectCa(ca: CertificateDto): void {
    this.selectedCaSerial = ca.serialNumber;
    this.caDropdownOpen = false;
    this.validTo = '';
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!(event.target as Element).closest('[data-ca-dropdown]')) {
      this.caDropdownOpen = false;
    }
  }

  submit(): void {
    if (!this.cn.trim()) {
      this.error = 'Common Name (CN) is required.';
      return;
    }
    if (!this.selectedCaSerial) {
      this.error = 'Please select a CA certificate.';
      return;
    }
    if (!this.validFrom || !this.validTo) {
      this.error = 'Both validity dates are required.';
      return;
    }
    if (new Date(this.validFrom) >= new Date(this.validTo)) {
      this.error = 'Valid From must be before Valid To.';
      return;
    }

    this.loading = true;
    this.error = '';
    this.result = null;

    this.certService.autogenerate({
      cn: this.cn,
      organization: this.organization || undefined,
      organizationUnit: this.organizationUnit || undefined,
      country: this.country || undefined,
      email: this.email || undefined,
      caSerialNumber: this.selectedCaSerial,
      validFrom: this.validFrom + ':00',
      validTo: this.validTo + ':00',
    }).subscribe({
      next: (res) => {
        this.result = res;
        this.loading = false;
      },
      error: (err) => {
        this.error = err.error?.message || 'Failed to generate certificate. Check your input and try again.';
        this.loading = false;
      },
    });
  }

  downloadCert(): void {
    if (!this.result) return;
    this.downloadBlob(this.result.certificatePem, this.result.serialNumber + '.crt', 'application/x-pem-file');
  }

  downloadKeystore(): void {
    if (!this.result?.keystoreBase64) return;
    // Decode Base64 → binary → Blob → .jks file
    const binary = atob(this.result.keystoreBase64);
    const bytes  = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    const buffer = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
    this.downloadBlob(buffer, this.result.serialNumber + '.jks', 'application/octet-stream', true);
  }

  private downloadBlob(content: string | ArrayBuffer, filename: string, type: string, binary = false): void {
    const blob = binary ? new Blob([content], { type }) : new Blob([content], { type });
    const url = URL.createObjectURL(blob);
    const a   = document.createElement('a');
    a.href     = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  reset(): void {
    this.result = null;
    this.error = '';
    this.cn = '';
    this.organization = '';
    this.organizationUnit = '';
    this.country = '';
    this.email = '';
    this.selectedCaSerial = '';
    this.validFrom = new Date().toISOString().slice(0, 16);
    this.validTo = '';
    this.keyDismissed = false;
  }

  goBack(): void {
    this.router.navigate(['/dashboard']);
  }
}