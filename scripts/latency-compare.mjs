#!/usr/bin/env node

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';

const [beforePath, afterPath, outputPath] = process.argv.slice(2);
if (!beforePath || !afterPath) {
  throw new Error('Usage: node scripts/latency-compare.mjs before.json after.json [comparison.md]');
}

const before = JSON.parse(await readFile(beforePath, 'utf8'));
const after = JSON.parse(await readFile(afterPath, 'utf8'));
const beforeRows = new Map(before.results.map((row) => [row.name, row]));
const rows = after.results.map((row) => {
  const old = beforeRows.get(row.name);
  return {
    name: row.name,
    p50: [old?.p50_ms, row.p50_ms, delta(old?.p50_ms, row.p50_ms)],
    p95: [old?.p95_ms, row.p95_ms, delta(old?.p95_ms, row.p95_ms)],
    p99: [old?.p99_ms, row.p99_ms, delta(old?.p99_ms, row.p99_ms)],
  };
});
const timingRows = after.results.flatMap((row) => {
  const beforeTiming = beforeRows.get(row.name)?.server_timing || {};
  return Object.entries(row.server_timing || {}).map(([metric, timing]) => {
    const old = beforeTiming[metric];
    return {
      name: row.name,
      metric,
      p50: [old?.p50_ms, timing.p50_ms, delta(old?.p50_ms, timing.p50_ms)],
      p95: [old?.p95_ms, timing.p95_ms, delta(old?.p95_ms, timing.p95_ms)],
      p99: [old?.p99_ms, timing.p99_ms, delta(old?.p99_ms, timing.p99_ms)],
    };
  });
});
const markdown = [
  '# Latency before/after comparison', '',
  `Before: ${before.generated_at} (${before.mode})  `,
  `After: ${after.generated_at} (${after.mode})`,
  `Before metadata: ${formatMetadata(before.metadata)}`,
  `After metadata: ${formatMetadata(after.metadata)}`, '',
  '| Path | Before p50 | After p50 | Delta | Before p95 | After p95 | Delta | Before p99 | After p99 | Delta |',
  '|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|',
  ...rows.map((row) => `| ${row.name} | ${format(row.p50[0])} | ${format(row.p50[1])} | ${formatDelta(row.p50[2])} | ${format(row.p95[0])} | ${format(row.p95[1])} | ${formatDelta(row.p95[2])} | ${format(row.p99[0])} | ${format(row.p99[1])} | ${formatDelta(row.p99[2])} |`),
  '', '## Server-Timing comparison', '',
  '| Path | Metric | Before p50 | After p50 | Delta | Before p95 | After p95 | Delta | Before p99 | After p99 | Delta |',
  '|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|',
  ...timingRows.map((row) => `| ${row.name} | ${row.metric} | ${format(row.p50[0])} | ${format(row.p50[1])} | ${formatDelta(row.p50[2])} | ${format(row.p95[0])} | ${format(row.p95[1])} | ${formatDelta(row.p95[2])} | ${format(row.p99[0])} | ${format(row.p99[1])} | ${formatDelta(row.p99[2])} |`),
].join('\n');
console.log(markdown);
if (outputPath) {
  await mkdir(dirname(resolve(outputPath)), { recursive: true });
  await writeFile(outputPath, `${markdown}\n`);
}

function delta(beforeValue, afterValue) {
  return beforeValue == null || afterValue == null ? null : Number((afterValue - beforeValue).toFixed(2));
}

function format(value) {
  return value == null ? 'n/a' : value.toFixed(2);
}

function formatDelta(value) {
  return value == null ? 'n/a' : `${value > 0 ? '+' : ''}${value.toFixed(2)}`;
}

function formatMetadata(metadata) {
  return metadata && Object.keys(metadata).length > 0
    ? Object.entries(metadata).map(([name, value]) => `${name}=${value}`).join(', ')
    : 'not supplied';
}
