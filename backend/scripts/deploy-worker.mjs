#!/usr/bin/env node
/**
 * Deploys the SANDE backend Worker through the Cloudflare REST API.
 *
 * Wrangler's deploy path needs workerd (no Android build), so this performs the
 * upload directly. The Worker source is bundled first with esbuild.
 *
 * Usage:
 *   CLOUDFLARE_API_TOKEN=xxx CLOUDFLARE_ACCOUNT_ID=xxx node scripts/deploy-worker.mjs [--env dev|staging|production] [--dry-run]
 *
 * CI still uses cloudflare/wrangler-action on Linux runners (see
 * .github/workflows/backend-ci.yml) — this script exists so the same deploy can
 * be done from a machine without workerd.
 */

import { readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import * as ts from 'typescript';

const __dirname = dirname(fileURLToPath(import.meta.url));
const BACKEND_ROOT = resolve(__dirname, '..');
const API = 'https://api.cloudflare.com/client/v4';

const env = (process.argv.find((a) => a.startsWith('--env='))?.split('=')[1]) ?? 'dev';
const dryRun = process.argv.includes('--dry-run');

const ENV_SUFFIX = { dev: 'dev', staging: 'staging', production: 'prod' }[env];
if (!ENV_SUFFIX) {
  console.error('Unknown --env. Use dev | staging | production');
  process.exit(1);
}

const token = process.env.CLOUDFLARE_API_TOKEN;
const accountId = process.env.CLOUDFLARE_ACCOUNT_ID;

if (!token || !accountId) {
  console.error('Missing CLOUDFLARE_API_TOKEN or CLOUDFLARE_ACCOUNT_ID');
  process.exit(1);
}

const SCRIPT_NAMES = {
  dev: 'sande-backend-dev',
  staging: 'sande-backend-staging',
  production: 'sande-backend-prod',
};
const scriptName = SCRIPT_NAMES[env];

async function cf(path, { method = 'GET', body, headers = {} } = {}) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    throw new Error(`Non-JSON response (${res.status}): ${text.slice(0, 400)}`);
  }
  if (!json.success) {
    const errors = (json.errors ?? []).map((e) => e.message ?? JSON.stringify(e)).join('; ');
    throw new Error(`Cloudflare ${method} ${path} failed (${res.status}): ${errors}`);
  }
  return json.result;
}

/** Reads the resource IDs for this environment out of wrangler.toml. */
async function readBindings() {
  const toml = await readFile(resolve(BACKEND_ROOT, 'wrangler.toml'), 'utf8');

  // Slice out just this environment's section (plus the top-level for dev).
  let section = toml;
  if (env === 'staging') {
    const start = toml.indexOf('[env.staging]');
    section = start >= 0 ? toml.slice(start) : toml;
  } else if (env === 'production') {
    const start = toml.indexOf('[env.production]');
    section = start >= 0 ? toml.slice(start) : toml;
  } else {
    const start = toml.indexOf('[env.staging]');
    section = start >= 0 ? toml.slice(0, start) : toml;
  }

  const dbId = section.match(/database_id\s*=\s*"([^"]+)"/)?.[1];
  const sessionKv = section.match(/id\s*=\s*"([^"]+)"/)?.[1];
  const kvIds = [...section.matchAll(/^\s*id\s*=\s*"([^"]+)"/gm)].map((m) => m[1]);
  const r2Bucket = section.match(/bucket_name\s*=\s*"([^"]+)"/)?.[1];

  const placeholders = [dbId, ...kvIds, r2Bucket].filter((v) => v && v.startsWith('PLACEHOLDER'));

  return {
    databaseId: dbId,
    kvIds,
    r2Bucket,
    hasPlaceholders: placeholders.length > 0,
  };
}

/** Reads and transpiles the TypeScript Worker source to JavaScript.
 *
 * Uses TypeScript's transpileModule API (no child process needed, works on Android).
 * Cloudflare Workers requires JavaScript content type.
 */
async function readSource() {
  const entryFile = resolve(BACKEND_ROOT, 'src/index.ts');
  const code = await readFile(entryFile, 'utf8');

  const result = ts.transpileModule(code, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
      strict: true,
      esModuleInterop: true,
      skipLibCheck: true,
    },
    fileName: 'index.ts',
    reportDiagnostics: true,
  });

  if (result.diagnostics && result.diagnostics.length > 0) {
    for (const d of result.diagnostics) {
      console.warn('  TS diagnostic:', d.messageText);
    }
  }

  console.log('  source transpiled →', result.outputText.length, 'bytes');
  return result.outputText;
}

const bindings = await readBindings();

if (bindings.hasPlaceholders) {
  console.error('wrangler.toml still contains PLACEHOLDER ids for this environment.');
  console.error('Run: node scripts/cloudflare-setup.mjs --env=' + env);
  process.exit(1);
}

console.log(`Deploying ${scriptName} (env: ${env})`);
console.log(`  D1   : ${bindings.databaseId}`);
console.log(`  KV   : ${bindings.kvIds.join(', ')}`);
console.log(`  R2   : ${bindings.r2Bucket}`);

const code = await readSource();

if (dryRun) {
  console.log('\n--dry-run: not uploading.');
  process.exit(0);
}

/**
 * Worker upload is multipart/form-data:
 *   metadata  – JSON describing the script, bindings and settings
 *   index.js  – the transpiled module
 */
const metadata = {
  main_module: 'index.js',
  compatibility_date: '2026-11-17',
  compatibility_flags: ['nodejs_compat'],
  module_rules: [
    { type: 'ESModule', filter: '.*\\.js$' },
  ],
  bindings: [
    { type: 'd1', name: 'DB', id: bindings.databaseId },
    { type: 'r2_bucket', name: 'R2_BACKUPS', bucket_name: bindings.r2Bucket },
    ...bindings.kvIds.map((id, index) => ({
      type: 'kv_namespace',
      name: index === 0 ? 'SESSION_KV' : 'RATE_LIMIT_KV',
      namespace_id: id,
    })),
  ],
  observability: { enabled: true },
};

// Build multipart body manually for full control
const boundary = '----SandeFormBoundary' + Math.random().toString(16).slice(2);
const parts = [];

// Module code part first
parts.push(
  `--${boundary}`,
  'Content-Disposition: form-data; name="index.js"; filename="index.js"',
  'Content-Type: application/javascript',
  '',
  code
);

// Metadata part
parts.push(
  `--${boundary}`,
  'Content-Disposition: form-data; name="metadata"',
  '',
  JSON.stringify(metadata)
);

parts.push(`--${boundary}--`);

const body = parts.join('\r\n');

console.log('\nUploading Worker...');
const res = await fetch(`${API}/accounts/${accountId}/workers/scripts/${scriptName}`, {
  method: 'PUT',
  headers: {
    Authorization: `Bearer ${token}`,
    'Content-Type': `multipart/form-data; boundary=${boundary}`,
  },
  body,
});

const text = await res.text();
let json;
try {
  json = JSON.parse(text);
} catch {
  throw new Error(`Non-JSON response (${res.status}): ${text.slice(0, 600)}`);
}
if (!json.success) {
  const errors = (json.errors ?? []).map((e) => e.message ?? JSON.stringify(e)).join('; ');
  throw new Error(`Upload failed (${res.status}): ${errors}`);
}

console.log('  deployed ✓');

const worker = await cf(`/accounts/${accountId}/workers/scripts/${scriptName}`);
console.log(`\nWorker: ${worker.id}`);
console.log(`  created : ${worker.created_on}`);
console.log(`  modified: ${worker.modified_on}`);

console.log(`\nHealth check: curl https://${scriptName}.your-subdomain.workers.dev/health`);
console.log('(Use the actual workers.dev URL shown in the Cloudflare dashboard.)');
