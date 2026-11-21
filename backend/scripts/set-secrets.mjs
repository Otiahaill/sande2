#!/usr/bin/env node
/**
 * Sets Worker secrets through the Cloudflare REST API.
 *
 * Usage:
 *   CLOUDFLARE_API_TOKEN=xxx CLOUDFLARE_ACCOUNT_ID=xxx node scripts/set-secrets.mjs [--env dev|staging|production] [--script NAME]
 *
 * Secrets are read from the environment (never stored in files):
 *   GOOGLE_CLIENT_ID      - Google OAuth web client ID
 *   GOOGLE_CLIENT_SECRET  - Google OAuth web client secret
 *   JWT_SECRET            - random string for signing access/refresh tokens
 *   ENCRYPTION_KEY        - random string for encrypting stored data at rest
 *   ADMIN_API_KEY         - random string for admin API access
 *
 * With --generate, missing JWT_SECRET / ENCRYPTION_KEY / ADMIN_API_KEY are
 * generated locally with crypto.randomBytes and printed once so they can be
 * saved somewhere safe.
 */

import { randomBytes } from 'node:crypto';

const API = 'https://api.cloudflare.com/client/v4';

const env = (process.argv.find((a) => a.startsWith('--env='))?.split('=')[1]) ?? 'dev';
const generate = process.argv.includes('--generate');
const scriptArg = process.argv.find((a) => a.startsWith('--script='))?.split('=')[1];

const SCRIPT_NAMES = {
  dev: 'sande-backend-dev',
  staging: 'sande-backend-staging',
  production: 'sande-backend-prod',
};

const scriptName = scriptArg ?? SCRIPT_NAMES[env];
if (!scriptName) {
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

const generated = {};

function resolveSecret(name) {
  const fromEnv = process.env[name];
  if (fromEnv && fromEnv.length > 0) return fromEnv;

  if (generate && ['JWT_SECRET', 'ENCRYPTION_KEY', 'ADMIN_API_KEY'].includes(name)) {
    const value = randomBytes(32).toString('hex');
    generated[name] = value;
    return value;
  }

  return null;
}

const SECRET_NAMES = [
  'GOOGLE_CLIENT_ID',
  'GOOGLE_CLIENT_SECRET',
  'JWT_SECRET',
  'ENCRYPTION_KEY',
  'ADMIN_API_KEY',
];

console.log(`Setting secrets on Worker: ${scriptName} (env: ${env})\n`);

for (const name of SECRET_NAMES) {
  const value = resolveSecret(name);
  if (!value) {
    console.log(`  ${name} → SKIPPED (not set in environment)`);
    continue;
  }

  await cf(`/accounts/${accountId}/workers/scripts/${scriptName}/secrets`, {
    method: 'PUT',
    body: { name, text: value, type: 'secret_text' },
  });
  console.log(`  ${name} → set`);
}

if (Object.keys(generated).length > 0) {
  console.log('\n⚠ Generated secrets (save these now — they are not recoverable from Cloudflare):');
  for (const [name, value] of Object.entries(generated)) {
    console.log(`  ${name}=${value}`);
  }
}

console.log('\nSecrets updated. They take effect on the next Worker deployment.');
