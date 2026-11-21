import { Context, ScheduledEvent } from '@cloudflare/workers-types';

/**
 * Scheduled handler for the nightly backup cron.
 *
 * Creates a logical export (NDJSON per table) of every active business and
 * uploads it to R2 under backups/{businessId}/{date}/backup_{timestamp}.ndjson.
 *
 * Retention: 30 daily backups + 12 monthly backups per business.
 */
export async function handleScheduled(
  event: ScheduledEvent,
  env: CloudflareBindings,
  ctx: ExecutionContext
): Promise<void> {
  const cron = event.cron;
  console.log(`[scheduled] cron "${cron}" fired at ${new Date(event.scheduledTime).toISOString()}`);

  switch (cron) {
    case '0 3 * * *':
      ctx.waitUntil(runNightlyBackups(env));
      break;
    case '0 4 * * *':
      ctx.waitUntil(cleanupOldExceptions(env));
      break;
    case '0 */6 * * *':
      ctx.waitUntil(checkLeaseExpiry(env));
      break;
    default:
      console.warn(`[scheduled] no handler for cron "${cron}"`);
  }
}

/** Creates a backup for every active business. */
async function runNightlyBackups(env: CloudflareBindings): Promise<void> {
  const now = Date.now();
  const businesses = await env.DB.prepare(
    `SELECT id, name FROM businesses WHERE status = 'active' AND deleted_at = 0`
  ).all<{ id: string; name: string }>();

  console.log(`[backup] creating backups for ${businesses.results.length} business(es)`);

  for (const business of businesses.results) {
    try {
      const result = await createBusinessBackup(env, business.id);
      console.log(`[backup] ${business.id}: ${result.rowCount} rows → ${result.r2Key}`);
    } catch (err) {
      console.error(`[backup] ${business.id} FAILED:`, err);
      await recordBackupFailure(env, business.id, err instanceof Error ? err.message : 'Unknown error');
    }
  }
}

/** Tables included in a logical export, in dependency order. */
const BACKUP_TABLES = [
  'business_profiles',
  'product_categories',
  'products',
  'product_price_history',
  'stock_movements',
  'sale_sessions',
  'sale_items',
  'receipts',
  'counters',
  'audit_events',
  'membership_mirrors',
  'device_registrations',
  'outbox_entries',
  'sync_cursors',
  'sync_exceptions',
];

/** Creates and uploads a backup for one business. */
async function createBusinessBackup(
  env: CloudflareBindings,
  businessId: string
): Promise<{ r2Key: string; rowCount: number; sizeBytes: number }> {
  const rowCounts: Record<string, number> = {};
  let totalRows = 0;
  let ndjson = '';

  for (const table of BACKUP_TABLES) {
    const rows = await env.DB.prepare(
      `SELECT * FROM ${table} WHERE business_id = ?`
    ).bind(businessId).all();

    rowCounts[table] = rows.results.length;
    totalRows += rows.results.length;

    for (const row of rows.results) {
      ndjson += JSON.stringify({ table, ...row }) + '\n';
    }
  }

  // SHA-256 of the payload
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(ndjson));
  const contentHash = Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');

  const now = Date.now();
  const dateStr = new Date(now).toISOString().split('T')[0];
  const r2Key = `backups/${businessId}/${dateStr}/backup_${now}.ndjson`;

  await env.R2_BACKUPS.put(r2Key, ndjson, {
    httpMetadata: { contentType: 'application/x-ndjson' },
    customMetadata: {
      businessId,
      createdAt: now.toString(),
      rowCounts: JSON.stringify(rowCounts),
      schemaVersion: '6',
      contentHash,
    },
  });

  const object = await env.R2_BACKUPS.head(r2Key);
  const sizeBytes = object?.size ?? ndjson.length;

  await env.DB.prepare(
    `INSERT INTO backups (id, business_id, r2_key, size_bytes, row_counts_json, schema_version, status, created_at)
     VALUES (?, ?, ?, ?, ?, ?, 'completed', ?)`
  ).bind(
    crypto.randomUUID(),
    businessId,
    r2Key,
    sizeBytes,
    JSON.stringify(rowCounts),
    6,
    now
  ).run();

  await enforceRetention(env, businessId);

  return { r2Key, rowCount: totalRows, sizeBytes };
}

/** Keeps 30 daily + 12 monthly backups; deletes the rest. */
async function enforceRetention(env: CloudflareBindings, businessId: string): Promise<void> {
  const backups = await env.DB.prepare(
    `SELECT id, r2_key, created_at FROM backups WHERE business_id = ? ORDER BY created_at DESC`
  ).bind(businessId).all<{ id: string; r2_key: string; created_at: number }>();

  const now = Date.now();
  const dailyCutoff = now - 30 * 24 * 60 * 60 * 1000;
  const monthlyCutoff = now - 12 * 30 * 24 * 60 * 60 * 1000;

  const toDelete: string[] = [];

  for (const backup of backups.results) {
    const date = new Date(backup.created_at);
    const isFirstOfMonth = date.getUTCDate() === 1;

    if (backup.created_at < dailyCutoff && !isFirstOfMonth) {
      toDelete.push(backup.r2_key);
    } else if (backup.created_at < monthlyCutoff && isFirstOfMonth) {
      toDelete.push(backup.r2_key);
    }
  }

  for (const key of toDelete) {
    try {
      await env.R2_BACKUPS.delete(key);
    } catch (err) {
      console.error(`[retention] failed to delete ${key}:`, err);
    }
  }

  if (toDelete.length > 0) {
    const placeholders = toDelete.map(() => '?').join(', ');
    await env.DB.prepare(
      `DELETE FROM backups WHERE business_id = ? AND r2_key IN (${placeholders})`
    ).bind(businessId, ...toDelete).run();
    console.log(`[retention] deleted ${toDelete.length} old backup(s) for ${businessId}`);
  }
}

/** Records a failed backup attempt. */
async function recordBackupFailure(
  env: CloudflareBindings,
  businessId: string,
  error: string
): Promise<void> {
  await env.DB.prepare(
    `INSERT INTO backups (id, business_id, r2_key, size_bytes, row_counts_json, schema_version, status, error, created_at)
     VALUES (?, ?, '', 0, '{}', 6, 'failed', ?, ?)`
  ).bind(crypto.randomUUID(), businessId, error, Date.now()).run();
}

/** Marks resolved sync exceptions older than 90 days as ignored. */
async function cleanupOldExceptions(env: CloudflareBindings): Promise<void> {
  const cutoff = Date.now() - 90 * 24 * 60 * 60 * 1000;
  const result = await env.DB.prepare(
    `UPDATE sync_exceptions SET status = 'ignored' WHERE status = 'resolved' AND resolved_at < ?`
  ).bind(cutoff).run();

  console.log(`[cleanup] archived ${result.meta?.changes ?? 0} old sync exception(s)`);
}

/** Flags businesses whose offline lease has expired. */
async function checkLeaseExpiry(env: CloudflareBindings): Promise<void> {
  const cutoff = Date.now();

  // Find memberships whose last sync is older than the lease
  const stale = await env.DB.prepare(
    `SELECT m.id, m.business_id, m.account_id, m.last_synced_at
     FROM memberships m
     WHERE m.status = 'active'
       AND (m.last_synced_at IS NULL OR m.last_synced_at < ?)`
  ).bind(cutoff - 14 * 24 * 60 * 60 * 1000).all<{
    id: string;
    business_id: string;
    account_id: string;
    last_synced_at: number | null;
  }>();

  console.log(`[lease] ${stale.results.length} membership/memberships past the 14-day lease`);

  // Notify (placeholder — a real implementation would send email/push)
  for (const membership of stale.results) {
    console.log(
      `[lease] business ${membership.business_id} account ${membership.account_id} needs to reconnect`
    );
  }
}
