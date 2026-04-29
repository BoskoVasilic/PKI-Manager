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
}