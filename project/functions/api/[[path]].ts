interface Env {
  BACKEND_URL: string;
}

const UPSTREAM_TIMEOUT_MS = 15_000;

const hopByHopHeaders = new Set([
  'connection',
  'content-length',
  'host',
  'keep-alive',
  'transfer-encoding',
  'upgrade',
]);

export const onRequest: PagesFunction<Env> = async ({ request, env, params }) => {
  const requestId = validRequestId(request.headers.get('X-Request-ID'))
    ? request.headers.get('X-Request-ID')!
    : crypto.randomUUID();
  const started = performance.now();
  const backendUrl = env.BACKEND_URL?.replace(/\/$/, '');
  if (!backendUrl) {
    logProxyTiming(requestId, 500, started);
    return new Response(JSON.stringify({ error: 'API proxy is not configured' }), {
      status: 500,
      headers: {
        'Content-Type': 'application/json',
        'Cache-Control': 'no-store',
        'X-Request-ID': requestId,
        'Server-Timing': `cf_upstream;dur=${Math.round(performance.now() - started)}`,
      },
    });
  }

  const path = Array.isArray(params.path) ? params.path.join('/') : String(params.path || '');
  const incomingUrl = new URL(request.url);
  const targetUrl = `${backendUrl}/api${path ? `/${path}` : ''}${incomingUrl.search}`;
  const headers = new Headers(request.headers);

  for (const header of hopByHopHeaders) {
    headers.delete(header);
  }
  headers.delete('origin');
  headers.delete('referer');
  headers.delete('forwarded');
  headers.delete('x-forwarded-for');
  headers.delete('x-forwarded-host');
  headers.delete('x-forwarded-proto');
  headers.delete('x-real-ip');
  headers.delete('true-client-ip');
  // Cloudflare supplies this value at the edge; never forward a visitor-supplied copy.
  const cloudflareClientIp = request.headers.get('CF-Connecting-IP');
  headers.delete('cf-connecting-ip');
  if (cloudflareClientIp) {
    headers.set('CF-Connecting-IP', cloudflareClientIp);
  }
  headers.set('X-Request-ID', requestId);

  const body = request.method === 'GET' || request.method === 'HEAD' ? undefined : request.body;
  const controller = new AbortController();
  let timedOut = false;
  const abortFromClient = () => controller.abort();
  if (request.signal.aborted) {
    controller.abort();
  } else {
    request.signal.addEventListener('abort', abortFromClient, { once: true });
  }
  const timeout = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, UPSTREAM_TIMEOUT_MS);
  let upstream: Response;
  try {
    upstream = await fetch(targetUrl, {
      method: request.method,
      headers,
      body,
      redirect: 'follow',
      signal: controller.signal,
    });
  } catch {
    const status = timedOut ? 504 : 502;
    logProxyTiming(requestId, status, started, timedOut);
    const responseHeaders = new Headers({
      'Content-Type': 'application/json',
      'Cache-Control': 'no-store',
      'X-Request-ID': requestId,
      'Server-Timing': `cf_upstream;dur=${Math.round(performance.now() - started)}`,
    });
    return new Response(JSON.stringify({ error: timedOut ? 'The service took too long to respond' : 'The service is unavailable' }), {
      status,
      headers: responseHeaders,
    });
  } finally {
    clearTimeout(timeout);
    request.signal.removeEventListener('abort', abortFromClient);
  }

  const responseHeaders = new Headers(upstream.headers);
  const versionedLogo = request.method === 'GET'
    && path === 'company/logo'
    && incomingUrl.searchParams.has('v');
  if (!versionedLogo) {
    responseHeaders.set('Cache-Control', 'no-store');
  }
  responseHeaders.set('X-Request-ID', requestId);
  responseHeaders.set('Server-Timing', appendServerTiming(responseHeaders.get('Server-Timing'),
    `cf_upstream;dur=${Math.round(performance.now() - started)}`));
  logProxyTiming(requestId, upstream.status, started);
  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  });
};

function logProxyTiming(requestId: string, status: number, started: number, timedOut = false) {
  console.log(JSON.stringify({
    event: 'proxy_timing',
    request_id: requestId,
    status,
    upstream_ms: Math.round(performance.now() - started),
    timed_out: timedOut,
  }));
}

function appendServerTiming(existing: string | null, timing: string) {
  return existing ? `${existing}, ${timing}` : timing;
}

function validRequestId(value: string | null) {
  return value != null && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}
