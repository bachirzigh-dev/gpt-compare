import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ChatRequest {
  /** Message utilisateur brut (trim fait côté UI) */
  message: string;

  /** Identifiant du modèle (ex: gpt-5-mini, gpt-4.1-mini) */
  model?: string;

  /**
   * Température du modèle.
   * IMPORTANT : on préfère l’omettre plutôt que `null` pour les modèles qui ne la supportent pas (ex: GPT-5).
   */
  temperature?: number;

  /** Limite maximale de tokens en sortie */
  maxOutputTokens?: number;
}

export interface ChatResponse {
  /** Réponse textuelle du modèle */
  reply: string;

  /** Temps de réponse backend en millisecondes */
  latencyMs?: number | null;

  /** Nombre de tokens consommés */
  inputTokens?: number | null;
  outputTokens?: number | null;
  totalTokens?: number | null;

  /** Indique si la réponse a été tronquée */
  truncated?: boolean | null;

  /** Raison du tronquage (ex: max_output_tokens) */
  truncateReason?: string | null;
}

@Injectable({ providedIn: 'root' })
export class ChatApiService {
  private readonly http = inject(HttpClient);

  private readonly baseUrl = 'http://localhost:8080/api/chat';

  sendMessage(req: ChatRequest): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${this.baseUrl}/send`, req);
  }
}
