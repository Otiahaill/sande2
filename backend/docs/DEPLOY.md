# SANDE 2 Online — Cloudflare Deployment Guide

## Prerequisites

You need these credentials (already collected):

| Credential | Value |
|------------|-------|
| CLOUDFLARE_API_TOKEN | `<your-cloudflare-api-token>` |
| CLOUDFLARE_ACCOUNT_ID | `<your-cloudflare-account-id>` |
| GOOGLE_CLOUD_PROJECT_ID | `<your-google-cloud-project-id>` |
| GOOGLE_CLIENT_ID (Web) | `<your-google-client-id>` |
| GOOGLE_CLIENT_SECRET | `<your-google-client-secret>` |
| GOOGLE_ANDROID_CLIENT_ID | `<your-google-android-client-id>` |

## Step 1: Install Dependencies (Desktop Machine)

```bash
cd /path/to/sande-2-online/backend
npm install
```

## Step 2: Create Cloudflare Resources

```bash
CLOUDFLARE_API_TOKEN=<token> CLOUDFLARE_ACCOUNT_ID=<account_id> \
  node scripts/cloudflare-setup.mjs --env=dev
```

This creates:
- D1 database: `sande-platform-dev`
- KV namespaces: `sande-session-dev`, `sande-ratelimit-dev`
- R2 bucket: `sande-backups-dev`
- Updates `wrangler.toml` with real resource IDs

## Step 3: Apply D1 Schema Migrations

```bash
CLOUDFLARE_API_TOKEN=<token> CLOUDFLARE_ACCOUNT_ID=<account_id> \
  node scripts/d1-migrate.mjs --env=dev
```

This applies 29 tables across 2 migration files.

## Step 4: Set Worker Secrets

```bash
CLOUDFLARE_API_TOKEN=<token> CLOUDFLARE_ACCOUNT_ID=<account_id> \
  GOOGLE_CLIENT_ID=<web-client-id> \
  GOOGLE_CLIENT_SECRET=<web-client-secret> \
  node scripts/set-secrets.mjs --env=dev --generate
```

This sets encrypted Workers Secrets:
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` (from you)
- `JWT_SECRET`, `ENCRYPTION_KEY`, `ADMIN_API_KEY` (auto-generated, save these!)

## Step 5: Deploy the Worker

From a desktop machine with wrangler:

```bash
cd /path/to/sande-2-online/backend
npx wrangler deploy --env=dev
```

Or using the REST API scripts:

```bash
CLOUDFLARE_API_TOKEN=<token> CLOUDFLARE_ACCOUNT_ID=<account_id> \
  node scripts/deploy-worker.mjs --env=dev
```

**Note:** The deploy script uses esbuild to bundle TypeScript. On Android, esbuild's native binary cannot execute due to shared storage permissions. Deploy from a Linux/macOS/Windows desktop instead.

## Step 6: Store Connection Manifest

```bash
CLOUDFLARE_API_TOKEN=<token> CLOUDFLARE_ACCOUNT_ID=<account_id> \
  GOOGLE_CLOUD_PROJECT_ID=<project-id> GOOGLE_CLIENT_ID=<web-client-id> \
  node scripts/store-config.mjs --env=dev
```

## Step 7: Verify Connection

```bash
CLOUDFLARE_API_TOKEN=<token> CLOUDFLARE_ACCOUNT_ID=<account_id> \
  node scripts/verify-connection.mjs --env=dev
```

## Step 8: Set Google Client ID in Android App

In the Android app, go to **Settings → Connection** and enter:
- **Backend URL:** `https://sande-backend-dev.<your-subdomain>.workers.dev` (from Cloudflare dashboard)
- **Google OAuth Client ID:** `<your-google-client-id>` (Web client)
- **Device code:** Auto-generated on first launch

## One-Shot Command (Desktop Only)

```bash
cd /path/to/sande-2-online/backend
CLOUDFLARE_API_TOKEN=<token> \
  CLOUDFLARE_ACCOUNT_ID=<account_id> \
  GOOGLE_CLIENT_ID=<web-client-id> \
  GOOGLE_CLIENT_SECRET=<web-client-secret> \
  GOOGLE_CLOUD_PROJECT_ID=<project-id> \
  node scripts/connect-all.mjs --env=dev
```

## Why Wrangler CLI Doesn't Work on Android

The `wrangler` CLI depends on `workerd` (Cloudflare's Workers runtime), which has no `android-arm64` build. Attempting to run it produces:

```
Error: Unsupported platform: android arm64 LE
```

The connection scripts in `backend/scripts/` use the Cloudflare REST API instead, but the Worker upload step requires esbuild (which also can't execute its native binary from Android shared storage). Deploy from a desktop machine.

## Files in `backend/scripts/`

| Script | Purpose |
|--------|---------|
| `cloudflare-setup.mjs` | Create D1, KV, R2 resources |
| `d1-migrate.mjs` | Apply D1 schema migrations |
| `set-secrets.mjs` | Set Workers Secrets (encrypted) |
| `deploy-worker.mjs` | Deploy Worker code |
| `store-config.mjs` | Store connection manifest in KV |
| `verify-connection.mjs` | Verify all resources |
| `connect-all.mjs` | Run all steps in order |

## Transpiled Worker Source

The TypeScript source has been transpiled to JavaScript and saved at:
`/storage/emulated/0/Android/data/com.androidharness.app/files/.harness-scratch/sande-backend-dev.js`

This file contains the complete Worker code (4,922 bytes) and can be uploaded via `wrangler deploy` from a desktop machine.
