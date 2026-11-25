import { Context } from 'hono';
import { CloudflareBindings, BusinessContext, GoogleAuthInput, RefreshTokenInput } from '../types';
import { MembershipRow } from '../db/rows';
import { createPlatformDB } from '../db/tenant';
import {
  verifyGoogleIdToken,
  hashToken,
  generateSecureToken,
  generateAccessToken,
  generateRefreshToken,
  generateInvitationCode,
  generateRecoveryCode,
} from '../middleware/auth';

/**
 * POST /auth/google
 * Authenticate with Google ID token, find/create account, return access + refresh tokens
 */
export async function googleAuth(c: Context<{ Bindings: CloudflareBindings }>) {
  const body = await c.req.json<GoogleAuthInput>();

  // Verify Google ID token
  let googleUser;
  try {
    googleUser = await verifyGoogleIdToken(body.idToken, c.env.GOOGLE_CLIENT_ID);
  } catch (err) {
    console.error('Google token verification failed:', err);
    return c.json({
      success: false,
      error: { code: '401', message: 'Invalid Google ID token' }
    }, 401);
  }

  if (!googleUser.email_verified) {
    return c.json({
      success: false,
      error: { code: '401', message: 'Google email not verified' }
    }, 401);
  }

  const platformDB = createPlatformDB(c.env.DB);
  const now = Date.now();

  // Find or create account
  let account = await platformDB.getAccountByGoogleSub(googleUser.sub);
  if (!account) {
    account = await platformDB.getAccountByEmail(googleUser.email);
    if (account) {
      // Link Google account to existing account
      await c.env.DB.prepare(
        `UPDATE accounts SET google_sub = ?, photo_url = ?, updated_at = ? WHERE id = ?`
      ).bind(googleUser.sub, googleUser.picture ?? null, now, account.id).run();
    } else {
      // Create new account
      const accountId = crypto.randomUUID();
      await platformDB.createAccount({
        id: accountId,
        email: googleUser.email,
        display_name: googleUser.name,
        photo_url: googleUser.picture,
        google_sub: googleUser.sub,
      });
      account = await platformDB.getAccountByEmail(googleUser.email);
    }
  }

  // Find memberships for this account
  const memberships = await platformDB.getMembershipsByAccount(account!.id);

  if (memberships.length === 0) {
    // No memberships - return account info so frontend can show business selection/registration
    return c.json({
      success: true,
      data: {
        account: {
          id: account!.id,
          email: account!.email,
          display_name: account!.display_name,
          photo_url: account!.photo_url,
        },
        memberships: [],
        needsBusinessSelection: true,
      }
    });
  }

  // If device_code provided, check if device is registered
  let selectedMembership: MembershipRow | undefined = memberships[0];
  if (body.deviceCode) {
    const deviceMembership = memberships.find(m => m.device_code === body.deviceCode);
    if (deviceMembership) {
      selectedMembership = deviceMembership;
    }
  }
  if (!selectedMembership) {
    return c.json({
      success: false,
      error: { code: '403', message: 'No membership for this account' }
    }, 403);
  }

  // If only one membership, use it; otherwise frontend should let user choose
  if (memberships.length > 1 && !body.deviceCode) {
    return c.json({
      success: true,
      data: {
        account: {
          id: account!.id,
          email: account!.email,
          display_name: account!.display_name,
          photo_url: account!.photo_url,
        },
        memberships: memberships.map(m => ({
          id: m.id,
          business_id: m.business_id,
          role: m.role,
          status: m.status,
          device_code: m.device_code,
        })),
        needsBusinessSelection: true,
      }
    });
  }

  // Generate tokens for selected membership
  const business = await platformDB.getBusiness(selectedMembership.business_id);
  if (!business || business.status !== 'active') {
    return c.json({
      success: false,
      error: { code: '403', message: 'Business suspended or deleted' }
    }, 403);
  }

  if (selectedMembership.status !== 'active') {
    return c.json({
      success: false,
      error: { code: '403', message: 'Membership not active' }
    }, 403);
  }

  // Check/Register device
  let device = await platformDB.getDevice(body.deviceCode);
  if (!device) {
    // Register new device (pending approval for staff)
    const deviceId = crypto.randomUUID();
    const isOwner = selectedMembership.role === 'owner';
    const status = isOwner ? 'active' : 'pending_approval';

    await platformDB.createDevice({
      id: deviceId,
      business_id: selectedMembership.business_id,
      account_id: account!.id,
      device_code: body.deviceCode,
      device_name: body.deviceName,
      device_type: body.deviceType,
      status,
    });

    device = await platformDB.getDevice(body.deviceCode);
  } else if (device.status === 'revoked') {
    return c.json({
      success: false,
      error: { code: '403', message: 'Device revoked' }
    }, 403);
  } else if (device.status === 'replaced') {
    return c.json({
      success: false,
      error: { code: '403', message: 'Device replaced' }
    }, 403);
  }

  // Update device last seen
  await c.env.DB.prepare(
    `UPDATE devices SET last_seen_at = ?, push_token = ?, updated_at = ? WHERE device_code = ?`
  ).bind(now, body.pushToken ?? null, now, body.deviceCode).run();

  // Generate tokens
  const accessToken = await generateAccessToken(c.env, {
    sub: account!.id,
    business_id: selectedMembership.business_id,
    role: selectedMembership.role as 'owner' | 'staff',
    device_code: body.deviceCode,
  });

  const refreshToken = await generateRefreshToken(c.env, {
    sub: account!.id,
    business_id: selectedMembership.business_id,
    role: selectedMembership.role as 'owner' | 'staff',
    device_code: body.deviceCode,
  });

  // Store refresh token hash
  const refreshTokenHash = await hashToken(refreshToken);
  const expiresAt = Math.floor(Date.now() / 1000) + 14 * 24 * 60 * 60; // 14 days

  await platformDB.createRefreshToken({
    id: crypto.randomUUID(),
    account_id: account!.id,
    device_id: device!.id,
    token_hash: refreshTokenHash,
    expires_at: expiresAt,
  });

  // Update membership last_synced_at
  await c.env.DB.prepare(
    `UPDATE memberships SET last_synced_at = ?, updated_at = ? WHERE id = ?`
  ).bind(now, now, selectedMembership.id).run();

  return c.json({
    success: true,
    data: {
      accessToken,
      refreshToken,
      expiresIn: 15 * 60, // 15 minutes
      membership: {
        id: selectedMembership.id,
        business_id: selectedMembership.business_id,
        business_name: business.name,
        role: selectedMembership.role as 'owner' | 'staff',
        device_code: body.deviceCode,
        device_status: device!.status,
      },
    }
  });
}

/**
 * POST /business/register
 * Register a new business (owner becomes first member)
 */
export async function registerBusiness(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: BusinessContext; platformDB: ReturnType<typeof createPlatformDB> } }>) {
  const body = await c.req.json<{
    name: string;
    category: string;
    phone?: string;
    countryCode: string;
    currencyCode: string;
    timezone: string;
    address?: string;
  }>();

  const context = c.get('businessContext');
  const platformDB = c.get('platformDB');

  // Check if account already owns a business
  const existingMemberships = await platformDB.getMembershipsByAccount(context.accountId);
  const ownedBusiness = existingMemberships.find(m => m.role === 'owner' && m.status === 'active');
  if (ownedBusiness) {
    return c.json({
      success: false,
      error: { code: '400', message: 'Account already owns a business' }
    }, 400);
  }

  const businessId = crypto.randomUUID();
  const membershipId = `${businessId}:${context.accountId}`;
  const now = Date.now();

  // Create business, membership, device, recovery codes in one batch
  const statements = [];

  // Business
  statements.push(c.env.DB.prepare(
    `INSERT INTO businesses (id, name, category, phone, country_code, currency_code, timezone, address, status, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'active', ?, ?)`
  ).bind(businessId, body.name, body.category, body.phone ?? null, body.countryCode, body.currencyCode, body.timezone, body.address ?? null, now, now));

  // Business profile
  statements.push(c.env.DB.prepare(
    `INSERT INTO business_profiles (id, business_id, name, category, phone, country_code, currency_code, timezone, address, negative_stock_policy, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'warn', ?, ?)`
  ).bind(businessId, businessId, body.name, body.category, body.phone ?? null, body.countryCode, body.currencyCode, body.timezone, body.address ?? null, now, now));

  // Membership (owner)
  statements.push(c.env.DB.prepare(
    `INSERT INTO memberships (id, business_id, account_id, role, status, accepted_at, created_at, updated_at)
     VALUES (?, ?, ?, 'owner', 'active', ?, ?, ?)`
  ).bind(membershipId, businessId, context.accountId, now, now, now));

  // Device (owner's current device)
  const deviceId = crypto.randomUUID();
  statements.push(c.env.DB.prepare(
    `INSERT INTO devices (id, business_id, account_id, device_code, device_name, device_type, status, approved_by, approved_at, last_seen_at, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, 'active', ?, ?, ?, ?, ?)`
  ).bind(deviceId, businessId, context.accountId, context.deviceCode, 'Owner Device', 'android', context.accountId, now, now, now, now));

  // Sync cursor
  const cursorId = `${businessId}:${context.deviceCode}`;
  statements.push(c.env.DB.prepare(
    `INSERT INTO sync_cursors (id, business_id, device_code, cursor, last_pull_at, last_push_at, pending_push_count, updated_at)
     VALUES (?, ?, ?, 0, ?, ?, 0, ?)`
  ).bind(cursorId, businessId, context.deviceCode, now, now, now));

  // Recovery codes (generate 10)
  const recoveryCodes: string[] = [];
  for (let i = 0; i < 10; i++) {
    recoveryCodes.push(generateRecoveryCode());
  }

  const codeHashes = await Promise.all(recoveryCodes.map(c => hashToken(c)));
  for (const hash of codeHashes) {
    statements.push(c.env.DB.prepare(
      `INSERT INTO recovery_codes (id, business_id, account_id, code_hash, created_at)
       VALUES (?, ?, ?, ?, ?)`
    ).bind(crypto.randomUUID(), businessId, context.accountId, hash, now));
  }

  // Execute batch
  await c.env.DB.batch(statements);

  // Generate new tokens for the new business
  const accessToken = await generateAccessToken(c.env, {
    sub: context.accountId,
    business_id: businessId,
    role: 'owner',
    device_code: context.deviceCode,
  });

  const refreshToken = await generateRefreshToken(c.env, {
    sub: context.accountId,
    business_id: businessId,
    role: 'owner',
    device_code: context.deviceCode,
  });

  // Store refresh token
  const refreshTokenHash = await hashToken(refreshToken);
  const expiresAt = Math.floor(Date.now() / 1000) + 14 * 24 * 60 * 60;
  await platformDB.createRefreshToken({
    id: crypto.randomUUID(),
    account_id: context.accountId,
    device_id: deviceId,
    token_hash: refreshTokenHash,
    expires_at: expiresAt,
  });

  return c.json({
    success: true,
    data: {
      accessToken,
      refreshToken,
      expiresIn: 15 * 60,
      business: {
        id: businessId,
        name: body.name,
        category: body.category,
        currency_code: body.currencyCode,
        timezone: body.timezone,
      },
      recoveryCodes, // Only shown once!
      membership: {
        id: membershipId,
        role: 'owner',
        device_code: context.deviceCode,
      }
    }
  });
}

/**
 * POST /memberships/accept
 * Accept a staff invitation
 */
export async function acceptInvitation(c: Context<{ Bindings: CloudflareBindings }>) {
  const body = await c.req.json<{ code: string; deviceCode: string; deviceName: string; deviceType: 'android' | 'ios' | 'web' }>();

  const platformDB = createPlatformDB(c.env.DB);
  const now = Date.now();

  // Hash the code and find invitation
  const codeHash = await hashToken(body.code);
  const invitation = await platformDB.getInvitationByCodeHash(codeHash);

  if (!invitation) {
    return c.json({
      success: false,
      error: { code: '404', message: 'Invalid or expired invitation code' }
    }, 404);
  }

  // Verify Google auth (need to get account from current token or re-auth)
  // For now, assume the user is already authenticated via Google
  // In practice, this would be called after googleAuth or with a valid token
  const authHeader = c.req.header('Authorization');
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return c.json({
      success: false,
      error: { code: '401', message: 'Authentication required to accept invitation' }
    }, 401);
  }

  // Get account from existing token (simplified)
  // In production, decode the token to get account_id
  // For now, we'll need the account_id from the request
  const accountId = c.req.header('X-Account-Id');
  if (!accountId) {
    return c.json({
      success: false,
      error: { code: '400', message: 'Account ID required' }
    }, 400);
  }

  // Check if account already has a membership in this business
  const existingMembership = await platformDB.getMembership(invitation.business_id, accountId);
  if (existingMembership) {
    return c.json({
      success: false,
      error: { code: '400', message: 'Already a member of this business' }
    }, 400);
  }

  // Check staff limit (max 4 staff per business)
  const memberships = await platformDB.getMembershipsByBusiness(invitation.business_id);
  const staffCount = memberships.filter(m => m.role === 'staff' && m.status === 'active').length;
  if (staffCount >= 4) {
    return c.json({
      success: false,
      error: { code: '400', message: 'Staff limit reached (max 4)' }
    }, 400);
  }

  // Accept invitation
  await platformDB.acceptInvitation(invitation.id, accountId);

  // Create membership
  const membershipId = `${invitation.business_id}:${accountId}`;
  await platformDB.createMembership({
    id: membershipId,
    business_id: invitation.business_id,
    account_id: accountId,
    role: invitation.role as 'owner' | 'staff',
    status: 'active',
    invited_by: invitation.invited_by,
  });

  // Register device
  const deviceId = crypto.randomUUID();
  await platformDB.createDevice({
    id: deviceId,
    business_id: invitation.business_id,
    account_id: accountId,
    device_code: body.deviceCode,
    device_name: body.deviceName,
    device_type: body.deviceType,
    status: 'pending_approval', // Staff devices need owner approval
  });

  // Sync cursor
  const cursorId = `${invitation.business_id}:${body.deviceCode}`;
  await c.env.DB.prepare(
    `INSERT INTO sync_cursors (id, business_id, device_code, cursor, last_pull_at, last_push_at, pending_push_count, updated_at)
     VALUES (?, ?, ?, 0, ?, ?, 0, ?)`
  ).bind(cursorId, invitation.business_id, body.deviceCode, now, now, now).run();

  // Generate tokens
  const business = await platformDB.getBusiness(invitation.business_id);

  const accessToken = await generateAccessToken(c.env, {
    sub: accountId,
    business_id: invitation.business_id,
    role: invitation.role as 'owner' | 'staff',
    device_code: body.deviceCode,
  });

  const refreshToken = await generateRefreshToken(c.env, {
    sub: accountId,
    business_id: invitation.business_id,
    role: invitation.role as 'owner' | 'staff',
    device_code: body.deviceCode,
  });

  const refreshTokenHash = await hashToken(refreshToken);
  const expiresAt = Math.floor(Date.now() / 1000) + 14 * 24 * 60 * 60;
  await platformDB.createRefreshToken({
    id: crypto.randomUUID(),
    account_id: accountId,
    device_id: deviceId,
    token_hash: refreshTokenHash,
    expires_at: expiresAt,
  });

  return c.json({
    success: true,
    data: {
      accessToken,
      refreshToken,
      expiresIn: 15 * 60,
      membership: {
        id: membershipId,
        business_id: invitation.business_id,
        business_name: business?.name,
        role: invitation.role as 'owner' | 'staff',
        device_code: body.deviceCode,
        device_status: 'pending_approval',
      }
    }
  });
}

/**
 * POST /devices/register
 * Register a new device for current membership
 */
export async function registerDevice(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: BusinessContext } }>) {
  const body = await c.req.json<{ deviceCode: string; deviceName: string; deviceType: 'android' | 'ios' | 'web'; pushToken?: string }>();

  const context = c.get('businessContext');
  const platformDB = createPlatformDB(c.env.DB);
  const now = Date.now();

  // Check device limit (max 5 active devices per business)
  const activeDevices = await platformDB.getActiveDeviceCount(context.businessId);
  if (activeDevices >= 5) {
    return c.json({
      success: false,
      error: { code: '400', message: 'Device limit reached (max 5 per business)' }
    }, 400);
  }

  // Check per-account limit (owner can have 2, staff 1)
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
      // Same device, update last seen
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
 * POST /auth/refresh
 * Refresh access token using refresh token
 */
export async function refreshToken(c: Context<{ Bindings: CloudflareBindings }>) {
  const body = await c.req.json<RefreshTokenInput>();
  const platformDB = createPlatformDB(c.env.DB);

  // Hash the refresh token
  const refreshTokenHash = await hashToken(body.refreshToken);

  // Find valid refresh token
  const storedToken = await platformDB.getRefreshToken(refreshTokenHash);
  if (!storedToken) {
    return c.json({
      success: false,
      error: { code: '401', message: 'Invalid or expired refresh token' }
    }, 401);
  }

  // Verify device still active
  const device = await platformDB.getDevice(storedToken.device_id);
  if (!device || device.status !== 'active') {
    // Revoke the refresh token
    await platformDB.revokeRefreshToken(refreshTokenHash);
    return c.json({
      success: false,
      error: { code: '401', message: 'Device revoked or inactive' }
    }, 401);
  }

  // Verify membership still active
  const membership = await platformDB.getMembership(device.business_id, storedToken.account_id);
  if (!membership || membership.status !== 'active') {
    await platformDB.revokeRefreshToken(refreshTokenHash);
    return c.json({
      success: false,
      error: { code: '401', message: 'Membership revoked or inactive' }
    }, 401);
  }

  // Verify business active
  const business = await platformDB.getBusiness(device.business_id);
  if (!business || business.status !== 'active') {
    await platformDB.revokeRefreshToken(refreshTokenHash);
    return c.json({
      success: false,
      error: { code: '401', message: 'Business suspended or deleted' }
    }, 401);
  }

  // Check lease expiry (refresh token expiry is the lease)
  // If we're here, token is valid (checked by getRefreshToken)

  // Rotate refresh token (revoke old, issue new)
  const newRefreshToken = await generateRefreshToken(c.env, {
    sub: storedToken.account_id,
    business_id: device.business_id,
    role: membership.role as 'owner' | 'staff',
    device_code: device.device_code,
  });

  const newRefreshTokenHash = await hashToken(newRefreshToken);
  const expiresAt = Math.floor(Date.now() / 1000) + 14 * 24 * 60 * 60;

  await platformDB.createRefreshToken({
    id: crypto.randomUUID(),
    account_id: storedToken.account_id,
    device_id: device.id,
    token_hash: newRefreshTokenHash,
    expires_at: expiresAt,
  });

  // Revoke old token
  await platformDB.revokeRefreshToken(refreshTokenHash, newRefreshTokenHash);

  // Generate new access token
  const accessToken = await generateAccessToken(c.env, {
    sub: storedToken.account_id,
    business_id: device.business_id,
    role: membership.role as 'owner' | 'staff',
    device_code: device.device_code,
  });

  return c.json({
    success: true,
    data: {
      accessToken,
      refreshToken: newRefreshToken,
      expiresIn: 15 * 60,
    }
  });
}

/**
 * POST /auth/logout
 * Revoke refresh token
 */
export async function logout(c: Context<{ Bindings: CloudflareBindings }>) {
  const body = await c.req.json<RefreshTokenInput>();

  const refreshTokenHash = await hashToken(body.refreshToken);
  const platformDB = createPlatformDB(c.env.DB);

  await platformDB.revokeRefreshToken(refreshTokenHash);

  return c.json({
    success: true,
    data: { message: 'Logged out successfully' }
  });
}
