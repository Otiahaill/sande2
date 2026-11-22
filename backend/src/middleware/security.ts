import { Context, Next } from 'hono';
import { CloudflareBindings } from '../types';
import { createPlatformDB } from '../db/tenant';

/**
 * Minimum version enforcement middleware.
 * Rejects requests from app versions below the minimum supported version.
 * The app sends its version code in the X-App-Version header.
 */
export async function minVersionMiddleware(c: Context<{ Bindings: CloudflareBindings }>, next: Next) {
  const appVersion = c.req.header('X-App-Version');
  const platform = c.req.header('X-App-Platform') || 'android';

  if (!appVersion) {
    // Allow requests without version header (legacy clients, health checks)
    await next();
    return;
  }

  const versionCode = parseInt(appVersion, 10);
  if (isNaN(versionCode)) {
    await next();
    return;
  }

  const platformDB = createPlatformDB(c.env.DB);
  const minVersion = await platformDB.getMinSupportedVersion(platform);

  if (versionCode < minVersion) {
    return c.json({
      success: false,
      error: {
        code: 'APP_VERSION_TOO_OLD',
        message: 'App version too old. Please update to the latest version.',
        details: { minVersion }
      }
    }, 426); // 426 Upgrade Required
  }

  await next();
}

/**
 * Security headers middleware.
 * Adds security headers to all responses.
 */
export async function securityHeadersMiddleware(c: Context<{ Bindings: CloudflareBindings }>, next: Next) {
  await next();

  const res = c.res;
  if (res) {
    res.headers.set('X-Content-Type-Options', 'nosniff');
    res.headers.set('X-Frame-Options', 'DENY');
    res.headers.set('X-XSS-Protection', '1; mode=block');
    res.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');
    res.headers.set('Content-Security-Policy', "default-src 'self'; connect-src 'self' https://*.cloudflare.com https://accounts.google.com https://www.googleapis.com;");
  }
}

/**
 * Business tenant enforcement middleware.
 * Ensures that the business context matches the authenticated device's business.
 * Prevents cross-tenant data access.
 */
export async function tenantIsolationMiddleware(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any } }>, next: Next) {
  const context = c.get('businessContext');
  const requestBusinessId = c.req.header('X-Business-Id');

  if (requestBusinessId && requestBusinessId !== context.businessId) {
    return c.json({
      success: false,
      error: {
        code: 'TENANT_MISMATCH',
        message: 'Requested business does not match authenticated device business'
      }
    }, 403);
  }

  await next();
}
