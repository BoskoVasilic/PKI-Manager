import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CertificateService, CertificateDto } from '../../services/certificate.service';

@Component({
  selector: 'app-user-certificates-view',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './user-certificates-view.html'
})
export class UserCertificatesViewComponent implements OnInit {
  certificates = signal<CertificateDto[]>([]);
  selected     = signal<CertificateDto | null>(null);
  isLoading    = signal(true);
  error        = signal('');

  searchQuery  = '';
  statusFilter = '';

  revokeTarget: CertificateDto | null = null;
  revokeReason = '';
  revokeError  = '';

  // ── Computed stats ────────────────────────────────────────

  activeCertCount  = computed(() => this.certificates().filter(c => !c.revoked && !this.isExpired(c)).length);
  expiredCertCount = computed(() => this.certificates().filter(c => !c.revoked && this.isExpired(c)).length);
  revokedCertCount = computed(() => this.certificates().filter(c => c.revoked).length);

  filteredCertificates = computed(() => {
    let result = this.certificates();

    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      result = result.filter(c =>
        c.commonName.toLowerCase().includes(q) ||
        c.serialNumber.toLowerCase().includes(q)
      );
    }

    if (this.statusFilter === 'active') {
      result = result.filter(c => !c.revoked && !this.isExpired(c));
    } else if (this.statusFilter === 'expired') {
      result = result.filter(c => !c.revoked && this.isExpired(c));
    } else if (this.statusFilter === 'revoked') {
      result = result.filter(c => c.revoked);
    }

    return result;
  });

  constructor(
    private certService: CertificateService,
    private router: Router
  ) {}

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

  // NOTE: called from the template on ngModelChange to re-trigger the computed signal
  applyFilters(): void {
    // Nudge the signal graph by re-setting the same array so computed() re-evaluates.
    // Alternatively just use the computed directly — it reads searchQuery/statusFilter
    // as plain properties, so Angular's change detection picks them up via the template.
    this.certificates.set([...this.certificates()]);
  }

  goBack(): void {
    this.router.navigate(['/dashboard']);
  }

  selectCert(cert: CertificateDto): void {
    this.selected.set(
      this.selected()?.serialNumber === cert.serialNumber ? null : cert
    );
  }

  closeDetail(): void {
    this.selected.set(null);
  }

  isExpired(cert: CertificateDto): boolean {
    return new Date(cert.validTo) < new Date();
  }

  statusClass(cert: CertificateDto): string {
    if (cert.revoked)         return 'bg-red-500/10 text-red-400 border border-red-500/25';
    if (this.isExpired(cert)) return 'bg-yellow-400/10 text-yellow-400 border border-yellow-400/25';
    return 'bg-emerald-400/10 text-emerald-400 border border-emerald-400/25';
  }

  statusLabel(cert: CertificateDto): string {
    if (cert.revoked)         return 'Revoked';
    if (this.isExpired(cert)) return 'Expired';
    return 'Active';
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric'
    });
  }

  openRevoke(cert: CertificateDto): void {
    this.revokeTarget = cert;
    this.revokeReason = '';
    this.revokeError  = '';
  }

  cancelRevoke(): void {
    this.revokeTarget = null;
  }

  confirmRevoke(): void {
    if (!this.revokeTarget || !this.revokeReason) return;

    this.certService.revokeMyCertificate(this.revokeTarget.serialNumber, this.revokeReason)
      .subscribe({
        next: () => {
          this.certificates.update(certs =>
            certs.map(c =>
              c.serialNumber === this.revokeTarget!.serialNumber
                ? { ...c, revoked: true }
                : c
            )
          );
          this.revokeTarget = null;
        },
        error: () => {
          this.revokeError = 'Failed to revoke certificate. Please try again.';
        }
      });
  }
}