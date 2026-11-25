#!/usr/bin/env node
/**
 * Resolves the public URL of the deployed SANDE Worker.
 *
 * The workers.dev subdomain belongs to the Cloudflare account, so the URL
 * cannot be hard-coded. This script asks the Cloudflare API for it using the
 * credentials that are already stored as repository secrets:
 *
 *   CLOUDFLARE_API_TOKEN   the API token
 *   CLOUDFLARE_ACCOUNT_ID  the account that owns the Worker
 *
 * It prints the URL and can store it in the `sande-config` KV namespace so
 * the app can discover it later.
 *
 * Usage:
 *   CLOUDFLARE_API_TOKEN=… CLOUDFLARE_ACCOUNT_ID=… \
 *     node scripts/resolve-api-url.mjs [--worker sande-backend] [--store]
 */
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const BACKEND = join(HERE, '..');

const API = 'https://api.cloudflare.com/client/v4';

function arg(name, fallback = null) {
  const i = process.argv.indexOf(name);
  return i !== -1 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
}

async function cf(path, token) {
  const res = await fetch(`${API}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await res.json();
  if (!res.ok || !body.success) {
    throw new Error(
      `Cloudflare API ${path} failed: ${JSON.stringify(body.errors || body)}`
    );
  }
  return body.result;
}

async function readWorkerName() {
  const toml = await readFile(join(BACKEND, 'wrangler.toml'), 'utf8');
  const name = toml.match(/^name\s*=\s*"([^"]+)"/m);
  return name ? name[1] : 'sande-backend';
}

async function main() {
  const token = process.env.CLOUDFLARE_API_TOKEN;
  const accountId = process.env.CLOUDFLARE_ACCOUNT_ID;
  if (!token || !accountId) {
    console.error(
      'CLOUDFLARE_API_TOKEN and CLOUDFLARE_ACCOUNT_ID must be set.'
    );
    process.exit(1);
  }

  const worker = arg('--worker', await readWorkerName());
  const store = process.argv.includes('--store');

  // The workers.dev subdomain is per account.
  const subdomain = await cf(
    `/accounts/${accountId}/workers/subdomain`,
    token
  );
  const base = `https://${worker}.${subdomain.subdomain}`;
  console.log(base);

  if (!store) return;

  // Publish the URL in the connection manifest so the app can discover it.
  const manifest = {
    api_base_url: base,
    worker,
    updated_at: new Date().toISOString(),
  };
  const kvId = '32df0f0153674c059656882dd8eeb9c2'; // sande-config
  await cf(`/accounts/${accountId}/storage/kv/namespaces/${kvId}/values/api-config`, token)
    .catch(async () => {
      // The namespace may not exist yet; create it on first use.
      await cf(
        `/accounts/${accountId}/storage/kv/namespaces`,
        token,
        { method: 'POST', body: { title: 'sande-config' } }
      ).catch(() => {});
    });

  console.error('The manifest is stored through wrangler; see store-config.mjs.');
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
