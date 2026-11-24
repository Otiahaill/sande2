import { Context } from 'hono';
import { CloudflareBindings } from '../types';
import { createPlatformDB } from '../db/tenant';
import { AccountRow, BackupRow, DeviceRow, SyncBatchRow, SyncCursorRow, SyncExceptionRow } from './db/rows';


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
 * GET /admin/businesses
 * List all businesses (with pagination)
 */
export async function listBusinesses(c: Context<{ Bindings: CloudflareBindings }>) {
  const platformDB = createPlatformDB(c.env.DB);
  const status = c.req.query('status');
  const limit = Math.min(parseInt(c.req.query('limit') || '50', 10), 200);
  const offset = parseInt(c.req.query('offset') || '0', 10);

  const businesses = await platformDB.listBusinesses(status);

  // Apply pagination manually (D1 doesn't support OFFSET in all cases)
  const paginated = businesses.slice(offset, offset + limit);

  return c.json({
    success: true,
    data: paginated,
    pagination: {
      limit,
      offset,
      total: businesses.length,
      hasMore: offset + limit < businesses.length,
    }
  });
}

/**
 * GET /admin/businesses/:businessId
 * Get business details
 */
export async function getBusiness(c: Context<{ Bindings: CloudflareBindings }>) {
  const businessId = c.req.param('businessId');
  const platformDB = createPlatformDB(c.env.DB);

  const business = await platformDB.getBusiness(businessId);
  if (!business) {
    return c.json({
      success: false,
      error: { code: '404', message: 'Business not found' }
    }, 404);
  }

  // Get memberships
  const memberships = await platformDB.getMembershipsByBusiness(businessId);
  const enrichedMemberships = await Promise.all(memberships.map(async (m) => {
    const account = await c.env.DB.prepare('SELECT id, email, display_name FROM accounts WHERE id = ?').bind(m.account_id).first<AccountRow>();
    const device = m.device_code ? await c.env.DB.prepare('SELECT * FROM devices WHERE device_code = ?').bind(m.device_code).first<DeviceRow>() : null;
    return {
      ...m,
      account_email: account?.email,
      account_display_name: account?.display_name,
      device_name: device?.device_name,
      device_status: device?.status,
    };
  }));

  // Get devices
  const devices = await rows<DeviceRow>( c.env.DB.prepare('SELECT * FROM devices WHERE business_id = ?').bind(businessId).all<DeviceRow>());

  // Get sync health
  const syncBatches = await rows<SyncBatchRow>( c.env.DB.prepare(
    `SELECT * FROM sync_batches WHERE business_id = ? ORDER BY started_at DESC LIMIT 10`
  ).bind(businessId).all<SyncBatchRow>());

  // Get open exceptions
  const exceptions = await rows<SyncExceptionRow>( c.env.DB.prepare(
    `SELECT * FROM sync_exceptions WHERE business_id = ? AND status = 'open' ORDER BY created_at DESC LIMIT 20`
  ).bind(businessId).all<SyncExceptionRow>());

  // Get backup status
  const backups = await rows<BackupRow>( c.env.DB.prepare(
    `SELECT * FROM backups WHERE business_id = ? ORDER BY created_at DESC LIMIT 5`
  ).bind(businessId).all<BackupRow>());

  return c.json({
    success: true,
    data: {
      business,
      memberships: enrichedMemberships,
      devices: devices,
      recent_sync_batches: syncBatches,
      open_exceptions: exceptions,
      recent_backups: backups,
    }
  });
}

/**
 * PUT /admin/businesses/:businessId/status
 * Update business status (activate/suspend)
 */
export async function updateBusinessStatus(c: Context<{ Bindings: CloudflareBindings }>) {
  const businessId = c.req.param('businessId');
  const body = await c.req.json<{ status: 'active' | 'suspended' | 'deleted'; reason?: string }>();
  const platformDB = createPlatformDB(c.env.DB);

  const business = await platformDB.getBusiness(businessId);
  if (!business) {
    return c.json({
      success: false,
      error: { code: '404', message: 'Business not found' }
    }, 404);
  }

  const validTransitions: Record<string, string[]> = {
    active: ['suspended', 'deleted'],
    suspended: ['active', 'deleted'],
    deleted: [],
  };

  if (!validTransitions[business.status]?.includes(body.status)) {
    return c.json({
      success: false,
      error: { code: '400', message: `Cannot transition from ${business.status} to ${body.status}` }
    }, 400);
  }

  await platformDB.updateBusinessStatus(businessId, body.status);

  // Log admin action
  await c.env.DB.prepare(
    `INSERT INTO change_log (id, business_id, entity_type, entity_id, operation, actor_id, actor_role, device_code, metadata_json, server_seq, created_at)
     VALUES (?, ?, 'business', ?, 'admin_status_change', 'platform_admin', 'admin', 'admin', ?, (SELECT COALESCE(MAX(server_seq), 0) + 1 FROM change_log WHERE business_id = ?), ?)`
  ).bind(
    crypto.randomUUID(),
    businessId,
    businessId,
    JSON.stringify({ new_status: body.status, reason: body.reason }),
    businessId,
    Date.now()
  ).run();

  return c.json({
    success: true,
    data: { message: `Business ${body.status}` }
  });
}

/**
 * GET /admin/businesses/:businessId/devices
 * List all devices for a business
 */
export async function listBusinessDevices(c: Context<{ Bindings: CloudflareBindings }>) {
  const businessId = c.req.param('businessId');
  const platformDB = createPlatformDB(c.env.DB);

  const devices = await platformDB.getDevicesByBusiness(businessId);
  const enriched = await Promise.all(devices.map(async (d) => {
    const account = await c.env.DB.prepare('SELECT id, email, display_name FROM accounts WHERE id = ?').bind(d.account_id).first<AccountRow>();
    const membership = await platformDB.getMembership(businessId, d.account_id);
    return {
      ...d,
      account_email: account?.email,
      account_display_name: account?.display_name,
      role: membership?.role,
    };
  }));

  return c.json({
    success: true,
    data: enriched
  });
}

/**
 * PUT /admin/businesses/:businessId/devices/:deviceCode
 * Admin device action (revoke, force-approve)
 */
export async function adminDeviceAction(c: Context<{ Bindings: CloudflareBindings }>) {
  const businessId = c.req.param('businessId');
  const deviceCode = c.req.param('deviceCode');
  const body = await c.req.json<{ action: 'revoke' | 'force_approve' | 'replace'; reason?: string }>();

  const platformDB = createPlatformDB(c.env.DB);
  const device = await platformDB.getDevice(deviceCode);
  if (!device || device.business_id !== businessId) {
    return c.json({
      success: false,
      error: { code: '404', message: 'Device not found' }
    }, 404);
  }

  const now = Date.now();

  if (body.action === 'revoke') {
    await platformDB.updateDeviceStatus(deviceCode, 'revoked');
    // Revoke refresh tokens
    await c.env.DB.prepare(
      `UPDATE refresh_tokens SET revoked_at = ? WHERE device_id = ?`
    ).bind(now, device.id).run();
  } else if (body.action === 'force_approve') {
    await platformDB.updateDeviceStatus(deviceCode, 'active');
  } else if (body.action === 'replace') {
    // Mark current as replaced, new device will be registered separately
    await platformDB.updateDeviceStatus(deviceCode, 'replaced');
  }

  // Log admin action
  await c.env.DB.prepare(
    `INSERT INTO change_log (id, business_id, entity_type, entity_id, operation, actor_id, actor_role, device_code, metadata_json, server_seq, created_at)
     VALUES (?, ?, 'device', ?, 'admin_${body.action}', 'platform_admin', 'admin', 'admin', ?, (SELECT COALESCE(MAX(server_seq), 0) + 1 FROM change_log WHERE business_id = ?), ?)`
  ).bind(
    crypto.randomUUID(),
    businessId,
    deviceCode,
    JSON.stringify({ reason: body.reason }),
    businessId,
    Date.now()
  ).run();

  return c.json({
    success: true,
    data: { message: `Device ${body.action}d` }
  });
}

/**
 * GET /admin/businesses/:businessId/sync
 * Get sync health for a business
 */
export async function getSyncHealth(c: Context<{ Bindings: CloudflareBindings }>) {
  const businessId = c.req.param('businessId');
  const now = Date.now();
  const dayAgo = now - 24 * 60 * 60 * 1000;

  // Recent sync batches
  const batches = await rows<SyncBatchRow>( c.env.DB.prepare(
    `SELECT * FROM sync_batches WHERE business_id = ? AND started_at > ? ORDER BY started_at DESC`
  ).bind(businessId, dayAgo).all<SyncBatchRow>());

  // Sync exceptions
  const exceptions = await rows<SyncExceptionRow>( c.env.DB.prepare(
    `SELECT exception_type, COUNT(*) as count FROM sync_exceptions WHERE business_id = ? AND status = 'open' GROUP BY exception_type`
  ).bind(businessId).all<SyncExceptionRow>());

  // Devices with pending push
  const cursors = await rows<SyncCursorRow>( c.env.DB.prepare(
    `SELECT device_code, cursor, last_pull_at, last_push_at, pending_push_count FROM sync_cursors WHERE business_id = ?`
  ).bind(businessId).all<SyncCursorRow>());

  // Calculate health metrics
  const totalBatches = batches.length;
  const failedBatches = batches.filter((b: any) => b.status === 'failed').length;
  const successRate = totalBatches > 0 ? ((totalBatches - failedBatches) / totalBatches * 100).toFixed(1) : '100';

  return c.json({
    success: true,
    data: {
      business_id: businessId,
      sync_health: {
        total_batches_24h: totalBatches,
        failed_batches_24h: failedBatches,
        success_rate: `${successRate}%`,
        open_exceptions: exceptions,
        device_cursors: cursors,
      }
    }
  });
}

/**
 * GET /admin/businesses/:businessId/exceptions
 * Get all sync exceptions for a business
 */
export async function getBusinessExceptions(c: Context<{ Bindings: CloudflareBindings }>) {
  const businessId = c.req.param('businessId');
  const status = c.req.query('status') || 'open';
  const limit = Math.min(parseInt(c.req.query('limit') || '100', 10), 500);

  const exceptions = await rows<SyncExceptionRow>( c.env.DB.prepare(
    `SELECT * FROM sync_exceptions WHERE business_id = ? AND status = ? ORDER BY created_at DESC LIMIT ?`
  ).bind(businessId, status, limit).all<SyncExceptionRow>());

  return c.json({
    success: true,
    data: exceptions
  });
}

/**
 * GET /admin/platform/stats
 * Platform-wide statistics
 */
export async function getPlatformStats(c: Context<{ Bindings: CloudflareBindings }>) {
  const now = Date.now();
  const dayAgo = now - 24 * 60 * 60 * 1000;
  const weekAgo = now - 7 * 24 * 60 * 60 * 1000;
  const monthAgo = now - 30 * 24 * 60 * 60 * 1000;

  const stats = await Promise.all([
    c.env.DB.prepare('SELECT COUNT(*) as count FROM businesses WHERE status = ?').bind('active').first<{ count: number }>(),
    c.env.DB.prepare('SELECT COUNT(*) as count FROM accounts WHERE deleted_at = 0').first<{ count: number }>(),
    c.env.DB.prepare('SELECT COUNT(*) as count FROM memberships WHERE status = ?').bind('active').first<{ count: number }>(),
    c.env.DB.prepare('SELECT COUNT(*) as count FROM devices WHERE status = ?').bind('active').first<{ count: number }>(),
    c.env.DB.prepare('SELECT COUNT(*) as count FROM sync_batches WHERE started_at > ? AND status = ?').bind(dayAgo, 'completed').first<{ count: number }>(),
    c.env.DB.prepare('SELECT COUNT(*) as count FROM sync_exceptions WHERE status = ?').bind('open').first<{ count: number }>(),
    c.env.DB.prepare('SELECT COUNT(*) as count FROM backups WHERE created_at > ?').bind(dayAgo).first<{ count: number }>(),
  ]);

  return c.json({
    success: true,
    data: {
      active_businesses: stats[0]?.count ?? 0,
      total_accounts: stats[1]?.count ?? 0,
      active_memberships: stats[2]?.count ?? 0,
      active_devices: stats[3]?.count ?? 0,
      sync_batches_24h: stats[4]?.count ?? 0,
      open_exceptions: stats[5]?.count ?? 0,
      backups_24h: stats[6]?.count ?? 0,
    }
  });
}

/**
 * GET /admin/platform/monitoring
 * Platform monitoring data (resource usage, errors)
 */
export async function getPlatformMonitoring(c: Context<{ Bindings: CloudflareBindings }>) {
  // D1 doesn't expose storage metrics directly via SQL
  // This would typically come from Cloudflare API or be tracked separately

  const errors = await rows<SyncBatchRow>( c.env.DB.prepare(
    `SELECT operation, COUNT(*) as count FROM sync_batches WHERE status = 'failed' AND started_at > ? GROUP BY operation`
  ).bind(Date.now() - 24 * 60 * 60 * 1000).all<SyncBatchRow>());

  const exceptionTypes = await rows<SyncExceptionRow>( c.env.DB.prepare(
    `SELECT exception_type, COUNT(*) as count FROM sync_exceptions WHERE status = 'open' AND created_at > ? GROUP BY exception_type`
  ).bind(Date.now() - 24 * 60 * 60 * 1000).all<SyncExceptionRow>());

  return c.json({
    success: true,
    data: {
      sync_errors_24h: errors,
      exception_types_24h: exceptionTypes,
      // These would come from Cloudflare Analytics API in production
      d1_storage_mb: 'N/A (via Cloudflare API)',
      d1_rows_read_24h: 'N/A (via Cloudflare API)',
      d1_rows_written_24h: 'N/A (via Cloudflare API)',
      r2_storage_gb: 'N/A (via Cloudflare API)',
      worker_requests_24h: 'N/A (via Cloudflare API)',
      worker_errors_24h: 'N/A (via Cloudflare API)',
    }
  });
}

/**
 * GET /admin/audit
 * Platform admin audit log
 */
export async function getAuditLog(c: Context<{ Bindings: CloudflareBindings }>) {
  const limit = Math.min(parseInt(c.req.query('limit') || '100', 10), 500);
  const offset = parseInt(c.req.query('offset') || '0', 10);
  const actorId = c.req.query('actor_id');

  let query = 'SELECT * FROM change_log WHERE actor_role = ?';
  const params: (string | number)[] = ['admin'];

  if (actorId) {
    query += ' AND actor_id = ?';
    params.push(actorId);
  }

  query += ' ORDER BY server_seq DESC LIMIT ? OFFSET ?';
  params.push(limit, offset);

  const audit = await rows( c.env.DB.prepare(query).bind(...params).all());

  return c.json({
    success: true,
    data: audit,
    pagination: { limit, offset }
  });
}

export const adminRoutes = {
  listBusinesses,
  getBusiness,
  updateBusinessStatus,
  listBusinessDevices,
  adminDeviceAction,
  getSyncHealth,
  getBusinessExceptions,
  getPlatformStats,
  getPlatformMonitoring,
  getAuditLog,
};
