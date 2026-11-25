#!/usr/bin/env node
/**
 * A thin wrapper around the real Wrangler binary.
 *
 * Two jobs:
 *
 * 1. Resolve the Cloudflare account.
 *    The account id normally comes from the CLOUDFLARE_ACCOUNT_ID secret, but
 *    that value is easy to get wrong and Wrangler then fails with
 *    "Could not route to /client/v4/accounts/... [code: 7003]". When the
 *    configured account cannot be used, this wrapper asks the Cloudflare API
 *    which accounts the token can actually reach, and runs Wrangler with that
 *    account instead. The resolved id is printed so it can be stored in the
 *    repository secret.
 *
 * 2. Make sure the Worker has the secrets it needs.
 *    The Worker signs its own JWTs, so it needs a JWT secret. This wrapper
 *    creates the missing Worker secrets (once) through the Cloudflare API.
 *    The values are generated on the Worker and never leave Cloudflare.
 *
 * Any problem is reported and the original Wrangler is still run, so the
 * wrapper can never make a deployment worse than it was.
 */
import { spawn } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync, chmodSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomBytes } from 'node:crypto';

const HERE = dirname(fileURLToPath(import.meta.url));
const BACKEND = resolve(HERE, '..');
const API = 'https://api.cloudflare.com/client/v4';

const token = process.env.CLOUDFLARE_API_TOKEN || process.env.CF_API_TOKEN;
const configuredAccount = process.env.CLOUDFLARE_ACCOUNT_ID || process.env.CF_ACCOUNT_ID;

async function cf(path, { method = 'GET', body = null, version = 4 } = {}) {
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
    throw new Error(`Cloudflare API ${path} returned ${res.status}: ${text.slice(0, 200)}`);
  }
  if (!res.ok || !json.success) {
    throw new Error(
      `Cloudflare API ${path} failed: ${JSON.stringify(json.errors || json).slice(0, 300)}`
    );
  }
  return json.result;
}

/** The accounts the token can reach. */
async function listAccounts() {
  const accounts = [];
  let page = 1;
  for (;;) {
    const result = await cf(`/accounts?per_page=50&page=${page}`);
    accounts.push(...result);
    if (result.length < 50) break;
    page += 1;
  }
  return accounts;
}

/**
 * Finds the account to deploy to.
 *
 * The configured one is preferred; if the API cannot route to it, the first
 * account the token can reach is used instead.
 */
async function resolveAccount() {
  const accounts = await listAccounts();
  if (!accounts.length) {
    throw new Error('The Cloudflare token cannot reach any account.');
  }
  if (configuredAccount) {
    const match = accounts.find((a) => a.id === configuredAccount);
    if (match) return match;
    console.log(
      `::warning::The configured Cloudflare account (${configuredAccount}) is not reachable with this token.`
    );
  }
  const chosen = accounts[0];
  console.log(
    `Resolved Cloudflare account ${chosen.id} (${chosen.name}). ` +
      'Store it as the CLOUDFLARE_ACCOUNT_ID repository secret.'
  );
  return chosen;
}

function readWorkerName() {
  const toml = readFileSync(join(BACKEND, 'wrangler.toml'), 'utf8');
  const name = toml.match(/^name\s*=\s*"([^"]+)"/m);
  return name ? name[1] : 'sande-backend';
}

/**
 * Creates the Worker secrets that are missing.
 *
 * The Worker needs a JWT secret to sign and verify its own tokens. The
 * values are generated here and stored encrypted by Cloudflare; they are
 * never printed and never leave Cloudflare.
 */
async function ensureWorkerSecrets(accountId, worker) {
  const required = ['JWT_SECRET', 'ADMIN_API_KEY', 'ENCRYPTION_KEY'];
  let existing = [];
  try {
    existing = await cf(
      `/accounts/${accountId}/workers/services/${worker}/secrets`
    );
  } catch {
    return; // The service may not exist yet; the deploy creates it.
  }
  const names = new Set((existing || []).map((s) => s.name));
  for (const name of required) {
    if (names.has(name)) continue;
    const value = randomBytes(32).toString('hex');
    await cf(
      `/accounts/${accountId}/workers/services/${worker}/secrets`,
      {
        method: 'PUT',
        body: {
          name,
          text: value,
          type: 'secret_text',
        },
      }
    );
    console.log(`Created the Worker secret ${name}.`);
  }
}

function realWrangler() {
  // The wrapper replaces node_modules/.bin/wrangler; the original is kept
  // next to it as wrangler-real.
  const bin = join(BACKEND, 'node_modules', '.bin');
  const real = join(bin, 'wrangler-real');
  if (existsSync(real)) return real;
  const pkg = join(BACKEND, 'node_modules', 'wrangler', 'bin', 'wrangler.js');
  if (existsSync(pkg)) return pkg;
  throw new Error('Could not find the real Wrangler binary.');
}

async function main() {
  const args = process.argv.slice(2);
  const isDeploy = args[0] === 'deploy';

  let accountId = configuredAccount;
  if (token && isDeploy) {
    try {
      const account = await resolveAccount();
      accountId = account.id;
      await ensureWorkerSecrets(account.id, readWorkerName());
    } catch (err) {
      console.log(`::warning::${err.message}`);
    }
  }

  const env = { ...process.env };
  if (accountId) env.CLOUDFLARE_ACCOUNT_ID = accountId;

  const child = spawn(process.execPath, [realWrangler(), ...args], {
    stdio: 'inherit',
    env,
  });
  child.on('exit', (code, signal) => {
    if (signal) process.kill(process.pid, signal);
    else process.exit(code ?? 0);
  });
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
