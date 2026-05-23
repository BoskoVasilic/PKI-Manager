import { Component, Input, OnChanges, SimpleChanges, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import {DecimalPipe, NgClass} from '@angular/common';
import { PasswordAnalysis, PasswordService } from '../../services/password.service';

@Component({
  selector: 'app-password-strength',
  imports: [NgClass, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (password && password.length > 0) {
      <div class="mt-2 space-y-2">

        <div class="flex gap-1">
          @for (seg of [0,1,2,3]; track seg) {
            <div class="h-1 flex-1 rounded-full transition-all duration-300"
                 [ngClass]="analysis && analysis.score > seg
                   ? svc.getScoreBarClass(analysis.score)
                   : 'bg-gray-800'">
            </div>
          }
        </div>

        @if (analysis) {
          <div class="flex items-center justify-between">
            <span class="text-xs font-medium" [ngClass]="svc.getScoreLabelClass(analysis.score)">
              {{ svc.getScoreLabel(analysis.score) }}
            </span>
            @if (analysis.crackTime) {
              <span class="text-xs text-gray-600">
                Crack time: {{ analysis.crackTime }}
              </span>
            }
          </div>

          <div class="space-y-0.5">
            <div class="flex items-center gap-1.5 text-xs"
                 [ngClass]="analysis.meetsLength ? 'text-emerald-500' : 'text-gray-600'">
              @if (analysis.meetsLength) {
                <svg class="w-3 h-3 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7"/>
                </svg>
              } @else {
                <svg class="w-3 h-3 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <circle cx="12" cy="12" r="3" stroke-width="2"/>
                </svg>
              }
              Minimum {{ minLength }} characters ({{ password.length }}/{{ minLength }})
            </div>

            @if (!analysis.underMaxLength) {
              <div class="flex items-center gap-1.5 text-xs text-red-400">
                <svg class="w-3 h-3 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                </svg>
                Maximum 128 characters exceeded ({{ password.length }})
              </div>
            }
          </div>

          <!-- HaveIBeenPwned status -->
          <div>
            @if (isPwnedChecking) {
              <div class="flex items-center gap-1.5 text-xs text-gray-500">
                <svg class="animate-spin w-3 h-3 shrink-0" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                </svg>
                Checking against known breaches...
              </div>
            } @else if (analysis.isPwned) {
              <div class="flex items-start gap-1.5 text-xs text-red-400">
                <svg class="w-3 h-3 shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.732-.833-2.5 0L3.732 16.5c-.77.833.192 2.5 1.732 2.5z"/>
                </svg>
                <span>
                  This password has appeared in
                  <strong>{{ analysis.pwnedCount | number }}</strong>
                  data breaches. Please choose a different one.
                </span>
              </div>
            } @else if (analysis.score >= 0) {
              <div class="flex items-center gap-1.5 text-xs text-emerald-500">
                <svg class="w-3 h-3 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7"/>
                </svg>
                Not found in known data breaches
              </div>
            }
          </div>

          <!-- zxcvbn upozorenje -->
          @if (analysis.warning) {
            <span class="text-gray-600 shrink-0 text-sm">Warning:</span>
            <div class="flex items-start gap-1.5 px-2.5 py-2 bg-yellow-500/10 border border-yellow-500/20 rounded text-xs text-yellow-400">
              {{ analysis.warning }}
            </div>
          }

          <!-- zxcvbn preporuke -->
          @if (analysis.suggestions.length > 0) {
            <div class="space-y-0.5">
              <span class="text-gray-600 shrink-0 text-sm">Suggestions:</span>
              @for (suggestion of analysis.suggestions; track suggestion) {
                <div class="flex items-start gap-1.5 text-xs text-gray-500">
                  <span class="text-gray-600 shrink-0">-</span>
                  {{ suggestion }}
                </div>
              }
            </div>
          }
        }
      </div>
    }
  `
})
export class PasswordStrengthComponent implements OnChanges {

  @Input() password = '';
  @Input() minLength = 15;

  analysis: PasswordAnalysis | null = null;
  isPwnedChecking = false;

  private debounceTimer: any;

  constructor(
    public svc: PasswordService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['password']) {
      this.debounceAnalysis();
    }
  }

  private debounceAnalysis(): void {
    clearTimeout(this.debounceTimer);

    if (!this.password) {
      this.analysis = null;
      this.cdr.markForCheck();
      return;
    }

    this.isPwnedChecking = true;
    this.cdr.markForCheck();

    this.debounceTimer = setTimeout(async () => {
      this.analysis = await this.svc.analyze(this.password);
      this.isPwnedChecking = false;
      this.cdr.markForCheck();
    }, 500);
  }
}
