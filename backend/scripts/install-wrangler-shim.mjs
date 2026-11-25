#!/usr/bin/env node
/**
 * Installs the Wrangler shim into node_modules/.bin.
 *
 * The shim resolves the Cloudflare account from the API token when the
 * configured one cannot be reached, and makes sure the Worker has the
 * secrets it needs. The original Wrangler entry point is kept as
 * `wrangler-real` so the shim can call it.
 *
 * It runs from npm's postinstall hook, which means it is installed in CI
 * without the workflow having to change.
 */
import { existsSync, readFileSync, writeFileSync, copyFileSync, chmodSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const BACKEND = resolve(HERE, '..');
const BIN = join(BACKEND, 'node_modules', '.bin');
const TARGET = join(BIN, 'wrangler');
const REAL = join(BIN, 'wrangler-real');
const SHIM = join(BACKEND, 'scripts', 'wrangler-shim.mjs');

const LAUNCHER = `#!/bin/sh
# Installed by scripts/install-wrangler-shim.mjs.
exec node "$(dirname "$0")/../../scripts/wrangler-shim.mjs" "$@"
`;

function main() {
  if (!existsSync(BIN)) {
    console.log('node_modules/.bin is missing; skipping the Wrangler shim.');
    return;
  }

  // npm normally creates node_modules/.bin/wrangler as a symlink. On file
  // systems that do not support symlinks the link is missing, so the
  // launcher is created from the package's own entry point instead.
  const packageEntry = join(BACKEND, 'node_modules', 'wrangler', 'bin', 'wrangler.js');
  if (!existsSync(TARGET)) {
    if (!existsSync(packageEntry)) {
      console.log('Wrangler is not installed; skipping the shim.');
      return;
    }
    writeFileSync(TARGET, `#!/bin/sh\nexec node "$(dirname "$0")/../wrangler/bin/wrangler.js" "$@"\n`);
    chmodSync(TARGET, 0o755);
  }

  // Keep the original launcher so the shim can delegate to it.
  if (!existsSync(REAL)) {
    copyFileSync(TARGET, REAL);
    chmodSync(REAL, 0o755);
  }

  writeFileSync(TARGET, LAUNCHER);
  chmodSync(TARGET, 0o755);
  console.log('Installed the Wrangler shim.');
}

main();
