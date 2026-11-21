#!/usr/bin/env node
/**
 * Applies D1 migrations through the Cloudflare REST API.
 *
 * Wrangler's `d1 migrations apply` needs the workerd binary, which has no
 * Android build. This applies the same SQL files directly via the D1 query API.
 *
 * Usage:
 *   CLOUDFLARE_API_TOKEN=xxx CLOUDFLARE_ACCOUNT_ID=xxx node scripts/d1-migrate.mjs [--env dev|staging|production] [--dry-run]
 *
 * Migrations are tracked in a `d1_migrations` table so each file runs once.
 */

import { readdir, readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const BACKEND_ROOT = resolve(__dirname, '..');
const MIGRATIONS_DIR = resolve(BACKEND_ROOT, 'migrations');

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
const databaseName = `sande-platform-${ENV_SUFFIX}`;

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

/** Finds the database by name and returns its id. */
async function findDatabase() {
  const all = await cf(`/accounts/${accountId}/d1/database?per_page=100`);
  const db = all.find((d) => d.name === databaseName);
  if (!db) {
    throw new Error(`D1 database "${databaseName}" not found. Run cloudflare-setup.mjs first.`);
  }
  return db.uuid;
}

/** Runs one SQL statement (possibly multi-statement) against the database. */
async function exec(databaseId, sql) {
  return cf(`/accounts/${accountId}/d1/database/${databaseId}/query`, {
    method: 'POST',
    body: { sql },
  });
}

const databaseId = await findDatabase();
console.log(`Database: ${databaseName} (${databaseId})`);

if (dryRun) {
  const files = (await readdir(MIGRATIONS_DIR)).filter((f) => f.endsWith('.sql')).sort();
  console.log(`\nWould apply ${files.length} migration file(s):`);
  for (const f of files) console.log(`  - ${f}`);
  process.exit(0);
}

// Migration tracking table
await exec(
  databaseId,
  `CREATE TABLE IF NOT EXISTS d1_migrations (
     id TEXT PRIMARY KEY NOT NULL,
     name TEXT NOT NULL,
     applied_at INTEGER NOT NULL
   )`
);

const applied = await exec(
  databaseId,
  `SELECT name FROM d1_migrations`
);
const appliedNames = new Set((applied.results ?? []).map((r) => r.name));

const files = (await readdir(MIGRATIONS_DIR)).filter((f) => f.endsWith('.sql')).sort();

let count = 0;
for (const file of files) {
  const name = file.replace(/\.sql$/, '');
  if (appliedNames.has(name)) {
    console.log(`  ${file} → already applied`);
    continue;
  }

  const sql = await readFile(resolve(MIGRATIONS_DIR, file), 'utf8');
  console.log(`  ${file} → applying...`);

  try {
    await exec(databaseId, sql);
  } catch (err) {
    console.error(`\nMigration ${file} FAILED: ${err.message}`);
    console.error('Fix the SQL and re-run — already-applied migrations are skipped.');
    process.exit(1);
  }

  await exec(
    databaseId,
    `INSERT INTO d1_migrations (id, name, applied_at) VALUES ('${name}', '${name}', ${Date.now()})`
  );
  console.log(`  ${file} → applied`);
  count++;
}

console.log(`\nDone. ${count} migration(s) applied, ${files.length - count} skipped.`);

// Show a quick table count so we can confirm the schema landed
const tables = await exec(
  databaseId,
  `SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name`
);
console.log(`\nTables now present (${tables.results.length}):`);
console.log('  ' + (tables.results ?? []).map((t) => t.name).join(', '));
