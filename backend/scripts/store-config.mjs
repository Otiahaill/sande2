#!/usr/bin/env node
/**
 * Stores the SANDE connection manifest in Cloudflare KV.
 *
 * "Safe place in Cloudflare" has two distinct parts, and they are deliberately
 * kept apart:
 *
 *   1. SECRETS (API keys, tokens, client secrets)
 *      → Workers Secrets (encrypted at rest, never readable via the API).
 *        Set with: node scripts/set-secrets.mjs --env=dev
 *
 *   2. MANIFEST (resource IDs, names, URLs, fingerprints — not secret)
 *      → a dedicated KV namespace named `sande-config`.
 *        Stored with: node scripts/store-config.mjs --env=dev
 *
 * The manifest is what lets any later tool (CI, another agent, a future
 * session) discover the whole wiring without asking you for IDs again.
 * Nothing sensitive is written here.
 *
 * Usage:
 *   CLOUDFLARE_API_TOKEN=xxx CLOUDFLARE_ACCOUNT_ID=xxx node scripts/store-config.mjs [--env dev|staging|production]
 */

import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const BACKEND_ROOT = resolve(__dirname, '..');
const API = 'https://api.cloudflare.com/client/v4';

const env = (process.argv.find((a) => a.startsWith('--env='))?.split('=')[1]) ?? 'dev';
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
    throw new Error(`Non-JSON response (${res.status}): ${text.slice(0, 400)}`);
  }
  if (!json.success) {
    const errors = (json.errors ?? []).map((e) => e.message ?? JSON.stringify(e)).join('; ');
    throw new Error(`Cloudflare ${method} ${path} failed (${res.status}): ${errors}`);
  }
  return json.result;
}

/** Finds the KV namespace used to hold the manifest. */
async function findConfigNamespace() {
  const all = await cf(`/accounts/${accountId}/storage/kv/namespaces?per_page=100`);
  const ns = all.find((k) => k.title === 'sande-config');
  if (!ns) {
    throw new Error(
      `KV namespace "sande-config" not found. Create it with:\n` +
        `  wrangler kv namespace create sande-config\n` +
        `or via the API:\n` +
        `  curl -X POST "https://api.cloudflare.com/client/v4/accounts/${accountId}/storage/kv/namespaces" \\\n` +
        `    -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \\\n` +
        `    -H "Content-Type: application/json" \\\n` +
        `    --data '{"title":"sande-config"}'`
    );
  }
  return ns.id;
}

/** Reads the resource IDs for this environment from wrangler.toml. */
async function readTomlBindings() {
  const toml = await readFile(resolve(BACKEND_ROOT, 'wrangler.toml'), 'utf8');
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
  const kvIds = [...section.matchAll(/id\s*=\s*"([^"]+)"/g)].map((m) => m[1]);
  const r2Bucket = section.match(/bucket_name\s*=\s*"([^"]+)"/)?.[1];

  return { databaseId: dbId, kvIds, r2Bucket };
}

const SCRIPT_NAMES = {
  dev: 'sande-backend-dev',
  staging: 'sande-backend-staging',
  production: 'sande-backend-prod',
};

const namespaceId = await findConfigNamespace();
const bindings = await readTomlBindings();

const manifest = {
  project: 'sande-2-online',
  environment: env,
  updatedAt: new Date().toISOString(),

  cloudflare: {
    accountId,
    worker: {
      name: SCRIPT_NAMES[env],
      scriptId: `${accountId}/${SCRIPT_NAMES[env]}`,
      // The actual workers.dev URL is assigned by Cloudflare on first deploy.
      url: process.env.SANDE_WORKER_URL ?? null,
      customDomain: process.env.SANDE_WORKER_DOMAIN ?? null,
    },
    d1: {
      name: `sande-platform-${ENV_SUFFIX}`,
      databaseId: bindings.databaseId,
    },
    kv: {
      session: { title: `sande-session-${ENV_SUFFIX}`, id: bindings.kvIds[0] ?? null },
      rateLimit: { title: `sande-ratelimit-${ENV_SUFFIX}`, id: bindings.kvIds[1] ?? null },
      config: { title: 'sande-config', id: namespaceId },
    },
    r2: {
      backups: { name: `sande-backups-${ENV_SUFFIX}` },
    },
  },

  google: {
    projectId: process.env.GOOGLE_CLOUD_PROJECT_ID ?? null,
    oauthClientId: process.env.GOOGLE_CLIENT_ID ?? null,
    // Server-side secret is NOT stored here — it lives in Workers Secrets.
    oauthClientSecretLocation: 'workers-secret:GOOGLE_CLIENT_SECRET',
    androidPackageName: 'com.example.sande',
    androidDebugSha1: process.env.SANDE_ANDROID_DEBUG_SHA1 ?? null,
    androidDebugSha256: process.env.SANDE_ANDROID_DEBUG_SHA256 ?? null,
  },

  android: {
    applicationId: 'com.example.sande',
    versionCode: 1,
    versionName: '1.0.0',
  },

  secretsLocation: 'workers-secret (encrypted at rest, not readable via API)',
  notes: [
    'This manifest contains identifiers only — never credentials.',
    'Real credentials live in Cloudflare Workers Secrets.',
    'See backend/docs/CONNECTION.md for the full key inventory.',
  ],
};

const key = `manifest:${env}`;
const res = await cf(`/accounts/${accountId}/storage/kv/namespaces/${namespaceId}/values/${key}`, {
  method: 'PUT',
  body: manifest,
});

console.log(`Manifest stored in KV "sande-config" (${namespaceId})`);
console.log(`  key: ${key}`);
console.log(`  size: ${JSON.stringify(manifest).length} bytes`);

if (res?.success !== false) {
  console.log('\nStored manifest:');
  console.log(JSON.stringify(manifest, null, 2));
}
