import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CertificateDto } from './certificate.service';

export type DownloadFormat = 'pem' | 'cer' | 'p12' | 'jks';

@Injectable({ providedIn: 'root' })
export class CertificateDownloadService {
  private readonly API_URL = 'https://localhost:8443/api/certificates';

  constructor(private http: HttpClient) {}

  downloadAsPem(serialNumber: string): Observable<Blob> {
    return this.http.get(`${this.API_URL}/${serialNumber}/download/pem`, {
      responseType: 'blob',
    });
  }


  downloadAsCer(serialNumber: string): Observable<Blob> {
    return this.http.get(`${this.API_URL}/${serialNumber}/download/cer`, {
      responseType: 'blob',
    });
  }

  downloadAsP12(serialNumber: string, exportPassword: string): Observable<Blob> {
  return this.http.get(`${this.API_URL}/${serialNumber}/download/p12`, {
    params: { exportPassword },
    responseType: 'blob',
  });
}

downloadAsJks(serialNumber: string, exportPassword: string): Observable<Blob> {
  return this.http.get(`${this.API_URL}/${serialNumber}/download/jks`, {
    params: { exportPassword },
    responseType: 'blob',
  });
}

  triggerDownload(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.style.display = 'none';
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    // Revoke after a short delay so the browser has time to start the download
    setTimeout(() => URL.revokeObjectURL(url), 5000);
  }

  getDownloadableCertificates(): Observable<CertificateDto[]> {
    return this.http.get<CertificateDto[]>(`${this.API_URL}/downloadable`);
  }
}
