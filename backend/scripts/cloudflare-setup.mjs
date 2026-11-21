#!/usr/bin/env node
/**
 * Cloudflare resource setup via REST API.
 *
 * Wrangler cannot run on Android (workerd has no android-arm64 build), so this
 * script performs the same operations through the Cloudflare REST API. It works
 * identically from a Linux/macOS dev machine and from CI.
 *
 * Usage:
 *   CLOUDFLARE_API_TOKEN=xxx CLOUDFLARE_ACCOUNT_ID=xxx node scripts/cloudflare-setup.mjs [--env dev|staging|production]
 *
 * What it creates:
 *   - D1 database   : sande-platform-<env>
 *   - KV namespaces : SESSION_KV, RATE_LIMIT_KV
 *   - R2 bucket     : sande-backups-<env>
 *
 * It then writes the real resource IDs back into wrangler.toml, replacing the
 * PLACEHOLDER_* markers.
 */

import { readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const BACKEND_ROOT = resolve(__dirname, '..');
const WRANGLER_TOML = resolve(BACKEND_ROOT, 'wrangler.toml');

const API = 'https://api.cloudflare.com/client/v4';

const env = (process.argv.find((a) => a.startsWith('--env='))?.split('=')[1]) ?? 'dev';

const ENV_SUFFIX = {
  dev: 'dev',
  staging: 'staging',
  production: 'prod',
}[env];

if (!ENV_SUFFIX) {
  console.error(`Unknown --env. Use dev | staging | production`);
  process.exit(1);
}

const token = process.env.CLOUDFLARE_API_TOKEN;
const accountId = process.env.CLOUDFLARE_ACCOUNT_ID;

if (!token || !accountId) {
  console.error('Missing credentials.');
  console.error('  CLOUDFLARE_API_TOKEN  — https://dash.cloudflare.com/profile/api-tokens');
  console.error('  CLOUDFLARE_ACCOUNT_ID — https://dash.cloudflare.com (right-hand sidebar)');
  process.exit(1);
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
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    throw new Error(`Non-JSON response from Cloudflare (${res.status}): ${text.slice(0, 400)}`);
  }

  if (!json.success) {
    const errors = (json.errors ?? []).map((e) => e.message ?? JSON.stringify(e)).join('; ');
    throw new Error(`Cloudflare ${method} ${path} failed (${res.status}): ${errors || 'unknown error'}`);
  }
  return json.result;
}

/** Finds an existing resource by name, or null. */
async function findExisting(kind, name) {
  const listers = {
    d1: async () =>
      (await cf(`/accounts/${accountId}/d1/database?per_page=100`)).filter((d) => d.name === name),
    kv: async () =>
      (await cf(`/accounts/${accountId}/storage/kv/namespaces?per_page=100`)).filter((k) => k.title === name),
    r2: async () => {
      const result = await cf(`/accounts/${accountId}/r2/buckets?per_page=100`);
      const buckets = result?.result ?? result ?? [];
      return buckets.filter((b) => b.name === name);
    },
  };

  const results = await listers[kind]();
  return results.length > 0 ? results[0] : null;
}

async function ensureD1(name) {
  const existing = await findExisting('d1', name);
  if (existing) {
    console.log(`  D1 ${name} → already exists (${existing.uuid})`);
    return existing.uuid;
  }
  const created = await cf(`/accounts/${accountId}/d1/database`, {
    method: 'POST',
    body: { name },
  });
  console.log(`  D1 ${name} → created (${created.uuid})`);
  return created.uuid;
}

async function ensureKv(title) {
  const existing = await findExisting('kv', title);
  if (existing) {
    console.log(`  KV ${title} → already exists (${existing.id})`);
    return existing.id;
  }
  const created = await cf(`/accounts/${accountId}/storage/kv/namespaces`, {
    method: 'POST',
    body: { title },
  });
  console.log(`  KV ${title} → created (${created.id})`);
  return created.id;
}

async function ensureR2(name) {
  const existing = await findExisting('r2', name);
  if (existing) {
    console.log(`  R2 ${name} → already exists`);
    return name;
  }
  await cf(`/accounts/${accountId}/r2/buckets/${name}`, { method: 'PUT' });
  console.log(`  R2 ${name} → created`);
  return name;
}

/** Rewrites wrangler.toml, filling in resource IDs for the target environment. */
async function patchWrangler(ids) {
  let toml = await readFile(WRANGLER_TOML, 'utf8');

  const sectionHeader =
    env === 'dev' ? '' : env === 'staging' ? '[env.staging]' : '[env.production]';

  // Replace the PLACEHOLDER ids that belong to this environment only.
  const replacements = [
    // database id
    {
      pattern: env === 'dev'
        ? /database_id = "PLACEHOLDER_DEV_DB_ID"/
        : env === 'staging'
          ? /database_id = "PLACEHOLDER_STAGING_DB_ID"/
          : /database_id = "PLACEHOLDER_PROD_DB_ID"/,
      value: `database_id = "${ids.d1}"`,
    },
    {
      pattern: env === 'dev'
        ? /id = "PLACEHOLDER_DEV_SESSION_KV_ID"/
        : env === 'staging'
          ? /id = "PLACEHOLDER_STAGING_SESSION_KV_ID"/
          : /id = "PLACEHOLDER_PROD_SESSION_KV_ID"/,
      value: `id = "${ids.sessionKv}"`,
    },
    {
      pattern: env === 'dev'
        ? /id = "PLACEHOLDER_DEV_RATE_LIMIT_KV_ID"/
        : env === 'staging'
          ? /id = "PLACEHOLDER_STAGING_RATE_LIMIT_KV_ID"/
          : /id = "PLACEHOLDER_PROD_RATE_LIMIT_KV_ID"/,
      value: `id = "${ids.rateLimitKv}"`,
    },
  ];

  for (const { pattern, value } of replacements) {
    if (!pattern.test(toml)) {
      console.warn(`  ! pattern not found in wrangler.toml: ${pattern}`);
      continue;
    }
    toml = toml.replace(pattern, value);
  }

  await writeFile(WRANGLER_TOML, toml, 'utf8');
  console.log(`  wrangler.toml → updated for "${env}"`);
}

console.log(`Cloudflare setup — environment: ${env} (suffix: ${ENV_SUFFIX})`);

const d1Name = `sande-platform-${ENV_SUFFIX}`;
const kvSessionTitle = `sande-session-${ENV_SUFFIX}`;
const kvRateTitle = `sande-ratelimit-${ENV_SUFFIX}`;
const r2Name = `sande-backups-${ENV_SUFFIX}`;

const ids = {
  d1: await ensureD1(d1Name),
  sessionKv: await ensureKv(kvSessionTitle),
  rateLimitKv: await ensureKv(kvRateTitle),
  r2: await ensureR2(r2Name),
};

await patchWrangler(ids);

console.log('\nDone. Resource IDs:');
console.log(`  D1 database   ${d1Name}          : ${ids.d1}`);
console.log(`  KV SESSION_KV ${kvSessionTitle} : ${ids.sessionKv}`);
console.log(`  KV RATE_LIMIT ${kvRateTitle} : ${ids.rateLimitKv}`);
console.log(`  R2 bucket     ${r2Name}        : ${ids.r2}`);
console.log('\nNext: run the D1 migrations, then set secrets, then deploy.');
