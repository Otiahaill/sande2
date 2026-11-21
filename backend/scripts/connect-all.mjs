#!/usr/bin/env node
/**
 * One-shot connection: creates resources, migrates the schema, sets secrets,
 * deploys the Worker, and stores the connection manifest.
 *
 * Usage:
 *   CLOUDFLARE_API_TOKEN=xxx \
 *   CLOUDFLARE_ACCOUNT_ID=xxx \
 *   GOOGLE_CLIENT_ID=xxx \
 *   GOOGLE_CLIENT_SECRET=xxx \
 *   GOOGLE_CLOUD_PROJECT_ID=xxx \
 *   node scripts/connect-all.mjs [--env dev|staging|production] [--generate-secrets]
 */

import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const API = 'https://api.cloudflare.com/client/v4';

const env = (process.argv.find((a) => a.startsWith('--env='))?.split('=')[1]) ?? 'dev';
const generateSecrets = process.argv.includes('--generate-secrets');

const required = [
  'CLOUDFLARE_API_TOKEN',
  'CLOUDFLARE_ACCOUNT_ID',
  'GOOGLE_CLIENT_ID',
  'GOOGLE_CLIENT_SECRET',
];

const missing = required.filter((k) => !process.env[k]);
if (missing.length > 0) {
  console.error('Missing environment variables:');
  for (const m of missing) console.error(`  ${m}`);
  console.error('\nSee backend/docs/CONNECTION.md for where to get each one.');
  process.exit(1);
}

/** Runs a child script, streaming output, and fails loudly on a non-zero exit. */
function run(script, args = []) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(process.execPath, [resolve(__dirname, script), ...args], {
      stdio: 'inherit',
      env: process.env,
    });
    child.on('exit', (code) => {
      if (code === 0) resolvePromise();
      else reject(new Error(`${script} exited with code ${code}`));
    });
  });
}

/** Reads the workers.dev subdomain so we can print the live URL. */
async function readSubdomain() {
  const res = await fetch(`${API}/accounts/${process.env.CLOUDFLARE_ACCOUNT_ID}/workers/subdomain`, {
    headers: { Authorization: `Bearer ${process.env.CLOUDFLARE_API_TOKEN}` },
  });
  const json = await res.json();
  return json.success ? json.result?.subdomain : null;
}

const steps = [
  { label: 'Creating Cloudflare resources (D1, KV, R2)', script: 'cloudflare-setup.mjs', args: [`--env=${env}`] },
  { label: 'Applying D1 schema', script: 'd1-migrate.mjs', args: [`--env=${env}`] },
  {
    label: 'Setting Worker secrets',
    script: 'set-secrets.mjs',
    args: [`--env=${env}`, ...(generateSecrets ? ['--generate'] : [])],
  },
  { label: 'Deploying the Worker', script: 'deploy-worker.mjs', args: [`--env=${env}`] },
  { label: 'Storing connection manifest in KV', script: 'store-config.mjs', args: [`--env=${env}`] },
];

for (let i = 0; i < steps.length; i++) {
  const step = steps[i];
  console.log(`\n${'━'.repeat(60)}`);
  console.log(`Step ${i + 1}/${steps.length}: ${step.label}`);
  console.log('━'.repeat(60));
  await run(step.script, step.args);
}

console.log(`\n${'━'.repeat(60)}`);
console.log('Connection complete');
console.log('━'.repeat(60));

const subdomain = await readSubdomain();
if (subdomain) {
  const scriptName = { dev: 'sande-backend-dev', staging: 'sande-backend-staging', production: 'sande-backend-prod' }[env];
  const url = `https://${scriptName}.${subdomain}.workers.dev`;
  console.log(`\nWorker URL : ${url}`);
  console.log(`Health     : ${url}/health`);
  console.log(`\nSet this in the Android app (Settings → API base URL):`);
  console.log(`  ${url}`);
}

console.log('\nFinal check:');
console.log(`  node scripts/verify-connection.mjs --env=${env}`);
