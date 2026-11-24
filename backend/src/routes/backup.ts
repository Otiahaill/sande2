import { Context } from 'hono';
import { CloudflareBindings } from '../types';
import { createTenantDB, createPlatformDB } from '../db/tenant';

/**
 * Unwraps the rows from a D1 result.
 *
 * `D1PreparedStatement.all()` resolves to a `D1Result`; the rows themselves
 * are in `.results`. Code that wants the rows uses this helper so the
 * distinction is explicit.
 */
export async function rows<T = Record<string, unknown>>(
  result: Promise<D1Result<T>>
): Promise<T[]> {
  return (await result).results;
}


/**
 * POST /api/backup/create
 * Create a backup of the business data (owner only)
 */
export async function create(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any } }>) {
  const context = c.get('businessContext');
  const tenantDB = createTenantDB(c.env.DB, context);
  const platformDB = createPlatformDB(c.env.DB);
  const now = Date.now();

  // Export all business tables as NDJSON
  const tables = [
    'products',
    'product_categories',
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
    'business_profiles',
  ];

  const backupData: Record<string, any[]> = {};
  let totalRows = 0;
  const rowCounts: Record<string, number> = {};

  for (const table of tables) {
    const result = await rows( c.env.DB.prepare(
      `SELECT * FROM ${table} WHERE business_id = ?`
    ).bind(context.businessId).all());

    backupData[table] = result.results;
    rowCounts[table] = result.results.length;
    totalRows += result.results.length;
  }

  // Create NDJSON content
  let ndjsonContent = '';
  for (const [table, rows] of Object.entries(backupData)) {
    for (const row of rows) {
      ndjsonContent += JSON.stringify({ table, ...row }) + '\n';
    }
  }

  // Calculate SHA-256 hash
  const encoder = new TextEncoder();
  const data = encoder.encode(ndjsonContent);
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  const contentHash = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');

  // Upload to R2
  const dateStr = new Date().toISOString().split('T')[0];
  const r2Key = `backups/${context.businessId}/${dateStr}/backup_${now}.ndjson`;

  await c.env.R2_BACKUPS.put(r2Key, ndjsonContent, {
    httpMetadata: {
      contentType: 'application/x-ndjson',
    },
    customMetadata: {
      businessId: context.businessId,
      createdAt: now.toString(),
      rowCounts: JSON.stringify(rowCounts),
      schemaVersion: '6', // Match app schema version
      contentHash,
    },
  });

  // Get size
  const object = await c.env.R2_BACKUPS.head(r2Key);
  const sizeBytes = object?.size ?? ndjsonContent.length;

  // Record backup metadata
  const backupId = crypto.randomUUID();
  await c.env.DB.prepare(
    `INSERT INTO backups (id, business_id, r2_key, size_bytes, row_counts_json, schema_version, status, created_at)
     VALUES (?, ?, ?, ?, ?, ?, 'completed', ?)`
  ).bind(backupId, context.businessId, r2Key, sizeBytes, JSON.stringify(rowCounts), 6, now).run();

  // Enforce retention: keep 30 daily + 12 monthly
  await enforceRetentionPolicy(c.env.R2_BACKUPS, c.env.DB, context.businessId);

  return c.json({
    success: true,
    data: {
      backup_id: backupId,
      r2_key: r2Key,
      size_bytes: sizeBytes,
      row_counts: rowCounts,
      total_rows: totalRows,
      content_hash: contentHash,
      created_at: now,
    }
  });
}

/**
 * Enforces backup retention policy
 */
async function enforceRetentionPolicy(r2: R2Bucket, db: any, businessId: string) {
  const backups = await rows( db.prepare(
    `SELECT id, r2_key, created_at FROM backups WHERE business_id = ? ORDER BY created_at DESC`
  ).bind(businessId).all());

  const now = Date.now();
  const dailyCutoff = now - 30 * 24 * 60 * 60 * 1000; // 30 days
  const monthlyCutoff = now - 12 * 30 * 24 * 60 * 60 * 1000; // 12 months

  // Keep daily backups for 30 days
  // Keep monthly backups (1st of month) for 12 months
  // Delete others

  const toDelete: string[] = [];

  for (const backup of backups.results) {
    const createdAt = backup.created_at;
    const date = new Date(createdAt);
    const isFirstOfMonth = date.getDate() === 1;

    if (createdAt < dailyCutoff && !isFirstOfMonth) {
      toDelete.push(backup.r2_key);
    } else if (createdAt < monthlyCutoff && isFirstOfMonth) {
      toDelete.push(backup.r2_key);
    }
  }

  // Delete from R2 and DB
  for (const key of toDelete) {
    try {
      await r2.delete(key);
    } catch (err) {
      console.error(`Failed to delete R2 object ${key}:`, err);
    }
  }

  if (toDelete.length > 0) {
    const placeholders = toDelete.map(() => '?').join(', ');
    await db.prepare(
      `DELETE FROM backups WHERE business_id = ? AND r2_key IN (${placeholders})`
    ).bind(businessId, ...toDelete).run();
  }
}

/**
 * GET /api/backup/list
 * List backups for the business (owner only)
 */
export async function list(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any } }>) {
  const context = c.get('businessContext');

  const backups = await rows( c.env.DB.prepare(
    `SELECT id, r2_key, size_bytes, row_counts_json, schema_version, status, error, created_at
     FROM backups WHERE business_id = ? ORDER BY created_at DESC LIMIT 100`
  ).bind(context.businessId).all());

  return c.json({
    success: true,
    data: backups.results.map((b: any) => ({
      ...b,
      row_counts: JSON.parse(b.row_counts_json),
    }))
  });
}

/**
 * POST /api/backup/restore
 * Restore from a backup (owner only)
 */
export async function restore(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any } }>) {
  const body = await c.req.json<{ backup_id: string }>();
  const context = c.get('businessContext');
  const tenantDB = createTenantDB(c.env.DB, context);
  const now = Date.now();

  // Get backup metadata
  const backup = await c.env.DB.prepare(
    `SELECT * FROM backups WHERE id = ? AND business_id = ?`
  ).bind(body.backup_id, context.businessId).first();

  if (!backup) {
    return c.json({
      success: false,
      error: { code: '404', message: 'Backup not found' }
    }, 404);
  }

  // Download from R2
  const object = await c.env.R2_BACKUPS.get(backup.r2_key);
  if (!object) {
    return c.json({
      success: false,
      error: { code: '404', message: 'Backup file not found in R2' }
    }, 404);
  }

  const text = await object.text();
  const lines = text.trim().split('\n').filter(Boolean);

  // Parse NDJSON
  const tableData: Record<string, any[]> = {};
  for (const line of lines) {
    try {
      const record = JSON.parse(line);
      const { table, ...data } = record;
      if (!tableData[table]) tableData[table] = [];
      tableData[table].push(data);
    } catch (err) {
      console.error('Failed to parse backup line:', err);
    }
  }

  // Restore in dependency order
  const restoreOrder = [
    'products',
    'product_categories',
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
    'business_profiles',
  ];

  let restoredRows = 0;
  const errors: string[] = [];

  for (const table of restoreOrder) {
    const rows = tableData[table] || [];
    if (rows.length === 0) continue;

    for (const row of rows) {
      try {
        // Build upsert query
        const columns = Object.keys(row).filter(k => k !== 'business_id'); // business_id is implicit
        const placeholders = columns.map(() => '?').join(', ');
        const updateClause = columns.map(c => `${c} = excluded.${c}`).join(', ');

        await c.env.DB.prepare(
          `INSERT INTO ${table} (business_id, ${columns.join(', ')})
           VALUES (?, ${placeholders})
           ON CONFLICT(business_id, id) DO UPDATE SET ${updateClause}`
        ).bind(context.businessId, ...columns.map(c => row[c])).run();

        restoredRows++;
      } catch (err) {
        const msg = err instanceof Error ? err.message : 'Unknown error';
        errors.push(`${table}:${row.id} - ${msg}`);
      }
    }
  }

  // Record restore as backup
  const restoreId = crypto.randomUUID();
  await c.env.DB.prepare(
    `INSERT INTO backups (id, business_id, r2_key, size_bytes, row_counts_json, schema_version, status, created_at)
     VALUES (?, ?, 'restore', 0, '{}', ?, 'completed', ?)`
  ).bind(restoreId, context.businessId, backup.schema_version, now).run();

  return c.json({
    success: true,
    data: {
      restored_rows: restoredRows,
      errors: errors.length > 0 ? errors : undefined,
      message: errors.length > 0 ? `Restored with ${errors.length} errors` : 'Restore completed successfully'
    }
  });
}

/**
 * GET /api/backup/status
 * Get backup status
 */
export async function status(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any } }>) {
  const context = c.get('businessContext');

  const latestBackup = await c.env.DB.prepare(
    `SELECT * FROM backups WHERE business_id = ? ORDER BY created_at DESC LIMIT 1`
  ).bind(context.businessId).first();

  const pendingCount = await c.env.DB.prepare(
    `SELECT COUNT(*) as count FROM backups WHERE business_id = ? AND status = 'pending'`
  ).bind(context.businessId).first<{ count: number }>();

  return c.json({
    success: true,
    data: {
      latest_backup: latestBackup ? {
        id: latestBackup.id,
        r2_key: latestBackup.r2_key,
        size_bytes: latestBackup.size_bytes,
        row_counts: JSON.parse(latestBackup.row_counts_json),
        created_at: latestBackup.created_at,
        status: latestBackup.status,
      } : null,
      pending_backups: pendingCount?.count ?? 0,
    }
  });
}

export const backupRoutes = {
  create,
  list,
  restore,
  status,
};
