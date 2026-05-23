import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  twoFaRequired: boolean;
}

export interface ChallengeResponse {
  encryptedChallenge: string;
  token: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly API_URL = 'http://localhost:8081/api';
  private readonly TOKEN_KEY = 'access_token';
  private readonly PRE_AUTH_KEY = 'pre_auth_token';

  constructor(private http: HttpClient, private router: Router) {}

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.API_URL}/auth/login`, credentials)
      .pipe(
        tap((response) => {
          if (response.twoFaRequired) {
            sessionStorage.setItem(this.PRE_AUTH_KEY, response.accessToken);
          } else {
            this.saveToken(response.accessToken);
          }
        })
      );
  }

  verifyTwoFa(code: string): Observable<{ accessToken: string }> {
    const preAuthToken = sessionStorage.getItem(this.PRE_AUTH_KEY);
    return this.http
      .post<{ accessToken: string }>(`${this.API_URL}/auth/2fa/verify`, {
        preAuthToken,
        code,
      })
      .pipe(
        tap((response) => {
          sessionStorage.removeItem(this.PRE_AUTH_KEY);
          this.saveToken(response.accessToken);
        })
      );
  }

  setup2Fa(): Observable<{ secret: string; qrCodeDataUri: string }> {
    return this.http.post<{ secret: string; qrCodeDataUri: string }>(
      `${this.API_URL}/auth/2fa/setup`,
      {}
    );
  }

  enable2Fa(secret: string, code: string): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/auth/2fa/enable`, {
      secret,
      code,
    });
  }

  disable2Fa(code: string): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/auth/2fa/disable`, { code });
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    sessionStorage.removeItem(this.PRE_AUTH_KEY);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  private saveToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  getCurrentUserEmail(): string | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.sub || null;
    } catch {
      return null;
    }
  }

  getCurrentUserOrganization(): string {
    const token = this.getToken();
    if (!token) return '';
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const org = payload.organization;
      if (!org) return '';
      if (typeof org === 'string') return org;
      return org.name || '';
    } catch {
      return '';
    }
  }

  register(payload: {
    email: string;
    password: string;
    confirmPassword: string;
    firstName: string;
    lastName: string;
    organizationName: string;
    publicKeyPem: string;
  }): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.API_URL}/auth/register`, payload);
  }

  getActivationChallenge(token: string): Observable<ChallengeResponse> {
    return this.http.get<ChallengeResponse>(`${this.API_URL}/auth/activate/challenge?token=${token}`);
  }

  activateAccount(token: string, decryptedChallenge: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.API_URL}/auth/activate`, {
      token,
      decryptedChallenge
    });
  }

  forgotPassword(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.API_URL}/auth/forgot-password`, { email });
  }

  getForgotPasswordChallenge(token: string): Observable<ChallengeResponse> {
    return this.http.get<ChallengeResponse>(`${this.API_URL}/auth/forgot-password/challenge?token=${token}`);
  }

  resetPassword(token: string, decryptedChallenge: string, newPassword: string, confirmNewPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.API_URL}/auth/forgot-password/reset`, {
      token,
      decryptedChallenge,
      newPassword,
      confirmNewPassword
    });
  }
}
