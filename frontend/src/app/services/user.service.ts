import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class UserService {

  constructor(private http: HttpClient) {}

  getOrganizations(): Observable<{ id: number; name: string }[]> {
    return this.http.get<{ id: number; name: string }[]>('http://localhost:8081/api/admin/organizations');
  }

  createCaUser(payload: {
    email: string;
    firstName: string;
    lastName: string;
    organizationId: string | null;
    organizationName: string | null;
  }): Observable<void> {
    return this.http.post<void>('http://localhost:8081/api/admin/ca-users', payload);
  }

  validateActivationToken(token: string): Observable<void> {
    return this.http.get<void>(`http://localhost:8081/api/auth/validate-token?token=${token}`);
  }

  activateCaUser(token: string, password: string): Observable<void> {
    return this.http.post<void>('http://localhost:8081/api/auth/activate-ca', { token, password });
  }
}
