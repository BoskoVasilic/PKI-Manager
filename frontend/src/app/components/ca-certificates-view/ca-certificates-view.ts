import { Component, OnInit } from '@angular/core';
import { CertificateData, CertificateService } from '../../services/certificate.service';
import { AuthService } from '../../services/auth.service';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DatePipe, NgClass, SlicePipe, Location } from '@angular/common';

@Component({
  selector: 'app-ca-certificates-view',
  imports: [
    FormsModule,
    DatePipe,
    SlicePipe,
    NgClass,
  ],
  templateUrl: './ca-certificates-view.html',
})
export class CaCertificatesViewComponent implements OnInit {
  certificates: CertificateData[] = [];
  filteredCertificates: CertificateData[] = [];

  searchQuery = '';
  typeFilter = '';
  statusFilter = '';

  selectedCert: CertificateData | null = null;
  revokeTarget: CertificateData | null = null;
  revokeReason = '';

  get currentUser(): string {
    return this.authService.getCurrentUserEmail() || 'CA User';
  }

  get activeCertCount(): number {
    return this.certificates.filter(c => !c.revoked && !this.isExpired(c)).length;
  }

  get expiredCertCount(): number {
    return this.certificates.filter(c => !c.revoked && this.isExpired(c)).length;
  }

  get revokedCertCount(): number {
    return this.certificates.filter(c => c.revoked).length;
  }

  constructor(
    private certService: CertificateService,
    private authService: AuthService,
    private router: Router,
    private location: Location,
  ) {}

  ngOnInit(): void {
    this.loadCertificates();
  }

  loadCertificates(): void {
    // CA user endpoint — returns only certificates belonging to the caller's organization
    this.certService.getOrgCertificates().subscribe({
      next: (certs) => {
        this.certificates = certs;
        this.applyFilters();
      },
      error: (err) => console.error('Error loading certificates.', err),
    });
  }

  applyFilters(): void {
    let result = [...this.certificates];

    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      result = result.filter(c =>
        c.commonName.toLowerCase().includes(q) ||
        c.organization.toLowerCase().includes(q) ||
        c.serialNumber.toLowerCase().includes(q),
      );
    }

    if (this.typeFilter) {
      result = result.filter(c => c.type === this.typeFilter);
    }

    if (this.statusFilter === 'active') {
      result = result.filter(c => !c.revoked && !this.isExpired(c));
    } else if (this.statusFilter === 'expired') {
      result = result.filter(c => !c.revoked && this.isExpired(c));
    } else if (this.statusFilter === 'revoked') {
      result = result.filter(c => c.revoked);
    }

    this.filteredCertificates = result;
  }

  isExpired(cert: CertificateData): boolean {
    return new Date(cert.validTo) < new Date();
  }

  getStatusLabel(cert: CertificateData): string {
    if (cert.revoked) return 'Revoked';
    if (this.isExpired(cert)) return 'Expired';
    return 'Active';
  }

  getStatusClass(cert: CertificateData): string {
    if (cert.revoked) return 'text-red-400 border-red-500/30 bg-red-500/10';
    if (this.isExpired(cert)) return 'text-yellow-400 border-yellow-500/30 bg-yellow-500/10';
    return 'text-emerald-400 border-emerald-500/30 bg-emerald-500/10';
  }

  getStatusDotClass(cert: CertificateData): string {
    if (cert.revoked) return 'bg-red-400';
    if (this.isExpired(cert)) return 'bg-yellow-400';
    return 'bg-emerald-400';
  }

  getTypeClass(type: string): string {
    switch (type) {
      case 'ROOT':         return 'text-purple-400 border-purple-500/30 bg-purple-500/10';
      case 'INTERMEDIATE': return 'text-blue-400 border-blue-500/30 bg-blue-500/10';
      default:             return 'text-gray-400 border-gray-600/30 bg-gray-500/10';
    }
  }

  getCertFields(cert: CertificateData): { label: string; value: string }[] {
    return [
      { label: 'Serial Number',  value: cert.serialNumber },
      { label: 'Common Name',    value: cert.commonName },
      { label: 'Organization',   value: cert.organization },
      { label: 'Org. Unit',      value: cert.organizationUnit || '—' },
      { label: 'Country',        value: cert.country },
      { label: 'Email',          value: cert.email || '—' },
      { label: 'Type',           value: cert.type },
      { label: 'Valid From',     value: new Date(cert.validFrom).toLocaleDateString('sr') },
      { label: 'Valid To',       value: new Date(cert.validTo).toLocaleDateString('sr') },
      { label: 'Issuer Serial',  value: cert.issuerSerialNumber || 'Self-signed' },
      { label: 'Status',         value: this.getStatusLabel(cert) },
    ];
  }

  trackById(_index: number, cert: CertificateData): number {
    return cert.id;
  }

  viewCertificate(cert: CertificateData): void {
    this.selectedCert = cert;
  }

  revokeCertificate(cert: CertificateData): void {
    this.revokeTarget = cert;
    this.revokeReason = '';
  }

  confirmRevoke(): void {
    if (!this.revokeTarget || !this.revokeReason) return;

    this.certService.revokeCertificate(this.revokeTarget.serialNumber, this.revokeReason).subscribe({
      next: () => {
        this.revokeTarget!.revoked = true;
        this.applyFilters();
        this.revokeTarget = null;
      },
      error: (err) => console.error('Error while revoking certificate.', err),
    });
  }

  openIssueCertificate(): void {
    this.router.navigate(['/ca/certificates/new']);
  }

  goBack(): void {
    this.location.back();
  }
}