interface Env {
  BACKEND_URL: string;
}

const hopByHopHeaders = new Set([
  'connection',
  'content-length',
  'host',
  'keep-alive',
  'transfer-encoding',
  'upgrade',
]);

export const onRequest: PagesFunction<Env> = async ({ request, env, params }) => {
  const backendUrl = env.BACKEND_URL?.replace(/\/$/, '');
  if (!backendUrl) {
    return new Response(JSON.stringify({ error: 'API proxy is not configured' }), {
      status: 500,
      headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' },
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

  const body = request.method === 'GET' || request.method === 'HEAD' ? undefined : request.body;
  const upstream = await fetch(targetUrl, {
    method: request.method,
    headers,
    body,
    redirect: 'manual',
  });

  const responseHeaders = new Headers(upstream.headers);
  responseHeaders.set('Cache-Control', 'no-store');
  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  });
};
