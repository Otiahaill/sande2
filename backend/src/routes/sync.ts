import { Context } from 'hono';
import { CloudflareBindings, OutboxEntry, SyncException } from '../types';
import { createTenantDB, createPlatformDB } from '../db/tenant';
import { hashToken } from '../middleware/auth';
import { ChangeLogRow, StockMovementRow, SyncCursorRow } from '../db/rows';


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
 * POST /api/sync/push
 * Push outbox entries to server
 */
export async function push(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any } }>) {
  const body = await c.req.json<{ batchId: string; entries: OutboxEntry[] }>();
  const context = c.get('businessContext');
  const tenantDB = createTenantDB(c.env.DB, context);
  const platformDB = createPlatformDB(c.env.DB);
  const now = Date.now();

  // Create sync batch record
  const batchId = body.batchId || crypto.randomUUID();
  await platformDB.createSyncBatch({
    id: batchId,
    business_id: context.businessId,
    device_code: context.deviceCode,
    direction: 'push',
  });

  let successCount = 0;
  let errorCount = 0;
  const errors: string[] = [];

  // Process each entry
  for (const entry of body.entries) {
    try {
      // Verify entry belongs to this business
      if (entry.businessId !== context.businessId) {
        throw new Error('Business ID mismatch');
      }

      // Apply the operation
      await applyOutboxEntry(c.env.DB, context.businessId, entry, context);

      // Mark as synced locally
      await tenantDB.markOutboxSynced([entry.id]);

      successCount++;
    } catch (err) {
      errorCount++;
      const errorMsg = err instanceof Error ? err.message : 'Unknown error';
      errors.push(`${entry.entityType}:${entry.entityId} - ${errorMsg}`);

      // Record attempt
      await tenantDB.recordOutboxAttempt(entry.id, errorMsg);

      // If too many attempts, create sync exception
      if (entry.attemptCount >= 3) {
        await createSyncException(c.env.DB, context.businessId, context.deviceCode, {
          id: crypto.randomUUID(),
          exceptionType: 'VALIDATION',
          entityType: entry.entityType,
          entityId: entry.entityId,
          localVersion: 0,
          serverVersion: 0,
          description: `Failed after ${entry.attemptCount} attempts: ${errorMsg}`,
          localDataJson: entry.payloadJson,
          serverDataJson: null,
        });
      }
    }
  }

  // Update batch status
  await platformDB.updateSyncBatch(batchId, {
    status: errorCount === 0 ? 'completed' : (successCount === 0 ? 'failed' : 'completed'),
    record_count: body.entries.length,
    success_count: successCount,
    error_count: errorCount,
    error_summary: errors.length > 0 ? errors.join('; ') : undefined,
    completed_at: now,
  });

  // Update sync cursor pending count
  const cursor = await tenantDB.getSyncCursor(context.deviceCode);
  if (cursor) {
    await tenantDB.upsertSyncCursor({
      ...cursor,
      pendingPushCount: Math.max(0, cursor.pendingPushCount - successCount),
      lastPushAt: now,
    });
  }

  return c.json({
    success: true,
    data: {
      batchId,
      processed: body.entries.length,
      success: successCount,
      errors: errorCount,
      errorDetails: errors.length > 0 ? errors : undefined,
    }
  });
}

/**
 * Applies a single outbox entry to the database
 */
async function applyOutboxEntry(db: any, businessId: string, entry: OutboxEntry, context: any) {
  const payload = JSON.parse(entry.payloadJson);

  switch (entry.entityType) {
    case 'product':
      await applyProduct(db, businessId, entry.operation, payload, context);
      break;
    case 'sale_session':
      await applySaleSession(db, businessId, entry.operation, payload, context);
      break;
    case 'stock_movement':
      await applyStockMovement(db, businessId, entry.operation, payload, context);
      break;
    case 'membership_mirror':
      await applyMembershipMirror(db, businessId, entry.operation, payload, context);
      break;
    case 'device_registration':
      await applyDeviceRegistration(db, businessId, entry.operation, payload, context);
      break;
    case 'business_profile':
      await applyBusinessProfile(db, businessId, entry.operation, payload, context);
      break;
    case 'counter':
      await applyCounter(db, businessId, entry.operation, payload, context);
      break;
    default:
      throw new Error(`Unknown entity type: ${entry.entityType}`);
  }

  // Write to change_log
  await db.prepare(
    `INSERT INTO change_log (id, business_id, entity_type, entity_id, operation, actor_id, actor_role, device_code, before_json, after_json, metadata_json, server_seq, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, (SELECT COALESCE(MAX(server_seq), 0) + 1 FROM change_log WHERE business_id = ?), ?)`
  ).bind(
    crypto.randomUUID(),
    businessId,
    entry.entityType,
    entry.entityId,
    entry.operation,
    context.accountId,
    context.role,
    context.deviceCode,
    null, // before_json - could be fetched for updates
    entry.payloadJson,
    JSON.stringify({ batchId: entry.batchId }),
    businessId,
    Date.now()
  ).run();
}

async function applyProduct(db: any, businessId: string, operation: string, payload: any, context: any) {
  if (operation === 'create' || operation === 'update') {
    await db.prepare(
      `INSERT INTO products (id, business_id, name, sku, category_id, category, price_cents, unit_of_measure, conversion_ratio, original_price_cents, is_active, archived_at, deleted_at, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, 0, ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         name = excluded.name,
         sku = excluded.sku,
         category_id = excluded.category_id,
         category = excluded.category,
         price_cents = excluded.price_cents,
         unit_of_measure = excluded.unit_of_measure,
         conversion_ratio = excluded.conversion_ratio,
         original_price_cents = excluded.original_price_cents,
         updated_at = excluded.updated_at`
    ).bind(
      payload.id, businessId, payload.name, payload.sku, payload.category_id, payload.category,
      payload.price_cents, payload.unit_of_measure, payload.conversion_ratio,
      payload.original_price_cents, payload.created_at, Date.now()
    ).run();
  } else if (operation === 'delete') {
    await db.prepare(
      `UPDATE products SET deleted_at = ?, updated_at = ? WHERE business_id = ? AND id = ?`
    ).bind(Date.now(), Date.now(), businessId, payload.id).run();
  }
}

async function applySaleSession(db: any, businessId: string, operation: string, payload: any, context: any) {
  if (operation === 'create' || operation === 'update') {
    await db.prepare(
      `INSERT INTO sale_sessions (id, business_id, receipt_number, device_id, device_code, salesperson_id, salesperson_role, subtotal_cents, tax_cents, total_cents, status, created_at, updated_at, synced)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
       ON CONFLICT(id) DO UPDATE SET
         status = excluded.status,
         updated_at = excluded.updated_at,
         synced = 1`
    ).bind(
      payload.id, businessId, payload.receipt_number, payload.device_id, payload.device_code,
      payload.salesperson_id, payload.salesperson_role, payload.subtotal_cents,
      payload.tax_cents, payload.total_cents, payload.status, payload.created_at, Date.now()
    ).run();
  }
}

async function applyStockMovement(db: any, businessId: string, operation: string, payload: any, context: any) {
  if (operation === 'create') {
    await db.prepare(
      `INSERT INTO stock_movements (id, business_id, product_id, movement_type, quantity_delta, unit_price_cents, actor_id, actor_role, device_code, reason, reference_id, reversal_of, metadata_json, created_at, synced, server_seq, running_balance)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, (SELECT COALESCE(MAX(server_seq), 0) + 1 FROM stock_movements WHERE business_id = ?), 0)`
    ).bind(
      payload.id, businessId, payload.product_id, payload.movement_type, payload.quantity_delta,
      payload.unit_price_cents, payload.actor_id, payload.actor_role, payload.device_code,
      payload.reason, payload.reference_id, payload.reversal_of, payload.metadata_json,
      payload.created_at, businessId
    ).run();

    // Update running balance for this and subsequent movements
    await updateRunningBalances(db, businessId, payload.product_id);
  }
}

async function applyMembershipMirror(db: any, businessId: string, operation: string, payload: any, context: any) {
  const id = `${businessId}:${payload.account_id}`;
  if (operation === 'create' || operation === 'update') {
    await db.prepare(
      `INSERT INTO membership_mirrors (id, business_id, account_id, email, role, status, invited_by, invited_at, accepted_at, device_code, last_synced_at, server_version, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         email = excluded.email,
         role = excluded.role,
         status = excluded.status,
         invited_by = excluded.invited_by,
         invited_at = excluded.invited_at,
         accepted_at = excluded.accepted_at,
         device_code = excluded.device_code,
         last_synced_at = excluded.last_synced_at,
         server_version = excluded.server_version,
         updated_at = excluded.updated_at`
    ).bind(
      id, businessId, payload.account_id, payload.email, payload.role, payload.status,
      payload.invited_by, payload.invited_at, payload.accepted_at, payload.device_code,
      payload.last_synced_at, payload.server_version, payload.created_at, Date.now()
    ).run();
  } else if (operation === 'delete') {
    await db.prepare(
      `DELETE FROM membership_mirrors WHERE id = ?`
    ).bind(id).run();
  }
}

async function applyDeviceRegistration(db: any, businessId: string, operation: string, payload: any, context: any) {
  if (operation === 'create' || operation === 'update') {
    await db.prepare(
      `INSERT INTO device_registrations (id, business_id, account_id, device_code, device_name, device_type, status, approved_by, approved_at, revoked_at, revoked_by, last_seen_at, last_sync_at, push_token, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         device_name = excluded.device_name,
         device_type = excluded.device_type,
         status = excluded.status,
         approved_by = excluded.approved_by,
         approved_at = excluded.approved_at,
         revoked_at = excluded.revoked_at,
         revoked_by = excluded.revoked_by,
         last_seen_at = excluded.last_seen_at,
         last_sync_at = excluded.last_sync_at,
         push_token = excluded.push_token,
         updated_at = excluded.updated_at`
    ).bind(
      payload.id, businessId, payload.account_id, payload.device_code, payload.device_name,
      payload.device_type, payload.status, payload.approved_by, payload.approved_at,
      payload.revoked_at, payload.revoked_by, payload.last_seen_at, payload.last_sync_at,
      payload.push_token, payload.created_at, Date.now()
    ).run();
  } else if (operation === 'delete') {
    await db.prepare(
      `DELETE FROM device_registrations WHERE id = ?`
    ).bind(payload.id).run();
  }
}

async function applyBusinessProfile(db: any, businessId: string, operation: string, payload: any, context: any) {
  if (operation === 'create' || operation === 'update') {
    await db.prepare(
      `INSERT INTO business_profiles (id, business_id, name, category, phone, country_code, currency_code, timezone, address, logo_r2_key, negative_stock_policy, receipt_footer, created_at, updated_at, deleted_at, schema_version)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
       ON CONFLICT(business_id) DO UPDATE SET
         name = excluded.name,
         category = excluded.category,
         phone = excluded.phone,
         country_code = excluded.country_code,
         currency_code = excluded.currency_code,
         timezone = excluded.timezone,
         address = excluded.address,
         logo_r2_key = excluded.logo_r2_key,
         negative_stock_policy = excluded.negative_stock_policy,
         receipt_footer = excluded.receipt_footer,
         updated_at = excluded.updated_at,
         schema_version = excluded.schema_version`
    ).bind(
      businessId, businessId, payload.name, payload.category, payload.phone,
      payload.country_code, payload.currency_code, payload.timezone, payload.address,
      payload.logo_r2_key, payload.negative_stock_policy, payload.receipt_footer,
      payload.created_at, Date.now(), payload.schema_version
    ).run();
  }
}

async function applyCounter(db: any, businessId: string, operation: string, payload: any, context: any) {
  const id = `${businessId}:${payload.device_code}:${payload.counter_type}`;
  if (operation === 'create' || operation === 'update') {
    await db.prepare(
      `INSERT INTO counters (id, business_id, device_code, counter_type, current_value, updated_at)
       VALUES (?, ?, ?, ?, ?, ?)
       ON CONFLICT(business_id, device_code, counter_type) DO UPDATE SET
         current_value = excluded.current_value,
         updated_at = excluded.updated_at`
    ).bind(id, businessId, payload.device_code, payload.counter_type, payload.current_value, Date.now()).run();
  }
}

async function updateRunningBalances(db: any, businessId: string, productId: string) {
  // Recalculate running balances for all movements of this product
  const movements = await rows<StockMovementRow>( db.prepare(
    `SELECT id FROM stock_movements WHERE business_id = ? AND product_id = ? ORDER BY created_at ASC`
  ).bind(businessId, productId).all<StockMovementRow>());

  let runningBalance = 0;
  for (const movement of movements) {
    const m = await db.prepare(
      `SELECT quantity_delta FROM stock_movements WHERE id = ?`
    ).bind(movement.id).first<{ quantity_delta: number }>();
    if (m) {
      runningBalance += m.quantity_delta;
      await db.prepare(
        `UPDATE stock_movements SET running_balance = ? WHERE id = ?`
      ).bind(runningBalance, movement.id).run();
    }
  }
}

/**
 * GET /api/sync/pull
 * Pull changes from server since cursor
 */
export async function pull(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any } }>) {
  const cursor = parseInt(c.req.query('cursor') || '0', 10);
  const limit = Math.min(parseInt(c.req.query('limit') || '100', 10), 500);
  const context = c.get('businessContext');
  const tenantDB = createTenantDB(c.env.DB, context);
  const now = Date.now();

  // Get changes from change_log since cursor
  const changes = await rows<ChangeLogRow>( c.env.DB.prepare(
    `SELECT * FROM change_log WHERE business_id = ? AND server_seq > ? ORDER BY server_seq ASC LIMIT ?`
  ).bind(context.businessId, cursor, limit).all<ChangeLogRow>());

  // Update cursor
  const newCursor = changes.length > 0
    ? Math.max(...changes.map((c: any) => c.server_seq))
    : cursor;

  await tenantDB.upsertSyncCursor({
    id: `${context.businessId}:${context.deviceCode}`,
    deviceCode: context.deviceCode,
    cursor: newCursor,
    lastPullAt: now,
    lastPushAt: cursor.pendingPushCount > 0 ? Date.now() : new Date(cursor.lastPushAt).getTime(), // preserve last push
    pendingPushCount: cursor.pendingPushCount,
  });

  return c.json({
    success: true,
    data: {
      cursor: newCursor,
      changes: changes,
      hasMore: changes.length === limit,
    }
  });
}

/**
 * GET /api/sync/status
 * Get sync status for current device
 */
export async function status(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any } }>) {
  const context = c.get('businessContext');
  const tenantDB = createTenantDB(c.env.DB, context);
  const platformDB = createPlatformDB(c.env.DB);

  const cursor = await tenantDB.getSyncCursor(context.deviceCode);
  const unsyncedCount = (await tenantDB.getUnsyncedOutboxEntries(1000)).results.length;

  // Get other devices' last sync times (owner only)
  let otherDevices: any[] = [];
  if (context.role === 'owner') {
    const devices = await platformDB.getDevicesByBusiness(context.businessId);
    otherDevices = await Promise.all(devices.map(async (d) => {
      if (d.device_code === context.deviceCode) return null;
      const cursor = await c.env.DB.prepare(
        `SELECT last_pull_at, last_push_at, pending_push_count FROM sync_cursors WHERE id = ?`
      ).bind(`${context.businessId}:${d.device_code}`).first<SyncCursorRow>();
      return {
        device_code: d.device_code,
        device_name: d.device_name,
        status: d.status,
        last_pull_at: cursor?.last_pull_at,
        last_push_at: cursor?.last_push_at,
        pending_push_count: cursor?.pending_push_count ?? 0,
      };
    }).then(r => r.filter(Boolean)));
  }

  return c.json({
    success: true,
    data: {
      device_code: context.deviceCode,
      cursor: cursor?.cursor ?? 0,
      last_pull_at: cursor?.lastPullAt ?? 0,
      last_push_at: cursor?.lastPushAt ?? 0,
      pending_push_count: cursor?.pendingPushCount ?? unsyncedCount,
      unsynced_count: unsyncedCount,
      other_devices: otherDevices,
      lease_expires_at: context.role === 'owner' ? Date.now() + 14 * 24 * 60 * 60 * 1000 : Date.now() + 14 * 24 * 60 * 60 * 1000,
    }
  });
}

/**
 * GET /api/sync/exceptions
 * Get sync exceptions for current business
 */
export async function exceptions(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any } }>) {
  const context = c.get('businessContext');
  const tenantDB = createTenantDB(c.env.DB, context);

  const status = c.req.query('status') || 'open';
  const exceptionType = c.req.query('type');
  const limit = Math.min(parseInt(c.req.query('limit') || '50', 10), 200);

  const exc = await tenantDB.getSyncExceptions({
    status,
    exception_type: exceptionType,
    limit,
  });

  return c.json({
    success: true,
    data: exc.results
  });
}

/**
 * PUT /api/sync/exceptions/:exceptionId/resolve
 * Resolve a sync exception
 */
export async function resolveException(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any } }>) {
  const exceptionId = c.req.param('exceptionId');
  if (!exceptionId) {
    return c.json({ success: false, error: { code: '400', message: 'exceptionId is required' } }, 400);
  }
  const body = await c.req.json<{ resolution: 'local_wins' | 'server_wins' | 'manual' | 'pending' }>();
  const context = c.get('businessContext');
  const tenantDB = createTenantDB(c.env.DB, context);

  await tenantDB.resolveSyncException(exceptionId, body.resolution, context.accountId);

  return c.json({
    success: true,
    data: { message: 'Exception resolved' }
  });
}

/**
 * Creates a sync exception
 */
async function createSyncException(db: any, businessId: string, deviceCode: string, exception: Omit<SyncException, 'businessId' | 'deviceCode' | 'createdAt'>) {
  const now = Date.now();
  await db.prepare(
    `INSERT INTO sync_exceptions (id, business_id, device_code, exception_type, entity_type, entity_id, local_version, server_version, description, local_data_json, server_data_json, resolution, status, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'open', ?)`
  ).bind(
    exception.id, businessId, deviceCode, exception.exceptionType, exception.entityType,
    exception.entityId, exception.localVersion, exception.serverVersion,
    exception.description, exception.localDataJson, exception.serverDataJson,
    exception.resolution, now
  ).run();
}

export const syncRoutes = {
  push,
  pull,
  status,
  exceptions,
  resolveException,
};
