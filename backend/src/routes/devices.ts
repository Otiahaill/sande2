import { Context } from 'hono';
import { CloudflareBindings } from '../types';
import { createPlatformDB } from '../db/tenant';
import { AccountRow } from './db/rows.ts';


/**
 * GET /api/devices
 * List devices for current business
 */
export async function list(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any; platformDB: any } }>) {
  const context = c.get('businessContext');
  const platformDB = c.get('platformDB');

  const devices = await platformDB.getDevicesByBusiness(context.businessId);

  // Enrich with account info
  const enriched = await Promise.all(devices.map(async (d) => {
    const account = await c.env.DB.prepare('SELECT id, email, display_name FROM accounts WHERE id = ?').bind(d.account_id).first<AccountRow>();
    const membership = await platformDB.getMembership(context.businessId, d.account_id);

    return {
      id: d.id,
      device_code: d.device_code,
      device_name: d.device_name,
      device_type: d.device_type,
      status: d.status,
      account_email: account?.email,
      account_display_name: account?.display_name,
      role: membership?.role,
      approved_by: d.approved_by,
      approved_at: d.approved_at,
      revoked_at: d.revoked_at,
      revoked_by: d.revoked_by,
      last_seen_at: d.last_seen_at,
      last_sync_at: d.last_sync_at,
      created_at: d.created_at,
    };
  }));

  return c.json({
    success: true,
    data: enriched
  });
}

/**
 * POST /api/devices/register
 * Register a new device (handled in auth.ts, but kept for API completeness)
 */
export async function register(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any; platformDB: any } }>) {
  const body = await c.req.json<{ deviceCode: string; deviceName: string; deviceType: 'android' | 'ios' | 'web'; pushToken?: string }>();
  const context = c.get('businessContext');
  const platformDB = c.get('platformDB');
  const now = Date.now();

  // Check device limit (max 5 active devices per business)
  const activeDevices = await platformDB.getActiveDeviceCount(context.businessId);
  if (activeDevices >= 5) {
    return c.json({
      success: false,
      error: { code: '400', message: 'Device limit reached (max 5 per business)' }
    }, 400);
  }

  // Check per-account limit
  const accountDevices = await platformDB.getActiveDeviceCountByAccount(context.accountId);
  const maxDevices = context.role === 'owner' ? 2 : 1;
  if (accountDevices >= maxDevices) {
    return c.json({
      success: false,
      error: { code: '400', message: `Device limit reached (max ${maxDevices} for ${context.role})` }
    }, 400);
  }

  // Check if device already registered
  const existingDevice = await platformDB.getDevice(body.deviceCode);
  if (existingDevice) {
    if (existingDevice.business_id === context.businessId && existingDevice.account_id === context.accountId) {
      await c.env.DB.prepare(
        `UPDATE devices SET last_seen_at = ?, push_token = ?, updated_at = ? WHERE device_code = ?`
      ).bind(now, body.pushToken ?? null, now, body.deviceCode).run();

      return c.json({
        success: true,
        data: { device_code: body.deviceCode, status: existingDevice.status }
      });
    }
    return c.json({
      success: false,
      error: { code: '400', message: 'Device code already registered to another account' }
    }, 400);
  }

  // Register new device
  const deviceId = crypto.randomUUID();
  const status = context.role === 'owner' ? 'active' : 'pending_approval';

  await platformDB.createDevice({
    id: deviceId,
    business_id: context.businessId,
    account_id: context.accountId,
    device_code: body.deviceCode,
    device_name: body.deviceName,
    device_type: body.deviceType,
    status,
  });

  // Sync cursor
  const cursorId = `${context.businessId}:${body.deviceCode}`;
  await c.env.DB.prepare(
    `INSERT INTO sync_cursors (id, business_id, device_code, cursor, last_pull_at, last_push_at, pending_push_count, updated_at)
     VALUES (?, ?, ?, 0, ?, ?, 0, ?)`
  ).bind(cursorId, context.businessId, body.deviceCode, now, now, now).run();

  return c.json({
    success: true,
    data: {
      device_code: body.deviceCode,
      status,
      message: status === 'pending_approval' ? 'Awaiting owner approval' : 'Device registered'
    }
  });
}

/**
 * PUT /api/devices/:deviceCode
 * Update device info
 */
export async function update(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any; platformDB: any } }>) {
  const deviceCode = c.req.param('deviceCode');
  const body = await c.req.json<{ deviceName?: string; pushToken?: string }>();
  const context = c.get('businessContext');
  const platformDB = c.get('platformDB');

  const device = await platformDB.getDevice(deviceCode);
  if (!device || device.business_id !== context.businessId) {
    return c.json({
      success: false,
      error: { code: '404', message: 'Device not found' }
    }, 404);
  }

  // Can only update own devices unless owner
  if (device.account_id !== context.accountId && context.role !== 'owner') {
    return c.json({
      success: false,
      error: { code: '403', message: 'Cannot update another user\'s device' }
    }, 403);
  }

  const now = Date.now();
  const updates: string[] = [];
  const values: (string | number | null)[] = [];

  if (body.deviceName !== undefined) { updates.push('device_name = ?'); values.push(body.deviceName); }
  if (body.pushToken !== undefined) { updates.push('push_token = ?'); values.push(body.pushToken); }

  if (updates.length > 0) {
    updates.push('updated_at = ?');
    values.push(now);
    values.push(deviceCode);

    await c.env.DB.prepare(
      `UPDATE devices SET ${updates.join(', ')} WHERE device_code = ?`
    ).bind(...values).run();
  }

  return c.json({
    success: true,
    data: { message: 'Device updated' }
  });
}

/**
 * POST /api/devices/:deviceCode/approve
 * Approve a pending device (owner only)
 */
export async function approve(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any; platformDB: any } }>) {
  const deviceCode = c.req.param('deviceCode');
  const context = c.get('businessContext');
  const platformDB = c.get('platformDB');
  const now = Date.now();

  const device = await platformDB.getDevice(deviceCode);
  if (!device || device.business_id !== context.businessId) {
    return c.json({
      success: false,
      error: { code: '404', message: 'Device not found' }
    }, 404);
  }

  if (device.status !== 'pending_approval') {
    return c.json({
      success: false,
      error: { code: '400', message: 'Device not pending approval' }
    }, 400);
  }

  // Check device limit again
  const activeDevices = await platformDB.getActiveDeviceCount(context.businessId);
  if (activeDevices >= 5) {
    return c.json({
      success: false,
      error: { code: '400', message: 'Device limit reached (max 5 per business)' }
    }, 400);
  }

  const accountDevices = await platformDB.getActiveDeviceCountByAccount(device.account_id);
  const maxDevices = device.role === 'owner' ? 2 : 1; // Need to get role from membership
  const membership = await platformDB.getMembership(context.businessId, device.account_id);
  const deviceMaxDevices = membership?.role === 'owner' ? 2 : 1;
  if (accountDevices >= deviceMaxDevices) {
    return c.json({
      success: false,
      error: { code: '400', message: `Device limit reached for this account (max ${deviceMaxDevices})` }
    }, 400);
  }

  await platformDB.updateDeviceStatus(deviceCode, 'active', context.accountId);

  return c.json({
    success: true,
    data: { message: 'Device approved' }
  });
}

/**
 * DELETE /api/devices/:deviceCode
 * Revoke a device (owner only, or own device)
 */
export async function revoke(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any; platformDB: any } }>) {
  const deviceCode = c.req.param('deviceCode');
  const context = c.get('businessContext');
  const platformDB = c.get('platformDB');
  const now = Date.now();

  const device = await platformDB.getDevice(deviceCode);
  if (!device || device.business_id !== context.businessId) {
    return c.json({
      success: false,
      error: { code: '404', message: 'Device not found' }
    }, 404);
  }

  // Can only revoke own devices unless owner
  if (device.account_id !== context.accountId && context.role !== 'owner') {
    return c.json({
      success: false,
      error: { code: '403', message: 'Cannot revoke another user\'s device' }
    }, 403);
  }

  // Can't revoke owner's primary device
  if (device.account_id === context.accountId && context.role === 'owner') {
    const ownerDevices = await c.env.DB.prepare(
      `SELECT COUNT(*) as count FROM devices WHERE business_id = ? AND account_id = ? AND status = 'active'`
    ).bind(context.businessId, context.accountId).first<{ count: number }>();
    if ((ownerDevices?.count ?? 0) <= 1) {
      return c.json({
        success: false,
        error: { code: '400', message: 'Cannot revoke owner\'s only active device' }
      }, 400);
    }
  }

  await platformDB.updateDeviceStatus(deviceCode, 'revoked', context.accountId);

  // Revoke refresh tokens for this device
  await c.env.DB.prepare(
    `UPDATE refresh_tokens SET revoked_at = ? WHERE device_id = ?`
  ).bind(now, device.id).run();

  // Remove sync cursor
  const cursorId = `${context.businessId}:${deviceCode}`;
  await c.env.DB.prepare(
    `DELETE FROM sync_cursors WHERE id = ?`
  ).bind(cursorId).run();

  return c.json({
    success: true,
    data: { message: 'Device revoked' }
  });
}

export const deviceRoutes = {
  list,
  register,
  update,
  approve,
  revoke,
};
