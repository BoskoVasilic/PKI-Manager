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
  validFrom = new Date().toISOString().slice(0, 16);
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
    this.validFrom = new Date().toISOString().slice(0, 16); // reset so min constraint refreshes
    this.validTo = '';
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

  get minValidFrom(): string {
    const ca = this.selectedCa;
    if (!ca) return new Date().toISOString().slice(0, 16);
    const caFrom = new Date(ca.validFrom);
    const now    = new Date();
    return (caFrom > now ? caFrom : now).toISOString().slice(0, 16);
  }


  submit(): void {
    if (!this.csrPem.trim())      { this.error = 'Please paste or upload a PEM-encoded CSR.'; return; }
    if (!this.selectedCaSerial)   { this.error = 'Please select a CA certificate.'; return; }
    if (!this.validFrom)          { this.error = 'Please specify the certificate start date.'; return; }
    if (!this.validTo)            { this.error = 'Please specify the certificate expiry date.'; return; }
    if (new Date(this.validFrom) >= new Date(this.validTo)) {
      this.error = 'Valid From must be before Valid To.'; return;
    }

    this.loading = true;
    this.error = '';
    this.result = null;

    this.certService.uploadCsr({
      csrPem: this.csrPem,
      caSerialNumber: this.selectedCaSerial,
      validFrom: this.validFrom + ':00',
      validTo: this.validTo + ':00',
    }).subscribe({
      next: (res) => { this.result = res; this.loading = false; },
      error: (err) => {
        this.error = err.error?.message || 'Failed to sign CSR. Check the PEM format, CA selection and validity period.';
        this.loading = false;
      },
    });
  }

  goToDownloads(): void {
    this.router.navigate(['/certificates/download']);
  }

  reset(): void {
    this.result = null; this.error = ''; this.csrPem = '';
    this.selectedCaSerial = '';
    this.validFrom = new Date().toISOString().slice(0, 16);
    this.validTo = '';
  }

  goBack(): void { this.router.navigate(['/dashboard']); }
}