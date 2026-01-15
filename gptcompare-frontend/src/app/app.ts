import { Component, ChangeDetectorRef, DestroyRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { forkJoin, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { ChatApiService, ChatRequest, ChatResponse } from './chat-api.service';

type Settings = Readonly<{
  model: string;
  temperature: number;
  maxOutputTokens: number;
}>;

type Which = 'A' | 'B';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrls: ['./app.css'],
})
export class App {
  // UI
  showSettings = true;
  compareMode = false;

  // input/state
  message = '';
  loading = false;
  error: string | null = null;

  /** Dernier message envoyé (sert aux relances). */
  private lastMsg = '';

  // settings (valeurs par défaut)
  settingsA: Settings = { model: 'gpt-5-mini', temperature: 0.7, maxOutputTokens: 800 };
  settingsB: Settings = { model: 'gpt-4.1-mini', temperature: 0.7, maxOutputTokens: 800 };

  // results
  resA: ChatResponse | null = null;
  resB: ChatResponse | null = null;

  // Angular utils
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  constructor(private readonly api: ChatApiService) {}

  /**
   * Dans un contexte OnPush / zoneless, déclencher explicitement la MAJ UI
   * rend l'app plus robuste qu'un simple binding “implicit”.
   */
  private refreshUI(): void {
    this.cdr.markForCheck();
  }

  /** Met à jour le loading + rafraîchit l’UI de façon centralisée. */
  private setLoading(v: boolean): void {
    this.loading = v;
    this.refreshUI();
  }

  /** GPT-5 ne supporte pas temperature (on la retire du payload plutôt que null). */
  supportsTemperature(model: string): boolean {
    return !model.toLowerCase().startsWith('gpt-5');
  }

  /**
   * Construit la requête envoyée au backend.
   * Important : on OMIT `temperature` si non supportée (plutôt que null),
   * pour laisser le backend accepter des modèles “sans temperature”.
   */
  private buildRequest(
    msg: string,
    settings: Settings,
    override?: Partial<Settings>
  ): ChatRequest {
    const s: Settings = { ...settings, ...override };
    return {
      message: msg,
      model: s.model,
      ...(this.supportsTemperature(s.model) ? { temperature: s.temperature } : {}),
      maxOutputTokens: s.maxOutputTokens,
    };
  }

  private resetResults(): void {
    this.resA = null;
    this.resB = null;
  }

  /** Centralise le message UI d’erreur + log dev. */
  private reportBackendError(e: unknown): void {
    console.error(e);
    this.error = 'Erreur appel backend';
  }

  /**
   * Envoi principal.
   * - early returns (loading / message vide)
   * - mode simple: une seule requête
   * - mode compare: deux requêtes en parallèle, chacune “tolérante” à l’erreur
   */
  send(): void {
    if (this.loading) return;

    const msg = this.message.trim();
    if (!msg) return;

    this.lastMsg = msg;
    this.error = null;
    this.resetResults();
    this.setLoading(true);

    const reqA = this.buildRequest(msg, this.settingsA);

    // Mode simple
    if (!this.compareMode) {
      this.api
        .sendMessage(reqA)
        .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.setLoading(false)))
        .subscribe({
          next: (res) => {
            this.resA = res;
            this.refreshUI();
          },
          error: (e) => {
            this.reportBackendError(e);
            this.refreshUI();
          },
        });

      return;
    }

    // Mode comparaison : A peut réussir même si B échoue, et inversement.
    const reqB = this.buildRequest(msg, this.settingsB);

    forkJoin({
      a: this.api.sendMessage(reqA).pipe(
        catchError((e) => {
          this.reportBackendError(e);
          return of(null);
        })
      ),
      b: this.api.sendMessage(reqB).pipe(
        catchError((e) => {
          this.reportBackendError(e);
          return of(null);
        })
      ),
    })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.setLoading(false)))
      .subscribe(({ a, b }) => {
        this.resA = a;
        this.resB = b;
        this.refreshUI();
      });
  }

  /**
   * Relance A ou B avec un maxOutputTokens différent.
   * On réutilise lastMsg (pas le textarea courant) : UX plus intuitive.
   */
  rerun(which: Which, tokens: number): void {
    if (this.loading) return;
    if (!this.lastMsg) return;

    this.error = null;
    this.setLoading(true);

    const base = which === 'A' ? this.settingsA : this.settingsB;
    const req = this.buildRequest(this.lastMsg, base, { maxOutputTokens: tokens });

    this.api
      .sendMessage(req)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.setLoading(false)))
      .subscribe({
        next: (res) => {
          if (which === 'A') this.resA = res;
          else this.resB = res;
          this.refreshUI();
        },
        error: (e) => {
          this.reportBackendError(e);
          this.refreshUI();
        },
      });
  }

  clear(): void {
    this.message = '';
    this.error = null;
    this.resetResults();
    this.refreshUI();
  }

  /** Ctrl/Cmd + Enter => envoi (qualité de vie). */
  onKeyDown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      event.preventDefault();
      this.send();
    }
  }
}
