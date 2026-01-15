import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  EnvironmentInjector,
  createEnvironmentInjector,
  runInInjectionContext,
  ChangeDetectorRef,
  DestroyRef,
} from '@angular/core';

import { of, Subject, throwError } from 'rxjs';
import { App } from './app';
import type { ChatApiService, ChatRequest, ChatResponse } from './chat-api.service';


const cdrStub: Pick<ChangeDetectorRef, 'markForCheck'> = {
  markForCheck: () => {},
};

/**
 * DestroyRef.onDestroy doit retourner une fonction de cleanup.
 */
function createDestroyRefStub(): Pick<DestroyRef, 'onDestroy'> {
  const callbacks: Array<() => void> = [];

  return {
    onDestroy: (cb: () => void) => {
      callbacks.push(cb);
      // cleanup: retire le callback si besoin
      return () => {
        const idx = callbacks.indexOf(cb);
        if (idx >= 0) callbacks.splice(idx, 1);
      };
    },
  };
}

describe('App (class only with injection context)', () => {
  let apiSpy: { sendMessage: ReturnType<typeof vi.fn> };
  let injector: EnvironmentInjector;

  function create() {
    return runInInjectionContext(injector, () => new App(apiSpy as unknown as ChatApiService));
  }

  beforeEach(() => {
    apiSpy = { sendMessage: vi.fn() };

    const root = createEnvironmentInjector([], null as unknown as EnvironmentInjector);

    injector = createEnvironmentInjector(
      [
        { provide: ChangeDetectorRef, useValue: cdrStub },
        { provide: DestroyRef, useValue: createDestroyRefStub() },
      ],
      root,
      'AppSpecInjector'
    );
  });

  it('supportsTemperature(): false pour gpt-5*', () => {
    const component = create();
    expect(component.supportsTemperature('gpt-5-mini')).toBe(false);
    expect(component.supportsTemperature('GPT-5-NANO')).toBe(false);
  });

  it('supportsTemperature(): true pour non gpt-5', () => {
    const component = create();
    expect(component.supportsTemperature('gpt-4.1-mini')).toBe(true);
  });

  it('send(): ne fait rien si loading=true', () => {
    const component = create();
    component.loading = true;
    component.message = 'Hello';
    component.send();
    expect(apiSpy.sendMessage).not.toHaveBeenCalled();
  });

  it('send(): ne fait rien si message vide après trim', () => {
    const component = create();
    component.message = '   ';
    component.send();
    expect(apiSpy.sendMessage).not.toHaveBeenCalled();
  });

  it('send() mode simple: température absente si modèle GPT-5', () => {
    const component = create();
    component.compareMode = false;
    component.settingsA = { model: 'gpt-5-mini', temperature: 0.9, maxOutputTokens: 800 } as any;
    component.message = '  Bonjour  ';

    apiSpy.sendMessage.mockReturnValue(of({ reply: 'ok' } as ChatResponse));

    component.send();

    const req = apiSpy.sendMessage.mock.calls[0][0] as ChatRequest;
    expect(req.message).toBe('Bonjour');
    expect(req.model).toBe('gpt-5-mini');
    expect('temperature' in req).toBe(false);
  });

  it('send() mode simple: error si backend KO', () => {
    const component = create();
    component.compareMode = false;
    component.message = 'Test';

    apiSpy.sendMessage.mockReturnValue(throwError(() => new Error('boom')));

    component.send();
    expect(component.error).toBe('Erreur appel backend');
    expect(component.loading).toBe(false);
  });

  it('loading true pendant requête async, puis false (finalize)', async () => {
    const component = create();
    component.compareMode = false;
    component.message = 'Hello';

    const subj = new Subject<ChatResponse>();
    apiSpy.sendMessage.mockReturnValue(subj.asObservable());

    component.send();
    expect(component.loading).toBe(true);

    subj.next({ reply: 'ok' } as any);
    subj.complete();

    await new Promise((r) => setTimeout(r, 0));
    expect(component.loading).toBe(false);
  });
  it('clear(): reset message/error/results', () => {
    const c = create();
    c.message = 'x';
    c.error = 'nope';
    c.resA = { reply: 'a' } as any;
    c.resB = { reply: 'b' } as any;

    c.clear();

    expect(c.message).toBe('');
    expect(c.error).toBeNull();
    expect(c.resA).toBeNull();
    expect(c.resB).toBeNull();
  });
  it('onKeyDown(): Ctrl+Enter déclenche send()', () => {
    const c = create();
    const spy = vi.spyOn(c, 'send');
    c.onKeyDown({ ctrlKey: true, metaKey: false, key: 'Enter', preventDefault() {} } as any);
    expect(spy).toHaveBeenCalled();
  });
  it('rerun(): ne fait rien si lastMsg vide', () => {
    const c = create();
    c.rerun('A', 4000);
    expect(apiSpy.sendMessage).not.toHaveBeenCalled();
  });

  it('rerun(): envoie lastMsg avec maxOutputTokens override', () => {
    const c = create();
    c.message = 'Hello';
    apiSpy.sendMessage.mockReturnValue(of({ reply: 'ok' } as any));

    c.send(); // définit lastMsg
    apiSpy.sendMessage.mockClear();

    c.rerun('A', 4000);

    const req = apiSpy.sendMessage.mock.calls[0][0] as ChatRequest;
    expect(req.message).toBe('Hello');
    expect(req.maxOutputTokens).toBe(4000);
  });
  it('send() compareMode: A ok, B erreur → resA ok, resB null, error set', async () => {
    const c = create();
    c.compareMode = true;
    c.message = 'Test';

    apiSpy.sendMessage
      .mockReturnValueOnce(of({ reply: 'A' } as any)) // A
      .mockReturnValueOnce(throwError(() => new Error('boom'))); // B

    c.send();

    await new Promise((r) => setTimeout(r, 0));

    expect(c.resA?.reply).toBe('A');
    expect(c.resB).toBeNull();
    expect(c.error).toBe('Erreur appel backend');
    expect(c.loading).toBe(false);
  });

});
