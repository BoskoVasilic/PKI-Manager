import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface IssueCertificateRequest {
  commonName: string;
  organization: string;
  organizationalUnit: string;
  country: string;
  email: string;
  validFrom: string;       // ISO date string
  validTo: string;         // ISO date string
  issuerSerialNumber: string;
  keyUsages: string[];     // e.g. ['KEY_CERT_SIGN', 'CRL_SIGN']
  isCA: boolean;
}

export interface CertificateResponse {
  serialNumber: string;
  commonName: string;
  organization: string;
  issuerCommonName: string;
  validFrom: string;
  validTo: string;
  type: string;            // ROOT, INTERMEDIATE, END_ENTITY
  status: string;          // VALID, REVOKED
  pemEncoded?: string;
}

@Injectable({
  providedIn: 'root',
})
export class CertificateService {
  private readonly API_URL = 'http://localhost:8080/api/certificates';

  constructor(private http: HttpClient) {}

  issueCertificate(request: IssueCertificateRequest): Observable<CertificateResponse> {
    return this.http.post<CertificateResponse>(`${this.API_URL}/issue`, request);
  }

  getAvailableIssuers(): Observable<CertificateResponse[]> {
    return this.http.get<CertificateResponse[]>(`${this.API_URL}/issuers`);
  }
}