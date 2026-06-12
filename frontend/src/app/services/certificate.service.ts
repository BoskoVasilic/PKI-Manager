import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

// Matches backend CertificateDTO exactly
export interface CertificateDto {
  id: number;
  serialNumber: string;
  alias: string;
  commonName: string;
  organization: string;
  organizationUnit: string;
  country: string;
  email: string;
  validFrom: string;
  validTo: string;
  type: 'ROOT' | 'INTERMEDIATE' | 'END_ENTITY';
  issuerSerialNumber: string | null;
  revoked: boolean;
  revokedAt: Date | null;
  revocationReason: string | null;
}

export type CertificateData = CertificateDto;

export interface IssueCertificateRequest {
  commonName: string;
  organization: string;
  organizationalUnit: string;
  country: string;
  email: string;
  validFrom: string;
  validTo: string;
  issuerSerialNumber: string;
  type: string;
  keyUsages: string[];
  isCa: boolean;
  pathLengthConstraint?: number;
}

export interface CsrRequest {
  cn: string;
  organization?: string;
  organizationUnit?: string;
  country?: string;
  email?: string;
  caSerialNumber: string;
  validFrom: string;
  validTo: string;
}

export interface CsrUploadRequest {
  csrPem: string;
  caSerialNumber: string;
  validFrom?: string;
  validTo: string;
}

export interface CsrResponse {
  serialNumber: string;
  certificatePem: string;
  keystoreBase64?: string;
  p12Base64?: string;
  keystorePassword?: string;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class CertificateService {
  private readonly API_URL = 'https://localhost:8443/api/certificates';

  constructor(private http: HttpClient) {}

  // ── CSR / User endpoints ────────────────────────────────────────────────────

  listAvailableCas(): Observable<CertificateDto[]> {
    return this.http.get<CertificateDto[]>(`${this.API_URL}/csr/available-cas`);
  }

  autogenerate(request: CsrRequest): Observable<CsrResponse> {
    return this.http.post<CsrResponse>(`${this.API_URL}/csr/autogenerate`, request);
  }

  uploadCsr(request: CsrUploadRequest): Observable<CsrResponse> {
    return this.http.post<CsrResponse>(`${this.API_URL}/csr/upload`, request);
  }

  // ── Regular user endpoints ──────────────────────────────────────────────────

  getMyCertificates(): Observable<CertificateDto[]> {
    return this.http.get<CertificateDto[]>(`${this.API_URL}/my`);
  }

  getCertificateBySerial(serialNumber: string): Observable<CertificateDto> {
    return this.http.get<CertificateDto>(`${this.API_URL}/my/${serialNumber}`);
  }

  revokeMyCertificate(serialNumber: string, reason: string): Observable<void> {
    return this.http.put<void>(`${this.API_URL}/my/${serialNumber}/revoke`, { reason });
  }

  // ── CA user endpoints ───────────────────────────────────────────────────────

  issueCertificate(request: IssueCertificateRequest): Observable<CertificateDto> {
    return this.http.post<CertificateDto>(`${this.API_URL}/issue`, request);
  }

  /** CA user: issuers from their own org */
  getAvailableIssuers(): Observable<CertificateDto[]> {
    return this.http.get<CertificateDto[]>(`${this.API_URL}/my/issuers`);
  }

  /** CA user: all certificates belonging to the caller's organization */
  getOrgCertificates(): Observable<CertificateDto[]> {
  return this.http.get<CertificateDto[]>(`${this.API_URL}/org`);
  }

  // ── Admin endpoints ─────────────────────────────────────────────────────────
  getAvailableIssuersAdmin(): Observable<CertificateDto[]> {
    return this.http.get<CertificateDto[]>(`${this.API_URL}/issuers`);
  }

  issueCertificateAdmin(payload: any): Observable<CertificateDto> {
    return this.http.post<CertificateDto>(`${this.API_URL}`, payload);
  }

  getAllCertificates(): Observable<CertificateDto[]> {
    return this.http.get<CertificateDto[]>(`${this.API_URL}`);
  }

  getCertificate(serialNumber: string): Observable<CertificateDto> {
    return this.http.get<CertificateDto>(`${this.API_URL}/${serialNumber}`);
  }

  revokeCertificate(serialNumber: string, reason: string): Observable<void> {
    return this.http.put<void>(`${this.API_URL}/${serialNumber}/revoke`, { reason });
  }

  getRevokedCertificates(): Observable<CertificateData[]> {
    return this.http.get<CertificateData[]>(`${this.API_URL}/revoked`);
  }

  rotateMasterKey(): Observable<string> {
    return this.http.post('https://localhost:8443/api/admin/master-key/rotate', {}, { responseType: 'text' });
  }
}
