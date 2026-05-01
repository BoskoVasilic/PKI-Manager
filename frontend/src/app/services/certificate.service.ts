import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

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

export interface CertificateDto {
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

  issueCertificate(request: IssueCertificateRequest): Observable<CertificateDto> {
    return this.http.post<CertificateDto>(`${this.API_URL}/issue`, request);
  }

  getAvailableIssuers(): Observable<CertificateDto[]> {
    return this.http.get<CertificateDto[]>(`${this.API_URL}/issuers`);
  }
}