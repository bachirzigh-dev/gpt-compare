import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { ChatApiService, ChatRequest, ChatResponse } from './chat-api.service';

describe('ChatApiService', () => {
  let service: ChatApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ChatApiService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(ChatApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // Garantit qu’aucune requête HTTP “en attente” ne reste non flush.
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('sendMessage(): POST vers /api/chat/send et retourne la réponse', () => {
    const mock: ChatResponse = { reply: 'ok' };
    const reqBody: ChatRequest = { message: 'yo' };

    service.sendMessage(reqBody).subscribe((res) => {
      expect(res).toEqual(mock);
    });

    const req = httpMock.expectOne('http://localhost:8080/api/chat/send');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(reqBody);

    req.flush(mock);
  });

  it('sendMessage(): propage une erreur HTTP', () => {
    const reqBody: ChatRequest = { message: 'yo' };

    service.sendMessage(reqBody).subscribe({
      next: () => {
        throw new Error('should not succeed');
      },
      error: (err) => {
        expect(err.status).toBe(500);
      },
    });

    const req = httpMock.expectOne('http://localhost:8080/api/chat/send');
    req.flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
  });
});
