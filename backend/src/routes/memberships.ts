import { Context } from 'hono';
import { CloudflareBindings } from '../types';
import { createPlatformDB } from '../db/tenant';
import { hashToken, generateInvitationCode } from '../middleware/auth';
import { AccountRow } from './db/rows.ts';


/**
 * GET /api/memberships
 * List all memberships for current business (owner only)
 */
export async function list(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any; platformDB: any } }>) {
  const context = c.get('businessContext');
  const platformDB = c.get('platformDB');

  const memberships = await platformDB.getMembershipsByBusiness(context.businessId);

  // Enrich with account info
  const enriched = await Promise.all(memberships.map(async (m) => {
    const account = await c.env.DB.prepare('SELECT id, email, display_name, photo_url FROM accounts WHERE id = ?').bind(m.account_id).first<AccountRow>();
    const device = m.device_code ? await platformDB.getDevice(m.device_code) : null;

    return {
      id: m.id,
      account_id: m.account_id,
      email: account?.email,
      display_name: account?.display_name,
      photo_url: account?.photo_url,
      role: m.role,
      status: m.status,
      invited_by: m.invited_by,
      invited_at: m.invited_at,
      accepted_at: m.accepted_at,
      revoked_at: m.revoked_at,
      device_code: m.device_code,
      device_name: device?.device_name,
      device_type: device?.device_type,
      device_status: device?.status,
      last_seen_at: device?.last_seen_at,
      last_sync_at: device?.last_sync_at,
      created_at: m.created_at,
    };
  }));

  return c.json({
    success: true,
    data: enriched
  });
}

/**
 * POST /api/memberships/invite
 * Invite a staff member (owner only)
 */
export async function invite(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any; platformDB: any } }>) {
  const body = await c.req.json<{ email: string; role: 'staff' }>();
  const context = c.get('businessContext');
  const platformDB = c.get('platformDB');
  const now = Date.now();

  // Check if email already has membership in this business
  const existingAccount = await platformDB.getAccountByEmail(body.email);
  if (existingAccount) {
    const existingMembership = await platformDB.getMembership(context.businessId, existingAccount.id);
    if (existingMembership) {
      return c.json({
        success: false,
        error: { code: '400', message: 'User already has membership in this business' }
      }, 400);
    }
  }

  // Check staff limit
  const memberships = await platformDB.getMembershipsByBusiness(context.businessId);
  const staffCount = memberships.filter(m => m.role === 'staff' && m.status === 'active').length;
  if (staffCount >= 4) {
    return c.json({
      success: false,
      error: { code: '400', message: 'Staff limit reached (max 4)' }
    }, 400);
  }

  // Generate invitation code
  const code = generateInvitationCode();
  const codeHash = await hashToken(code);
  const expiresAt = now + 7 * 24 * 60 * 60 * 1000; // 7 days

  const invitationId = crypto.randomUUID();

  await platformDB.createInvitation({
    id: invitationId,
    business_id: context.businessId,
    email: body.email,
    role: body.role,
    invited_by: context.accountId,
    code_hash: codeHash,
    expires_at: expiresAt,
  });

  // TODO: Send invitation email with code
  // For now, return code in response (in production, only send via email)
  console.log(`Invitation code for ${body.email}: ${code}`);

  return c.json({
    success: true,
    data: {
      invitation_id: invitationId,
      email: body.email,
      role: body.role,
      expires_at: expiresAt,
      code, // Remove in production!
    }
  });
}

/**
 * PUT /api/memberships/:membershipId
 * Update membership status (owner only)
 */
export async function update(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any; platformDB: any } }>) {
  const membershipId = c.req.param('membershipId');
  const body = await c.req.json<{ status: 'active' | 'suspended' | 'revoked' }>();
  const context = c.get('businessContext');
  const platformDB = c.get('platformDB');

  // Parse membershipId (format: business_id:account_id)
  const [businessId, accountId] = membershipId.split(':');
  if (businessId !== context.businessId) {
    return c.json({
      success: false,
      error: { code: '403', message: 'Membership not in this business' }
    }, 403);
  }

  // Can't change own membership
  if (accountId === context.accountId) {
    return c.json({
      success: false,
      error: { code: '400', message: 'Cannot modify own membership' }
    }, 400);
  }

  const membership = await platformDB.getMembership(businessId, accountId);
  if (!membership) {
    return c.json({
      success: false,
      error: { code: '404', message: 'Membership not found' }
    }, 404);
  }

  // Can't demote owner
  if (membership.role === 'owner') {
    return c.json({
      success: false,
      error: { code: '400', message: 'Cannot modify owner membership' }
    }, 400);
  }

  const now = Date.now();
  const updates: string[] = ['status = ?', 'updated_at = ?'];
  const values: (string | number)[] = [body.status, now];

  if (body.status === 'revoked') {
    updates.push('revoked_at = ?');
    updates.push('revoked_by = ?');
    values.push(now);
    values.push(context.accountId);

    // Also revoke device
    if (membership.device_code) {
      await c.env.DB.prepare(
        `UPDATE devices SET status = 'revoked', revoked_at = ?, revoked_by = ?, updated_at = ? WHERE device_code = ?`
      ).bind(now, context.accountId, now, membership.device_code).run();
    }

    // Revoke refresh tokens
    const device = membership.device_code ? await platformDB.getDevice(membership.device_code) : null;
    if (device) {
      await c.env.DB.prepare(
        `UPDATE refresh_tokens SET revoked_at = ? WHERE device_id = ?`
      ).bind(now, device.id).run();
    }
  }

  values.push(businessId);
  values.push(accountId);

  await c.env.DB.prepare(
    `UPDATE memberships SET ${updates.join(', ')} WHERE business_id = ? AND account_id = ?`
  ).bind(...values).run();

  return c.json({
    success: true,
    data: { message: `Membership ${body.status}` }
  });
}

/**
 * DELETE /api/memberships/:membershipId
 * Revoke membership (owner only)
 */
export async function revoke(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any; platformDB: any } }>) {
  return update(c); // Same logic, just calls with status='revoked'
}

export const membershipRoutes = {
  list,
  invite,
  update,
  revoke,
};
