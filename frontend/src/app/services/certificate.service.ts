import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Certificate {
  serialNumber: string;
  subjectCN: string;
  subjectO: string;
  subjectOU: string;
  subjectC: string;
  subjectEmail: string;
  issuerCN: string;
  validFrom: string;
  validTo: string;
  type: 'ROOT' | 'INTERMEDIATE' | 'END_ENTITY';
  status: 'ACTIVE' | 'REVOKED';
  revocationReason: string | null;
}

export interface CsrRequest {
  cn: string;
  organization?: string;
  organizationUnit?: string;
  country?: string;
  email?: string;
  caSerialNumber: string;
  validFrom: string; // ISO-8601 LocalDateTime, e.g. "2025-06-01T00:00:00"
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
  privateKeyPem?: string; // only present for autogenerate
  message: string;
}

@Injectable({ providedIn: 'root' })
export class CertificateService {
  private readonly API_URL = 'http://localhost:8081/api';

  constructor(private http: HttpClient) {}

  getMyCertificates(): Observable<Certificate[]> {
    return this.http.get<Certificate[]>(`${this.API_URL}/certificates/my`);
  }

  getCertificateBySerial(serialNumber: string): Observable<Certificate> {
    return this.http.get<Certificate>(`${this.API_URL}/certificates/my/${serialNumber}`);
  }

  /** Feature 6/8 — server generates key pair, signs cert, returns private key ONCE */
  autogenerate(request: CsrRequest): Observable<CsrResponse> {
    return this.http.post<CsrResponse>(`${this.API_URL}/csr/autogenerate`, request);
  }

  /** Feature 8 — user uploads their own CSR PEM, server signs and returns cert */
  uploadCsr(request: CsrUploadRequest): Observable<CsrResponse> {
    return this.http.post<CsrResponse>(`${this.API_URL}/csr/upload`, request);
  }
}