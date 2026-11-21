#!/usr/bin/env node
/**
 * Verifies the full SANDE connection: Cloudflare resources, D1 schema, Worker
 * health endpoint, and the Google OAuth configuration.
 *
 * Usage:
 *   CLOUDFLARE_API_TOKEN=xxx CLOUDFLARE_ACCOUNT_ID=xxx node scripts/verify-connection.mjs [--env dev|staging|production]
 */

import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const API = 'https://api.cloudflare.com/client/v4';

const env = (process.argv.find((a) => a.startsWith('--env='))?.split('=')[1]) ?? 'dev';
const ENV_SUFFIX = { dev: 'dev', staging: 'staging', production: 'prod' }[env];

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

let pass = 0;
let fail = 0;
const failures = [];

function check(label, ok, detail = '') {
  if (ok) {
    pass++;
    console.log(`  ✓ ${label}${detail ? ' — ' + detail : ''}`);
  } else {
    fail++;
    failures.push(label);
    console.log(`  ✗ ${label}${detail ? ' — ' + detail : ''}`);
  }
}

async function cf(path, { method = 'GET', body } = {}) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch {
    return { success: false, errors: [{ message: text.slice(0, 200) }] };
  }
}

console.log(`\nSANDE connection check — environment: ${env}\n`);

// ---- 1. Token works -------------------------------------------------------
console.log('Cloudflare credentials');
const verify = await cf('/user/tokens/verify');
check('API token is valid', verify.success === true, verify.success ? 'active' : 'rejected');
check('Account ID reachable', Boolean(accountId), accountId);

if (!verify.success) {
  console.log('\nCannot continue without a valid token.');
  process.exit(1);
}

// ---- 2. Resources exist ----------------------------------------------------
console.log('\nCloudflare resources');

const d1Name = `sande-platform-${ENV_SUFFIX}`;
const d1All = await cf(`/accounts/${accountId}/d1/database?per_page=100`);
const d1 = d1All.success ? d1All.result.find((d) => d.name === d1Name) : null;
check(`D1 database "${d1Name}"`, Boolean(d1), d1?.uuid ?? 'not found');

const kvAll = await cf(`/accounts/${accountId}/storage/kv/namespaces?per_page=100`);
const kvList = kvAll.success ? kvAll.result : [];
const sessionKv = kvList.find((k) => k.title === `sande-session-${ENV_SUFFIX}`);
const rateKv = kvList.find((k) => k.title === `sande-ratelimit-${ENV_SUFFIX}`);
const configKv = kvList.find((k) => k.title === 'sande-config');
check(`KV "${sande-session-${ENV_SUFFIX}}"`, Boolean(sessionKv), sessionKv?.id ?? 'not found');
check(`KV "${sande-ratelimit-${ENV_SUFFIX}}"`, Boolean(rateKv), rateKv?.id ?? 'not found');
check(`KV "sande-config" (manifest store)`, Boolean(configKv), configKv?.id ?? 'not found');

const r2All = await cf(`/accounts/${accountId}/r2/buckets?per_page=100`);
const r2 = r2All.success ? r2All.result.find((b) => b.name === `sande-backups-${ENV_SUFFIX}`) : null;
check(`R2 bucket "${sande-backups-${ENV_SUFFIX]}"`, Boolean(r2), r2?.name ?? 'not found');

// ---- 3. Worker deployed ----------------------------------------------------
console.log('\nWorker deployment');
const worker = await cf(`/accounts/${accountId}/workers/scripts/${scriptName}`);
check(`Worker "${scriptName}" deployed`, worker.success === true);

let workerUrl = null;
if (worker.success) {
  const subdomain = await cf(`/accounts/${accountId}/workers/subdomain`);
  if (subdomain.success && subdomain.result?.subdomain) {
    workerUrl = `https://${scriptName}.${subdomain.result.subdomain}.workers.dev`;
  }
}

// ---- 4. Secrets set --------------------------------------------------------
console.log('\nWorker secrets');
if (worker.success) {
  const secrets = await cf(`/accounts/${accountId}/workers/scripts/${scriptName}/secrets`);
  const secretNames = secrets.success ? (secrets.result ?? []).map((s) => s.name) : [];
  for (const name of [
    'GOOGLE_CLIENT_ID',
    'GOOGLE_CLIENT_SECRET',
    'JWT_SECRET',
    'ENCRYPTION_KEY',
    'ADMIN_API_KEY',
  ]) {
    check(`secret ${name}`, secretNames.includes(name));
  }
} else {
  check('Worker secrets', false, 'worker not deployed');
}

// ---- 5. D1 schema ----------------------------------------------------------
console.log('\nD1 schema');
if (d1) {
  const tablesRes = await cf(`/accounts/${accountId}/d1/database/${d1.uuid}/query`, {
    method: 'POST',
    body: { sql: `SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'` },
  });
  const tables = tablesRes.success ? (tablesRes.result?.results ?? []).map((t) => t.name) : [];

  const required = [
    'businesses', 'accounts', 'memberships', 'invitations', 'devices',
    'refresh_tokens', 'recovery_codes', 'sync_batches', 'change_log',
    'backups', 'app_versions', 'platform_admins', 'support_access_grants',
    'products', 'product_categories', 'product_price_history', 'stock_movements',
    'sale_sessions', 'sale_items', 'receipts', 'counters', 'audit_events',
    'membership_mirrors', 'device_registrations', 'outbox_entries',
    'sync_cursors', 'sync_exceptions', 'business_profiles',
  ];
  const missing = required.filter((t) => !tables.includes(t));
  check(
    `All ${required.length} required tables present`,
    missing.length === 0,
    missing.length > 0 ? `missing: ${missing.join(', ')}` : `${tables.length} tables total`
  );

  const migrations = await cf(`/accounts/${accountId}/d1/database/${d1.uuid}/query`, {
    method: 'POST',
    body: { sql: `SELECT name FROM d1_migrations ORDER BY name` },
  });
  const applied = migrations.success ? (migrations.result?.results ?? []).map((m) => m.name) : [];
  check('Migrations recorded', applied.length >= 2, applied.join(', ') || 'none');
}

// ---- 6. Health endpoint ----------------------------------------------------
console.log('\nWorker health');
if (workerUrl) {
  try {
    const res = await fetch(`${workerUrl}/health`, { signal: AbortSignal.timeout(15000) });
    const body = await res.json();
    check('GET /health responds 200', res.status === 200, `status=${body?.status}`);
    check('D1 reachable from Worker', body?.checks?.d1 === 'ok');
    check('R2 reachable from Worker', body?.checks?.r2 === 'ok');
    check('KV reachable from Worker', body?.checks?.kv === 'ok');
  } catch (err) {
    check('GET /health', false, err.message);
  }
} else {
  check('Worker URL resolvable', false, 'no workers.dev subdomain found');
}

// ---- 7. Google configuration ----------------------------------------------
console.log('\nGoogle OAuth');
const manifestRes = configKv
  ? await cf(`/accounts/${accountId}/storage/kv/namespaces/${configKv.id}/values/manifest:${env}`)
  : { success: false };
if (manifestRes.success && manifestRes.result) {
  let manifest = null;
  try {
    manifest = JSON.parse(manifestRes.result);
  } catch {
    /* fall through */
  }
  check('Connection manifest stored in KV', Boolean(manifest));
  if (manifest) {
    check('Google project ID recorded', Boolean(manifest.google?.projectId));
    check('OAuth client ID recorded', Boolean(manifest.google?.oauthClientId));
    check('Android SHA-1 recorded', Boolean(manifest.google?.androidDebugSha1));
  }
} else {
  check('Connection manifest stored in KV', false, 'run store-config.mjs');
}

if (process.env.GOOGLE_CLIENT_ID) {
  check('GOOGLE_CLIENT_ID in environment', true);
} else {
  console.log('  · GOOGLE_CLIENT_ID not in this shell (it lives in Workers Secrets) — OK');
}

// ---- Summary ---------------------------------------------------------------
console.log(`\n${'─'.repeat(60)}`);
console.log(`Passed: ${pass}   Failed: ${fail}`);
if (fail > 0) {
  console.log('\nFailed checks:');
  for (const f of failures) console.log(`  - ${f}`);
  process.exit(1);
}
console.log('\n✅ Connection is fully wired.');
