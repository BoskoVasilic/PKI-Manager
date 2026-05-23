import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, throwError } from 'rxjs';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
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
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';
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
            this.saveTokens(response.accessToken, response.refreshToken);
          }
        })
      );
  }

  refresh(): Observable<LoginResponse> {
    const refreshToken = localStorage.getItem(this.REFRESH_TOKEN_KEY);
    if (!refreshToken) {
      this.logout();
      return throwError(() => new Error('No refresh token available'));
    }

    return this.http
      .post<LoginResponse>(`${this.API_URL}/auth/refresh`, { refreshToken })
      .pipe(
        tap((response) => {
          this.saveTokens(response.accessToken, response.refreshToken);
        }),
        catchError((err) => {
          // Refresh token itself is expired — force logout
          this.logout();
          return throwError(() => err);
        })
      );
  }

  verifyTwoFa(code: string): Observable<LoginResponse> {
    const preAuthToken = sessionStorage.getItem(this.PRE_AUTH_KEY);
    return this.http
      .post<LoginResponse>(`${this.API_URL}/auth/2fa/verify`, {
        preAuthToken,
        code,
      })
      .pipe(
        tap((response) => {
          sessionStorage.removeItem(this.PRE_AUTH_KEY);
          // After 2FA, the backend should return both tokens
          this.saveTokens(response.accessToken, response.refreshToken);
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
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    sessionStorage.removeItem(this.PRE_AUTH_KEY);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  private saveTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem(this.TOKEN_KEY, accessToken);
    localStorage.setItem(this.REFRESH_TOKEN_KEY, refreshToken);
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
