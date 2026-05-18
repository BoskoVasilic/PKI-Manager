import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { CertificateService, CertificateDto } from '../../services/certificate.service';
import {
  CertificateDownloadService,
  DownloadFormat,
} from '../../services/certificate-download.service';

interface DownloadState {
  inProgress: boolean;
  lastFormat: DownloadFormat | null;
  success: boolean;
  error: string;
}

type DownloadTab = 'withoutPrivateKey' | 'withPrivateKey';

@Component({
  selector: 'app-certificate-download',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './download-certificate.html',
})
export class CertificateDownloadComponent implements OnInit {

  // ── Data signals ─────────────────────────────────────────────────────────
  certificates  = signal<CertificateDto[]>([]);
  selected      = signal<CertificateDto | null>(null);
  isLoading     = signal(true);
  fetchError    = signal('');

  // ── Search / filter ──────────────────────────────────────────────────────
  searchQuery = '';
  activeTab = signal<DownloadTab>('withoutPrivateKey');

  filteredCerts = computed(() => {
    const q = this.searchQuery.trim().toLowerCase();
    return q
      ? this.certificates().filter(
          c =>
            c.commonName.toLowerCase().includes(q) ||
            c.serialNumber.toLowerCase().includes(q) ||
            (c.organization ?? '').toLowerCase().includes(q)
        )
      : this.certificates();
  });

  // ── Download state ───────────────────────────────────────────────────────
  dl: DownloadState = {
    inProgress: false,
    lastFormat: null,
    success: false,
    error: '',
  };

  // ── Private key export ───────────────────────────────────────────────────
  exportPassword = signal('');
  showPassword   = signal(false);

  constructor(
    private certService:     CertificateService,
    private downloadService: CertificateDownloadService,
    private router:          Router
  ) {}

  ngOnInit(): void {
    this.downloadService.getDownloadableCertificates().subscribe({
      next: data => {
        this.certificates.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.fetchError.set('Failed to load certificates.');
        this.isLoading.set(false);
      },
    });
  }

  goBack(): void {
    this.router.navigate(['/dashboard']);
  }

  setTab(tab: DownloadTab): void {
    this.activeTab.set(tab);
    // Reset download state when switching tabs
    this.dl = { inProgress: false, lastFormat: null, success: false, error: '' };
  }

  // ── Selection ────────────────────────────────────────────────────────────
  select(cert: CertificateDto): void {
    const isSame = this.selected()?.serialNumber === cert.serialNumber;
    this.selected.set(isSame ? null : cert);
    this.dl = { inProgress: false, lastFormat: null, success: false, error: '' };
    this.exportPassword.set('');
    this.showPassword.set(false);
  }

  // ── Download ─────────────────────────────────────────────────────────────
  download(format: DownloadFormat): void {
    const cert = this.selected();
    if (!cert || this.dl.inProgress) return;

    if ((format === 'p12' || format === 'jks') && !this.exportPassword().trim()) {
      this.dl = {
        inProgress: false,
        lastFormat: format,
        success: false,
        error: 'Please enter a password to protect the keystore.',
      };
      return;
    }

    this.dl = { inProgress: true, lastFormat: format, success: false, error: '' };

    let stream$: Observable<Blob>;

    if (format === 'pem') {
      stream$ = this.downloadService.downloadAsPem(cert.serialNumber);
    } else if (format === 'cer') {
      stream$ = this.downloadService.downloadAsCer(cert.serialNumber);
    } else if (format === 'p12') {
      stream$ = this.downloadService.downloadAsP12(cert.serialNumber, this.exportPassword());
    } else {
      stream$ = this.downloadService.downloadAsJks(cert.serialNumber, this.exportPassword());
    }

    const filename = `certificate-${cert.serialNumber}.${format}`;

    stream$.subscribe({
      next: blob => {
        this.downloadService.triggerDownload(blob, filename);
        this.dl = { inProgress: false, lastFormat: format, success: true, error: '' };
      },
      error: () => {
        this.dl = {
          inProgress: false,
          lastFormat: format,
          success: false,
          error: 'Download failed. The certificate may not be available.',
        };
      },
    });
  }

  toggleShowPassword(): void {
    this.showPassword.set(!this.showPassword());
  }

  // ── Helpers ──────────────────────────────────────────────────────────────
  applyFilters(): void {
    this.certificates.set([...this.certificates()]);
  }

  isExpired(cert: CertificateDto): boolean {
    return new Date(cert.validTo) < new Date();
  }

  statusLabel(cert: CertificateDto): string {
    if (cert.revoked)         return 'Revoked';
    if (this.isExpired(cert)) return 'Expired';
    return 'Active';
  }

  statusClass(cert: CertificateDto): string {
    if (cert.revoked)         return 'bg-red-500/10 text-red-400 border border-red-500/25';
    if (this.isExpired(cert)) return 'bg-yellow-400/10 text-yellow-400 border border-yellow-400/25';
    return 'bg-emerald-400/10 text-emerald-400 border border-emerald-400/25';
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric',
    });
  }
}