import { Component, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CertificateService, CertificateDto, CsrResponse } from '../../services/certificate.service';

@Component({
  selector: 'app-upload-csr',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './upload-csr.html',
})
export class UploadCsrComponent implements OnInit {
  csrPem = '';
  selectedCaSerial = '';
  validTo = '';

  availableCas: CertificateDto[] = [];
  loadingCas = true;
  loading = false;
  result: CsrResponse | null = null;
  error = '';

  // Custom dropdown state
  caDropdownOpen = false;

  // Drag-and-drop state
  isDragging = false;

  constructor(
    private certService: CertificateService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.certService.listAvailableCas().subscribe({
      next: (cas) => { this.availableCas = cas; this.loadingCas = false; },
      error: () => { this.error = 'Failed to load available CA certificates.'; this.loadingCas = false; },
    });
  }


  toggleDropdown(): void {
    this.caDropdownOpen = !this.caDropdownOpen;
  }

  selectCa(ca: CertificateDto): void {
    this.selectedCaSerial = ca.serialNumber;
    this.caDropdownOpen = false;
    this.validTo = ''; // reset when CA changes so max constraint refreshes
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!(event.target as Element).closest('[data-ca-dropdown]')) {
      this.caDropdownOpen = false;
    }
  }


  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = false;
    const file = event.dataTransfer?.files?.[0];
    if (file) this.readFile(file);
  }

  onFileChange(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.readFile(file);
  }

  private readFile(file: File): void {
    const reader = new FileReader();
    reader.onload = (e) => { this.csrPem = (e.target?.result as string) || ''; };
    reader.readAsText(file);
  }


  get selectedCa(): CertificateDto | undefined {
    return this.availableCas.find(c => c.serialNumber === this.selectedCaSerial);
  }

  get maxValidTo(): string {
    const ca = this.selectedCa;
    return ca ? new Date(ca.validTo).toISOString().slice(0, 16) : '';
  }


  submit(): void {
    if (!this.csrPem.trim())      { this.error = 'Please paste or upload a PEM-encoded CSR.'; return; }
    if (!this.selectedCaSerial)   { this.error = 'Please select a CA certificate.'; return; }
    if (!this.validTo)            { this.error = 'Please specify the certificate expiry date.'; return; }

    this.loading = true;
    this.error = '';
    this.result = null;

    this.certService.uploadCsr({
      csrPem: this.csrPem,
      caSerialNumber: this.selectedCaSerial,
      validTo: this.validTo + ':00',
    }).subscribe({
      next: (res) => { this.result = res; this.loading = false; },
      error: (err) => {
        this.error = err.error?.message || 'Failed to sign CSR. Check the PEM format, CA selection and validity period.';
        this.loading = false;
      },
    });
  }

  downloadCert(): void {
    if (!this.result) return;
    const blob = new Blob([this.result.certificatePem], { type: 'application/x-pem-file' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = this.result.serialNumber + '.crt'; a.click();
    URL.revokeObjectURL(url);
  }

  reset(): void {
    this.result = null; this.error = ''; this.csrPem = '';
    this.selectedCaSerial = ''; this.validTo = '';
  }

  goBack(): void { this.router.navigate(['/dashboard']); }
}