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
}

// Alias — admin pages already use CertificateData, keep it pointing to the same shape
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
  privateKeyPem?: string;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class CertificateService {
  private readonly API_URL = 'http://localhost:8081/api/certificates';

  constructor(private http: HttpClient) {}

  getMyCertificates(): Observable<CertificateDto[]> {
    return this.http.get<CertificateDto[]>(`${this.API_URL}/my`);
  }

  getCertificateBySerial(serialNumber: string): Observable<CertificateDto> {
    return this.http.get<CertificateDto>(`${this.API_URL}/my/${serialNumber}`);
  }

  autogenerate(request: CsrRequest): Observable<CsrResponse> {
    return this.http.post<CsrResponse>(`${this.API_URL}/csr/autogenerate`, request);
  }

  uploadCsr(request: CsrUploadRequest): Observable<CsrResponse> {
    return this.http.post<CsrResponse>(`${this.API_URL}/csr/upload`, request);
  }

  issueCertificate(request: IssueCertificateRequest): Observable<CertificateDto> {
    return this.http.post<CertificateDto>(`${this.API_URL}/issue`, request);
  }

  // CA user: issuers from their own org
  getAvailableIssuers(): Observable<CertificateDto[]> {
    return this.http.get<CertificateDto[]>(`${this.API_URL}/my/issuers`);
  }

  // Admin: all issuers system-wide
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
    return this.http.get<CertificateDto>(`${this.API_URL}/certificates/${serialNumber}`);
  }

  revokeCertificate(serialNumber: string, reason: string): Observable<void> {
    return this.http.put<void>(`${this.API_URL}/certificates/${serialNumber}/revoke`, { reason });
  }
}