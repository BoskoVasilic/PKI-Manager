import {Component, OnInit} from '@angular/core';
import {DatePipe, Location, NgClass, SlicePipe} from '@angular/common';
import {CertificateData, CertificateService} from '../../services/certificate.service';

@Component({
  selector: 'app-crl-view',
  imports: [
    NgClass,
    DatePipe,
    SlicePipe
  ],
  templateUrl: './crl-view.component.html',
  styleUrl: './crl-view.component.css',
})
export class CrlViewComponent implements OnInit{
  revokedCerts: CertificateData[] = [];
  groupedByIssuer: Record<string, CertificateData[]> = {};
  issuerCrlUrls: Record<string, string> = {};
  issuerNames: Record<string, string> = {};

  isLoading = false;
  now = new Date();
  nextUpdate = new Date(Date.now() + 24 * 60 * 60 * 1000);

  constructor(
    private certService: CertificateService,
    private location: Location,
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.isLoading = true;
    this.now = new Date();
    this.nextUpdate = new Date(Date.now() + 24 * 60 * 60 * 1000);

    this.certService.getAllCertificates().subscribe({
      next: (certs) => {
        this.revokedCerts = certs.filter(c => c.revoked);

        const allCertsMap: Record<string, CertificateData> = {};
        certs.forEach(c => { allCertsMap[c.serialNumber] = c; });

        this.groupedByIssuer = this.revokedCerts.reduce((acc, cert) => {
          const key = cert.issuerSerialNumber ?? 'self-signed';
          if (!acc[key]) acc[key] = [];
          acc[key].push(cert);
          return acc;
        }, {} as Record<string, CertificateData[]>);

        this.issuerCrlUrls = {};
        this.issuerNames = {};
        Object.keys(this.groupedByIssuer).forEach(issuerSerial => {
          this.issuerCrlUrls[issuerSerial] =
            `https://localhost:8443/api/crl/${issuerSerial}/crl.crl`;

          const issuerCert = allCertsMap[issuerSerial];
          this.issuerNames[issuerSerial] = issuerCert
            ? `${issuerCert.commonName} · ${issuerCert.type}`
            : issuerSerial;
        });

        this.isLoading = false;
      },
      error: () => { this.isLoading = false; }
    });
  }

  getIssuerSerials(): string[] {
    return Object.keys(this.groupedByIssuer);
  }

  crlErrorMessage: Record<string, string> = {};

  downloadCrlForIssuer(issuerSerial: string): void {
    this.crlErrorMessage[issuerSerial] = '';

    this.certService.downloadCrlForIssuer(issuerSerial).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${issuerSerial}.crl`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: (err) => {
        if (err.status === 403) {
          this.crlErrorMessage[issuerSerial] =
            'This CA does not have the cRLSign extension enabled. Its private key cannot sign a CRL.';
        } else {
          this.crlErrorMessage[issuerSerial] =
            err?.error?.message || 'Failed to download CRL.';
        }
      }
    });
  }

  getTypeClass(type: string): string {
    switch (type) {
      case 'ROOT':         return 'text-purple-400 border-purple-500/30 bg-purple-500/10';
      case 'INTERMEDIATE': return 'text-blue-400 border-blue-500/30 bg-blue-500/10';
      default:             return 'text-gray-400 border-gray-600/30 bg-gray-500/10';
    }
  }

  formatReason(reason: string | null | undefined): string {
    if (!reason) return 'Unspecified';
    const map: Record<string, string> = {
      'UNSPECIFIED':            'Unspecified',
      'KEY_COMPROMISE':         'Key Compromise',
      'CA_COMPROMISE':          'CA Compromise',
      'AFFILIATION_CHANGED':    'Affiliation Changed',
      'SUPERSEDED':             'Superseded',
      'CESSATION_OF_OPERATION': 'Cessation of Operation',
      'PRIVILEGE_WITHDRAWN':    'Privilege Withdrawn',
      'CERTIFICATE_HOLD':       'Certificate Hold',
    };
    return map[reason] ?? reason;
  }

  goBack(): void {
    this.location.back();
  }
}
