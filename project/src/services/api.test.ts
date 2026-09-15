import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, clearCompanyId, clearCompanyProfileCache, setCompanyId } from './api';

describe('api bootstrap cache', () => {
  afterEach(() => {
    clearCompanyId();
    clearCompanyProfileCache();
    vi.unstubAllGlobals();
  });

  it('reuses a bootstrapped company profile on the Company page', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      user: { username: 'owner@example.com' },
      companies: [{ id: 10, tradeName: 'Textile Group', version: 3 }],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);

    await api.bootstrap();
    setCompanyId('10');
    await expect(api.getCompanyProfile()).resolves.toMatchObject({ id: 10, tradeName: 'Textile Group' });

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('propagates a request id on every fetch', async () => {
    const requestId = '123e4567-e89b-12d3-a456-426614174000';
    vi.stubGlobal('crypto', { randomUUID: () => requestId });
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      user: { username: 'owner@example.com' },
      companies: [],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);

    await api.bootstrap();

    const requestInit = fetchMock.mock.calls[0][1] as RequestInit;
    expect(new Headers(requestInit.headers).get('X-Request-ID')).toBe(requestId);
  });
});
