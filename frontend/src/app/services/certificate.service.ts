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

export interface CertificateData {
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

  getAvailableIssuers(): Observable<CertificateData[]> {
    return this.http.get<CertificateData[]>(`${this.API_URL}/certificates/issuers`);
  }

  issueCertificate(payload: any): Observable<CertificateData> {
    return this.http.post<CertificateData>(`${this.API_URL}/certificates`, payload);
  }

  getAllCertificates(): Observable<CertificateData[]> {
    return this.http.get<CertificateData[]>(`${this.API_URL}/certificates`);
  }

  getCertificate(serialNumber: string): Observable<CertificateData> {
    return this.http.get<CertificateData>(`${this.API_URL}/certificates/${serialNumber}`);
  }

  revokeCertificate(serialNumber: string, reason: string): Observable<void> {
    return this.http.put<void>(`${this.API_URL}/certificates/${serialNumber}/revoke`, { reason });
  }
}
