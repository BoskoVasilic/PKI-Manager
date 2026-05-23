import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';


export interface PasswordAnalysis {
  score: 0 | 1 | 2 | 3 | 4;
  crackTime: string;
  warning: string;
  suggestions: string[];
  isPwned: boolean;
  pwnedCount: number;
  meetsLength: boolean;
  underMaxLength: boolean;
  isAcceptable: boolean;
  minLength: number;
}

@Injectable({ providedIn: 'root' })
export class PasswordService {

  static readonly DEFAULT_MIN_LENGTH = 15;
  static readonly MFA_MIN_LENGTH = 8;
  static readonly MAX_LENGTH = 128;

  private zxcvbn: any = null;
  private zxcvbnLoaded = false;

  constructor(private http: HttpClient) {
    this.loadZxcvbn();
  }


  private async loadZxcvbn(): Promise<void> {
    try {
      const { zxcvbn, zxcvbnOptions } = await import('@zxcvbn-ts/core');
      const { dictionary, translations } = await import('@zxcvbn-ts/language-en');
      const { adjacencyGraphs } = await import('@zxcvbn-ts/language-common');

      const common = await import('@zxcvbn-ts/language-common');

      zxcvbnOptions.setOptions({
        graphs: adjacencyGraphs,
        dictionary: {
          ...dictionary,
          ...common.dictionary,
        },
        translations: translations,
      });

      this.zxcvbn = zxcvbn;
      this.zxcvbnLoaded = true;
    } catch (err) {
      console.warn('zxcvbn-ts is not loaded, using fallback:', err);
    }
  }

  async analyze(
    password: string,
    minLength: number = PasswordService.DEFAULT_MIN_LENGTH
  ): Promise<PasswordAnalysis> {

    const meetsLength = password.length >= minLength;
    const underMaxLength = password.length <= PasswordService.MAX_LENGTH;

    let score: 0 | 1 | 2 | 3 | 4 = 0;
    let crackTime = '';
    let warning = '';
    let suggestions: string[] = [];

    if (this.zxcvbnLoaded && this.zxcvbn) {
      const result = this.zxcvbn(password);
      score = result.score;
      crackTime = result.crackTimesDisplay?.offlineSlowHashing1e4PerSecond || '';
      warning = result.feedback?.warning || '';
      suggestions = result.feedback?.suggestions || [];
    } else {
      if (password.length >= 20) score = 3;
      else if (password.length >= minLength) score = 2;
      else if (password.length >= 8) score = 1;
    }

    let isPwned = false;
    let pwnedCount = 0;
    if (password.length > 0) {
      try {
        const result = await this.checkPwned(password);
        isPwned = result.isPwned;
        pwnedCount = result.count;
      } catch {
        console.warn('HaveIBeenPwned API unavailable');
      }
    }

    const isAcceptable = score >= 3 && !isPwned && meetsLength && underMaxLength;

    return {
      score, crackTime, warning, suggestions,
      isPwned, pwnedCount,
      meetsLength, underMaxLength,
      isAcceptable,
      minLength,
    };
  }


  private async checkPwned(password: string): Promise<{ isPwned: boolean; count: number }> {
    const hash = await this.sha1(password);
    const prefix = hash.substring(0, 5).toUpperCase();
    const suffix = hash.substring(5).toUpperCase();

    const response = await firstValueFrom(
      this.http.get(`https://api.pwnedpasswords.com/range/${prefix}`, { responseType: 'text' })
    );

    const lines = response.split('\n');
    for (const line of lines) {
      const [hashSuffix, countStr] = line.trim().split(':');
      if (hashSuffix === suffix) {
        return { isPwned: true, count: parseInt(countStr, 10) };
      }
    }

    return { isPwned: false, count: 0 };
  }

  private async sha1(message: string): Promise<string> {
    const encoder = new TextEncoder();
    const data = encoder.encode(message);
    const hashBuffer = await window.crypto.subtle.digest('SHA-1', data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
  }

  getScoreLabel(score: number): string {
    switch (score) {
      case 0: return 'Very weak';
      case 1: return 'Weak';
      case 2: return 'Fair';
      case 3: return 'Strong';
      case 4: return 'Very strong';
      default: return '';
    }
  }

  getScoreColor(score: number): string {
    switch (score) {
      case 0: return 'red';
      case 1: return 'orange';
      case 2: return 'yellow';
      case 3: return 'blue';
      case 4: return 'emerald';
      default: return 'gray';
    }
  }

  getScoreBarClass(score: number): string {
    switch (score) {
      case 0: return 'bg-red-500';
      case 1: return 'bg-orange-500';
      case 2: return 'bg-yellow-500';
      case 3: return 'bg-blue-500';
      case 4: return 'bg-emerald-500';
      default: return 'bg-gray-700';
    }
  }

  getScoreLabelClass(score: number): string {
    switch (score) {
      case 0: return 'text-red-400';
      case 1: return 'text-orange-400';
      case 2: return 'text-yellow-400';
      case 3: return 'text-blue-400';
      case 4: return 'text-emerald-400';
      default: return 'text-gray-500';
    }
  }
}
