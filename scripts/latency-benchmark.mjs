#!/usr/bin/env node

const args = new Map();
for (let index = 2; index < process.argv.length; index += 1) {
  const value = process.argv[index];
  if (value.startsWith('--')) {
    args.set(value.slice(2), process.argv[index + 1]?.startsWith('--') ? true : process.argv[++index]);
  }
}

const baseUrl = String(args.get('url') || process.env.BENCHMARK_BASE_URL || '').replace(/\/$/, '');
const iterations = Number(args.get('iterations') || process.env.BENCHMARK_ITERATIONS || 20);
const warmup = Number(args.get('warmup') || process.env.BENCHMARK_WARMUP || 3);
const mode = String(args.get('mode') || 'warm');
const bootstrapMode = String(process.env.BENCHMARK_BOOTSTRAP_MODE || 'endpoint');
const requireCloudflareTiming = process.env.BENCHMARK_REQUIRE_CF_TIMING === '1';
const requireProductionMetadata = process.env.BENCHMARK_REQUIRE_PRODUCTION_METADATA === '1';
const output = args.get('output');
const jsonOutput = args.get('json-output');
const username = process.env.BENCHMARK_USERNAME;
const password = process.env.BENCHMARK_PASSWORD;
const companyId = process.env.BENCHMARK_COMPANY_ID;
const customerId = process.env.BENCHMARK_CUSTOMER_ID;
const benchmarkMetadata = {
  label: metadataValue('BENCHMARK_RUN_LABEL', 'label'),
  pages_revision: metadataValue('BENCHMARK_PAGES_REVISION', 'pages-revision'),
  backend_revision: metadataValue('BENCHMARK_BACKEND_REVISION', 'backend-revision'),
  database_branch: metadataValue('BENCHMARK_DATABASE_BRANCH', 'database-branch'),
  region: metadataValue('BENCHMARK_REGION', 'region'),
  client_region: metadataValue('BENCHMARK_CLIENT_REGION', 'client-region'),
};

if (requireProductionMetadata) {
  const missing = ['pages_revision', 'backend_revision', 'database_branch', 'region', 'client_region']
    .filter((name) => !benchmarkMetadata[name]);
  if (missing.length > 0) {
    throw new Error(`Production benchmark metadata is required: ${missing.join(', ')}.`);
  }
}

if (!baseUrl || !username || !password || !companyId || !customerId) {
  throw new Error('Provide --url and BENCHMARK_USERNAME, BENCHMARK_PASSWORD, BENCHMARK_COMPANY_ID, BENCHMARK_CUSTOMER_ID.');
}
if (!['warm', 'cold'].includes(mode)) {
  throw new Error('mode must be warm or cold.');
}
if (!Number.isInteger(iterations) || iterations < 5 || !Number.isInteger(warmup) || warmup < 0) {
  throw new Error('iterations must be an integer >= 5 and warmup must be a non-negative integer.');
}
if (!process.env.BENCHMARK_ALLOW_MUTATIONS) {
  throw new Error('Set BENCHMARK_ALLOW_MUTATIONS=1 to include create-sale samples; use an isolated benchmark company.');
}

class CookieJar {
  #cookies = new Map();

  absorb(response) {
    const values = response.headers.getSetCookie?.() || [];
    for (const value of values) {
      const pair = value.split(';', 1)[0];
      const separator = pair.indexOf('=');
      if (separator > 0) this.#cookies.set(pair.slice(0, separator), pair.slice(separator + 1));
    }
  }

  header() {
    return [...this.#cookies].map(([name, value]) => `${name}=${value}`).join('; ');
  }
}

async function call(path, { method = 'GET', body, jar, csrf } = {}) {
  const requestId = crypto.randomUUID();
  const headers = new Headers({ Accept: 'application/json', 'X-Request-ID': requestId });
  const cookies = jar?.header();
  if (cookies) headers.set('Cookie', cookies);
  if (body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  if (method !== 'GET') {
    headers.set('X-Company-Id', companyId);
    headers.set('X-CSRF-TOKEN', csrf);
  }
  const started = performance.now();
  const response = await fetch(`${baseUrl}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  jar?.absorb(response);
  const elapsed = performance.now() - started;
  if (!response.ok) throw new Error(`${method} ${path} returned ${response.status}`);
  const text = await response.text();
  return {
    elapsed,
    body: text ? JSON.parse(text) : undefined,
    requestIdMatches: response.headers.get('X-Request-ID') === requestId,
    serverTiming: parseServerTiming(response.headers.get('Server-Timing')),
  };
}

function metadataValue(environmentName, argumentName) {
  const value = args.get(argumentName) ?? process.env[environmentName];
  if (value === undefined || value === true) {
    return undefined;
  }
  const normalized = String(value).trim();
  if (!/^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$/.test(normalized)) {
    throw new Error(`${environmentName} must be a short identifier using letters, numbers, ., _, :, /, or - only.`);
  }
  return normalized;
}

function parseServerTiming(value) {
  const timings = {};
  for (const entry of value?.split(',') || []) {
    const [name, ...parameters] = entry.trim().split(';');
    const duration = parameters.find((parameter) => parameter.trim().startsWith('dur='))?.trim().slice(4);
    const numericDuration = Number(duration);
    if (name && Number.isFinite(numericDuration)) timings[name] = numericDuration;
  }
  return timings;
}

async function session() {
  const jar = new CookieJar();
  const csrf = await call('/api/auth/csrf', { jar });
  const login = await call('/api/auth/login', {
    method: 'POST', jar, csrf: csrf.body.token, body: { username, password },
  });
  return {
    jar,
    csrf: csrf.body.token,
    loginElapsed: login.elapsed,
    loginRequestIdMatches: login.requestIdMatches,
    loginServerTiming: login.serverTiming,
  };
}

async function measure(name, operation) {
  const samples = [];
  const timingSamples = new Map();
  let requestIdMatches = 0;
  for (let index = 0; index < iterations + (mode === 'warm' ? warmup : 0); index += 1) {
    const result = await operation();
    if (index >= (mode === 'warm' ? warmup : 0)) {
      samples.push(result.elapsed);
      if (result.requestIdMatches) requestIdMatches += 1;
      for (const [metric, value] of Object.entries(result.serverTiming || {})) {
        if (!timingSamples.has(metric)) timingSamples.set(metric, []);
        timingSamples.get(metric).push(value);
      }
    }
  }
  samples.sort((left, right) => left - right);
  return {
    name,
    samples: samples.length,
    p50_ms: percentile(samples, 0.50),
    p95_ms: percentile(samples, 0.95),
    p99_ms: percentile(samples, 0.99),
    request_ids_observed: requestIdMatches,
    server_timing_samples: Object.fromEntries([...timingSamples].map(([metric, values]) => [metric, values.length])),
    server_timing: Object.fromEntries([...timingSamples].map(([metric, values]) => {
      values.sort((left, right) => left - right);
      return [metric, {
        p50_ms: percentile(values, 0.50),
        p95_ms: percentile(values, 0.95),
        p99_ms: percentile(values, 0.99),
      }];
    })),
  };
}

function percentile(values, p) {
  return Number(values[Math.min(values.length - 1, Math.ceil(values.length * p) - 1)].toFixed(2));
}

// Keep cold mode's first request inside the measured operation. Creating a
// session here would consume the post-reset request before any sample ran.
const shared = mode === 'warm' ? await session() : null;
const results = [];
results.push(await measure('login', async () => {
  const active = await session();
  return {
    elapsed: active.loginElapsed,
    requestIdMatches: active.loginRequestIdMatches,
    serverTiming: active.loginServerTiming,
  };
}));
results.push(await measure('bootstrap', async () => {
  const active = mode === 'cold' ? await session() : shared;
  const started = performance.now();
  if (bootstrapMode === 'parallel') {
    const responses = await Promise.all([
      call('/api/auth/me', { jar: active.jar }),
      call('/api/company/all', { jar: active.jar }),
    ]);
    return {
      elapsed: performance.now() - started,
      requestIdMatches: responses.every((response) => response.requestIdMatches),
      serverTiming: {},
    };
  } else {
    const response = await call('/api/auth/bootstrap', { jar: active.jar });
    return {
      elapsed: performance.now() - started,
      requestIdMatches: response.requestIdMatches,
      serverTiming: response.serverTiming,
    };
  }
}));
results.push(await measure('list', async () => {
  const active = mode === 'cold' ? await session() : shared;
  return call('/api/sales?page=0&size=25', { jar: active.jar });
}));
results.push(await measure('dashboard', async () => {
  const active = mode === 'cold' ? await session() : shared;
  return call('/api/dashboard?period=monthly', { jar: active.jar });
}));
results.push(await measure('create-sale', async () => {
  const active = mode === 'cold' ? await session() : shared;
  const result = await call('/api/sales', {
    method: 'POST', jar: active.jar, csrf: active.csrf,
    body: {
      saleDate: new Date().toISOString().slice(0, 10),
      customer: { id: Number(customerId) }, brokerName: 'benchmark', quality: 'benchmark',
      challanNo: null, billNo: null, balanceChallanColumnsByMeters: false, rate: 1,
      takaEntries: [{ takaNo: 900000000 + Math.floor(Math.random() * 999999), meters: 1 }],
    },
  });
  if (result.body?.id) await call(`/api/sales/${result.body.id}`, { method: 'DELETE', jar: active.jar, csrf: active.csrf });
  return result;
}));

if (requireCloudflareTiming && bootstrapMode !== 'parallel') {
  const incomplete = results
    .filter((row) => row.server_timing_samples.cf_upstream !== iterations)
    .map((row) => row.name);
  if (incomplete.length > 0) {
    throw new Error(`Missing cf_upstream Server-Timing on every sample for: ${incomplete.join(', ')}`);
  }
}

const report = {
  generated_at: new Date().toISOString(),
  mode,
  bootstrap_mode: bootstrapMode,
  base_url: new URL(baseUrl).origin,
  iterations,
  warmup: mode === 'warm' ? warmup : 0,
  metadata: Object.fromEntries(Object.entries(benchmarkMetadata).filter(([, value]) => value !== undefined)),
  results,
};
const markdown = [
  `# Latency benchmark (${mode})`, '',
  `Generated: ${report.generated_at}  `, `Target origin: ${report.base_url}`, '',
  ...Object.entries(report.metadata).map(([name, value]) => `${name}: ${value}  `),
  ...(Object.keys(report.metadata).length > 0 ? [''] : []),
  '| Path | Samples | p50 (ms) | p95 (ms) | p99 (ms) | Request IDs echoed |', '|---|---:|---:|---:|---:|---:|',
  ...results.map((row) => `| ${row.name} | ${row.samples} | ${row.p50_ms} | ${row.p95_ms} | ${row.p99_ms} | ${row.request_ids_observed}/${row.samples} |`), '',
  'Create-sale samples are cleaned up immediately and require an isolated benchmark company/customer.',
  '',
  '## Server-Timing headers', '',
  '| Path | Metric | p50 (ms) | p95 (ms) | p99 (ms) | Request IDs echoed |',
  '|---|---|---:|---:|---:|---:|',
  ...results.flatMap((row) => Object.entries(row.server_timing).map(([metric, timing]) =>
    `| ${row.name} | ${metric} | ${timing.p50_ms} | ${timing.p95_ms} | ${timing.p99_ms} | ${row.request_ids_observed}/${row.samples} |`)),
].join('\n');
console.log(markdown);
if (output) {
  const fs = await import('node:fs/promises');
  const path = await import('node:path');
  await fs.mkdir(path.dirname(path.resolve(output)), { recursive: true });
  await fs.writeFile(output, `${markdown}\n\n<!-- ${JSON.stringify(report)} -->\n`);
}
if (jsonOutput) {
  const fs = await import('node:fs/promises');
  const path = await import('node:path');
  await fs.mkdir(path.dirname(path.resolve(jsonOutput)), { recursive: true });
  await fs.writeFile(jsonOutput, `${JSON.stringify(report, null, 2)}\n`);
}
