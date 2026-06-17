import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { CertificateData, CertificateService } from '../../services/certificate.service';
import { FormsModule } from '@angular/forms';
import { DatePipe, NgClass, Location } from '@angular/common';

interface CertExtensions {
  keyCertSign: boolean;
  cRLSign: boolean;
  digitalSignature: boolean;
  keyEncipherment: boolean;
  basicConstraintsCA: boolean;
  serverAuth: boolean;
  /** null = unlimited (omit pathLen from BasicConstraints); number = cap depth */
  pathLenConstraint: number | null;
  sanNames: string[];
}

interface CertForm {
  type: 'ROOT' | 'INTERMEDIATE' | 'END_ENTITY' | '';
  issuerSerialNumber: string;
  commonName: string;
  organization: string;
  organizationUnit: string;
  country: string;
  email: string;
  validFrom: string;
  validTo: string;
  extensions: CertExtensions;
}

@Component({
  selector: 'app-issue-certificate-admin',
  imports: [
    FormsModule,
    NgClass,
    DatePipe
  ],
  templateUrl: './issue-certificate-admin.component.html'
})
export class IssueCertificateAdminComponent {
  issuers: CertificateData[] = [];
  selectedIssuer: CertificateData | null = null;

  isLoading = false;
  errorMessage = '';
  successMessage = '';

  today: string = new Date().toISOString().split('T')[0];

  form: CertForm = {
    type: '',
    issuerSerialNumber: '',
    commonName: '',
    organization: '',
    organizationUnit: '',
    country: '',
    email: '',
    validFrom: this.today,
    validTo: '',
    extensions: {
      keyCertSign: false,
      cRLSign: false,
      digitalSignature: true,
      keyEncipherment: false,
      basicConstraintsCA: false,
      serverAuth: false,
      pathLenConstraint: null,
      sanNames: [],
    }
  };

  certTypeOptions = [
    { value: 'ROOT',        label: 'Root CA' },
    { value: 'INTERMEDIATE', label: 'Intermediate CA' },
    { value: 'END_ENTITY',  label: 'End-Entity' },
  ];

  constructor(
    private certService: CertificateService,
    private router: Router,
    private location: Location,
  ) {}

  ngOnInit(): void {
    this.loadIssuers();
  }

  loadIssuers(): void {
    this.certService.getAvailableIssuersAdmin().subscribe({
      next: (issuers) => { this.issuers = issuers; },
      error: (err) => console.error('Error loading issuers.', err)
    });
  }

  onTypeChange(): void {
    this.form.issuerSerialNumber = '';
    this.selectedIssuer = null;

    if (this.form.type === 'ROOT' || this.form.type === 'INTERMEDIATE') {
      this.form.extensions.keyCertSign = true;
      this.form.extensions.basicConstraintsCA = true;
      this.form.extensions.serverAuth = false;
      this.form.extensions.keyEncipherment = false;
      this.form.extensions.sanNames = [];
    } else {
      this.form.extensions.keyCertSign = false;
      this.form.extensions.basicConstraintsCA = false;
      this.form.extensions.pathLenConstraint = null;
      this.form.extensions.digitalSignature = true;
      this.form.extensions.sanNames = [];
    }
  }

  onServerAuthChange(): void {
    if (this.form.extensions.serverAuth && this.form.extensions.sanNames.length === 0) {
      this.form.extensions.sanNames = ['localhost', '127.0.0.1'];
    }
  }

  newSanEntry = '';

  addSan(): void {
    const val = this.newSanEntry.trim();
    if (!val) return;
    if (this.form.extensions.sanNames.includes(val)) return;
    this.form.extensions.sanNames = [...this.form.extensions.sanNames, val];
    this.newSanEntry = '';
  }

  removeSan(san: string): void {
    this.form.extensions.sanNames = this.form.extensions.sanNames.filter(s => s !== san);
  }

  onSanKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.addSan();
    }
  }

  onIssuerChange(issuer: CertificateData): void {
    this.selectedIssuer = issuer;
    if (this.form.validTo > issuer.validTo) {
      this.form.validTo = issuer.validTo.split('T')[0];
    }
  }

  getX500Preview(): string {
    const parts: string[] = [];
    if (this.form.commonName)       parts.push(`CN=${this.form.commonName}`);
    if (this.form.organizationUnit) parts.push(`OU=${this.form.organizationUnit}`);
    if (this.form.organization)     parts.push(`O=${this.form.organization}`);
    if (this.form.country)          parts.push(`C=${this.form.country}`);
    if (this.form.email)            parts.push(`E=${this.form.email}`);
    return parts.length > 0 ? parts.join(', ') : 'CN=..., O=..., C=...';
  }

  getTypeClass(type: string): string {
    switch (type) {
      case 'ROOT':         return 'text-purple-400 border-purple-500/30 bg-purple-500/10';
      case 'INTERMEDIATE': return 'text-blue-400 border-blue-500/30 bg-blue-500/10';
      default:             return 'text-gray-400 border-gray-600/30 bg-gray-500/10';
    }
  }

  onSubmit(): void {
    if (this.isLoading) return;
    this.errorMessage = '';
    this.successMessage = '';
    this.isLoading = true;

    // Resolve pathLenConstraint: only include for CA types, and only when a value is set
    const isCA = this.form.type === 'ROOT' || this.form.type === 'INTERMEDIATE';
    const rawPathLen = this.form.extensions.pathLenConstraint;
    const pathLenConstraint: number | null =
      isCA && rawPathLen !== null && rawPathLen !== undefined && String(rawPathLen) !== ''
        ? Number(rawPathLen)
        : null;

    const payload = {
      type: this.form.type,
      issuerSerialNumber: this.form.type === 'ROOT' ? null : this.form.issuerSerialNumber,
      commonName: this.form.commonName,
      organization: this.form.organization,
      organizationUnit: this.form.organizationUnit,
      country: this.form.country,
      email: this.form.email,
      validFrom: this.form.validFrom,
      validTo: this.form.validTo,
      keyCertSign:        this.form.extensions.keyCertSign,
      cRLSign:            this.form.extensions.cRLSign,
      digitalSignature:   this.form.extensions.digitalSignature,
      keyEncipherment:    this.form.extensions.keyEncipherment,
      basicConstraintsCA: this.form.extensions.basicConstraintsCA,
      serverAuth:         this.form.extensions.serverAuth,
      pathLenConstraint:  pathLenConstraint,
      sanNames:           this.form.extensions.sanNames,
    };

    this.certService.issueCertificateAdmin(payload).subscribe({
      next: () => {
        this.isLoading = false;
        this.successMessage = 'The certificate is successfully issued!';
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.error?.message || 'Error occurred while issuing the certificate. Check issuer validity.';
      }
    });
  }

  goBack(): void {
    this.location.back();
  }
}
