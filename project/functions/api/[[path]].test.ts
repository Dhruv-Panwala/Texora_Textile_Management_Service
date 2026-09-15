import { afterEach, describe, expect, it, vi } from 'vitest';
import { onRequest } from './[[path]]';

const requestId = '11111111-1111-4111-8111-111111111111';

describe('Pages API proxy', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('propagates request IDs and combines upstream timing without caching API JSON', async () => {
    const upstream = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{"ok":true}', {
      status: 200,
      headers: {
        'Content-Type': 'application/json',
        'Server-Timing': 'app;dur=12, db;dur=4',
      },
    }));
    const response = await onRequest({
      request: new Request('https://pages.example/api/auth/bootstrap?companyId=7', {
        headers: { 'X-Request-ID': requestId },
      }),
      env: { BACKEND_URL: 'https://backend.example/' },
      params: { path: ['auth', 'bootstrap'] },
      next: vi.fn(),
      functionPath: '/functions/api/[[path]]',
      waitUntil: vi.fn(),
      passThroughOnException: vi.fn(),
    } as Parameters<typeof onRequest>[0]);

    expect(upstream).toHaveBeenCalledWith(
      'https://backend.example/api/auth/bootstrap?companyId=7',
      expect.objectContaining({
        method: 'GET',
        signal: expect.any(AbortSignal),
      }),
    );
    expect(response.status).toBe(200);
    expect(response.headers.get('X-Request-ID')).toBe(requestId);
    expect(response.headers.get('Cache-Control')).toBe('no-store');
    expect(response.headers.get('Server-Timing')).toMatch(/^app;dur=12, db;dur=4, cf_upstream;dur=\d+$/);
  });

  it('returns 504 when the upstream timeout aborts the request', async () => {
    vi.useFakeTimers();
    const upstream = vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => new Promise((_resolve, reject) => {
      init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')), { once: true });
    }));
    const pending = onRequest({
      request: new Request('https://pages.example/api/dashboard'),
      env: { BACKEND_URL: 'https://backend.example' },
      params: { path: ['dashboard'] },
      next: vi.fn(),
      functionPath: '/functions/api/[[path]]',
      waitUntil: vi.fn(),
      passThroughOnException: vi.fn(),
    } as Parameters<typeof onRequest>[0]);

    await vi.advanceTimersByTimeAsync(15_000);
    const response = await pending;

    expect(upstream).toHaveBeenCalledOnce();
    expect(response.status).toBe(504);
    expect(response.headers.get('X-Request-ID')).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
    expect(response.headers.get('Server-Timing')).toMatch(/^cf_upstream;dur=\d+$/);
    await expect(response.json()).resolves.toEqual({ error: 'The service took too long to respond' });
  });
});
