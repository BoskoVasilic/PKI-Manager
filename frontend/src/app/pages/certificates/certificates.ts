import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CertificateService, Certificate } from '../../services/certificate.service';

@Component({
  selector: 'app-certificates',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './certificates.html'
})
export class CertificatesComponent implements OnInit {
  certificates = signal<Certificate[]>([]);
  selected = signal<Certificate | null>(null);
  isLoading = signal(true);
  error = signal('');

  constructor(private certService: CertificateService) {}

  ngOnInit(): void {
    this.certService.getMyCertificates().subscribe({
      next: (data) => {
        this.certificates.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.error.set('Failed to load certificates.');
        this.isLoading.set(false);
      }
    });
  }

  selectCert(cert: Certificate): void {
    this.selected.set(this.selected()?.serialNumber === cert.serialNumber ? null : cert);
  }

  closeDetail(): void {
    this.selected.set(null);
  }

  isExpired(validTo: string): boolean {
    return new Date(validTo) < new Date();
  }

  statusClass(cert: Certificate): string {
    if (cert.status === 'REVOKED')
      return 'bg-red-500/10 text-red-400 border border-red-500/25';
    if (this.isExpired(cert.validTo))
      return 'bg-yellow-400/10 text-yellow-400 border border-yellow-400/25';
    return 'bg-emerald-400/10 text-emerald-400 border border-emerald-400/25';
  }

  statusLabel(cert: Certificate): string {
    if (cert.status === 'REVOKED') return 'Revoked';
    if (this.isExpired(cert.validTo)) return 'Expired';
    return 'Active';
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric'
    });
  }
}