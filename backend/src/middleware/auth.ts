import { Context, Next } from 'hono';
import { verify } from 'hono/jwt';
import { CloudflareBindings, BusinessContext, JWTPayload } from '../types';
import { createPlatformDB } from '../db/tenant';

export interface AuthVariables {
  businessContext: BusinessContext;
  platformDB: ReturnType<typeof createPlatformDB>;
}

/**
 * Middleware to verify JWT access token and extract business context
 */
export async function authMiddleware(c: Context<{ Bindings: CloudflareBindings; Variables: AuthVariables }>, next: Next) {
  const authHeader = c.req.header('Authorization');
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return c.json({
      success: false,
      error: { code: '401', message: 'Missing or invalid Authorization header' }
    }, 401);
  }

  const token = authHeader.slice(7); // Remove 'Bearer '

  try {
    // Verify JWT signature
    const payload = await verify(token, c.env.JWT_SECRET, 'HS256') as unknown as JWTPayload;

    // Check token type
    if (payload.type !== 'access') {
      return c.json({
        success: false,
        error: { code: '401', message: 'Invalid token type' }
      }, 401);
    }

    // Check expiration
    if (payload.exp * 1000 < Date.now()) {
      return c.json({
        success: false,
        error: { code: '401', message: 'Token expired' }
      }, 401);
    }

    // Verify membership still exists and is active
    const platformDB = createPlatformDB(c.env.DB);
    const membership = await platformDB.getMembership(payload.business_id, payload.sub);

    if (!membership || membership.status !== 'active') {
      return c.json({
        success: false,
        error: { code: '401', message: 'Membership revoked or inactive' }
      }, 401);
    }

    // Verify device is still active
    const device = await platformDB.getDevice(payload.device_code);
    if (!device || device.status !== 'active') {
      return c.json({
        success: false,
        error: { code: '401', message: 'Device revoked or inactive' }
      }, 401);
    }

    // Verify business is active
    const business = await platformDB.getBusiness(payload.business_id);
    if (!business || business.status !== 'active') {
      return c.json({
        success: false,
        error: { code: '401', message: 'Business suspended or deleted' }
      }, 401);
    }

    // Check offline lease (access token valid for 15 min, refresh token for lease period)
    // The lease check is done at refresh time, not here

    // Set context
    const businessContext: BusinessContext = {
      businessId: payload.business_id,
      accountId: payload.sub,
      role: payload.role,
      deviceCode: payload.device_code,
      membershipId: membership.id,
    };

    c.set('businessContext', businessContext);
    c.set('platformDB', platformDB);

    await next();
  } catch (err) {
    console.error('Auth error:', err);
    return c.json({
      success: false,
      error: { code: '401', message: 'Invalid or expired token' }
    }, 401);
  }
}

/**
 * Middleware to require owner role
 */
export async function requireOwner(c: Context<{ Bindings: CloudflareBindings; Variables: AuthVariables }>, next: Next) {
  const context = c.get('businessContext');
  if (context.role !== 'owner') {
    return c.json({
      success: false,
      error: { code: '403', message: 'Owner access required' }
    }, 403);
  }
  await next();
}

/**
 * Middleware to require platform admin
 */
export async function requireAdmin(c: Context<{ Bindings: CloudflareBindings; Variables: AuthVariables }>, next: Next) {
  const authHeader = c.req.header('Authorization');
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return c.json({
      success: false,
      error: { code: '401', message: 'Admin authentication required' }
    }, 401);
  }

  const token = authHeader.slice(7);

  try {
    // Verify admin JWT (different secret or same with admin claim)
    const payload = await verify(token, c.env.JWT_SECRET, 'HS256') as unknown as JWTPayload & { admin?: boolean };

    const platformDB = createPlatformDB(c.env.DB);
    const isAdmin = await platformDB.isPlatformAdmin(payload.sub);

    if (!isAdmin) {
      return c.json({
        success: false,
        error: { code: '403', message: 'Platform admin access required' }
      }, 403);
    }

    c.set('platformDB', platformDB);
    await next();
  } catch (err) {
    return c.json({
      success: false,
      error: { code: '401', message: 'Invalid admin token' }
    }, 401);
  }
}

/**
 * Generates access token (15 min expiry)
 */
export async function generateAccessToken(
  env: CloudflareBindings,
  payload: Omit<JWTPayload, 'iat' | 'exp' | 'type'>
): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const tokenPayload: JWTPayload = {
    ...payload,
    iat: now,
    exp: now + 15 * 60, // 15 minutes
    type: 'access',
  };

  // Use hono/jwt sign
  const { sign } = await import('hono/jwt');
  return sign(tokenPayload as unknown as Record<string, unknown>, env.JWT_SECRET);
}

/**
 * Generates refresh token (lease period, e.g., 14 days)
 */
export async function generateRefreshToken(
  env: CloudflareBindings,
  payload: Omit<JWTPayload, 'iat' | 'exp' | 'type'>
): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const leaseDays = 14; // Configurable per business
  const tokenPayload: JWTPayload = {
    ...payload,
    iat: now,
    exp: now + leaseDays * 24 * 60 * 60, // lease days
    type: 'refresh',
  };

  const { sign } = await import('hono/jwt');
  return sign(tokenPayload as unknown as Record<string, unknown>, env.JWT_SECRET);
}

/**
 * Verifies Google ID token and extracts user info
 */
export async function verifyGoogleIdToken(idToken: string, clientId: string) {
  const { createRemoteJWKSet, jwtVerify } = await import('jose');
  const JWKS = createRemoteJWKSet(new URL('https://www.googleapis.com/oauth2/v3/certs'));

  const { payload } = await jwtVerify(idToken, JWKS, {
    issuer: 'https://accounts.google.com',
    audience: clientId,
  });

  // jose's payload type has an index signature, so the claims are read
  // explicitly.
  const claims = payload as unknown as Record<string, unknown>;
  return {
    sub: claims['sub'] as string,
    email: claims['email'] as string,
    email_verified: claims['email_verified'] as boolean,
    name: claims['name'] as string,
    picture: claims['picture'] as string,
  };
}

/**
 * Hashes a token for storage (SHA-256)
 */
export async function hashToken(token: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(token);
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Generates a secure random token
 */
export function generateSecureToken(length = 32): string {
  const array = new Uint8Array(length);
  crypto.getRandomValues(array);
  return Array.from(array, byte => byte.toString(16).padStart(2, '0')).join('');
}

/**
 * Generates a 6-digit invitation code
 */
export function generateInvitationCode(): string {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

/**
 * Generates a recovery code (8 alphanumeric chars)
 */
export function generateRecoveryCode(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // No confusing chars
  let code = '';
  for (let i = 0; i < 8; i++) {
    code += chars[Math.floor(Math.random() * chars.length)];
  }
  return code;
}
