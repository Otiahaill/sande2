import { Context, Next } from 'hono';
import { CloudflareBindings } from '../types';

/**
 * Rate limiting middleware using KV store
 * Different limits for different endpoint types
 */
export async function rateLimitMiddleware(c: Context<{ Bindings: CloudflareBindings }>, next: Next) {
  const path = c.req.path;
  const ip = c.req.header('CF-Connecting-IP') || c.req.header('X-Forwarded-For') || 'unknown';
  const deviceCode = c.req.header('X-Device-Code') || 'unknown';

  // Define rate limits by path pattern
  const limits: Record<string, { requests: number; windowMs: number }> = {
    '/auth/google': { requests: 10, windowMs: 60 * 1000 }, // 10/min
    '/auth/refresh': { requests: 30, windowMs: 60 * 1000 }, // 30/min
    '/api/business/register': { requests: 5, windowMs: 60 * 1000 }, // 5/min
    '/api/memberships/invite': { requests: 20, windowMs: 60 * 1000 }, // 20/min
    '/api/devices/register': { requests: 10, windowMs: 60 * 1000 }, // 10/min
    '/api/sync/push': { requests: 100, windowMs: 60 * 1000 }, // 100/min
    '/api/sync/pull': { requests: 100, windowMs: 60 * 1000 }, // 100/min
    '/admin/': { requests: 200, windowMs: 60 * 1000 }, // 200/min for admin
  };

  // Find matching limit
  let matchedLimit: { requests: number; windowMs: number } | null = null;
  for (const [pattern, limit] of Object.entries(limits)) {
    if (path.startsWith(pattern)) {
      matchedLimit = limit;
      break;
    }
  }

  // Default limit
  if (!matchedLimit) {
    matchedLimit = { requests: 300, windowMs: 60 * 1000 }; // 300/min default
  }

  const key = `ratelimit:${ip}:${deviceCode}:${path}`;
  const now = Date.now();
  const windowStart = now - matchedLimit.windowMs;

  try {
    // Get current count
    const current = await c.env.RATE_LIMIT_KV.get(key);
    let count = current ? parseInt(current, 10) : 0;

    if (count >= matchedLimit.requests) {
      // Get TTL for retry-after header
      const ttl = await c.env.RATE_LIMIT_KV.get(`${key}:ttl`);
      const retryAfter = ttl ? Math.ceil(parseInt(ttl, 10) / 1000) : Math.ceil(matchedLimit.windowMs / 1000);

      return c.json({
        success: false,
        error: {
          code: '429',
          message: 'Rate limit exceeded',
          details: { retryAfter }
        }
      }, 429, {
        'Retry-After': retryAfter.toString(),
        'X-RateLimit-Limit': matchedLimit.requests.toString(),
        'X-RateLimit-Remaining': '0',
        'X-RateLimit-Reset': Math.ceil((now + matchedLimit.windowMs) / 1000).toString(),
      });
    }

    // Increment count
    count++;
    await c.env.RATE_LIMIT_KV.put(key, count.toString(), { expirationTtl: Math.ceil(matchedLimit.windowMs / 1000) });

    // Set TTL key for retry-after calculation
    await c.env.RATE_LIMIT_KV.put(`${key}:ttl`, (matchedLimit.windowMs).toString(), { expirationTtl: Math.ceil(matchedLimit.windowMs / 1000) });

    // Add rate limit headers
    c.header('X-RateLimit-Limit', matchedLimit.requests.toString());
    c.header('X-RateLimit-Remaining', (matchedLimit.requests - count).toString());
    c.header('X-RateLimit-Reset', Math.ceil((now + matchedLimit.windowMs) / 1000).toString());
  } catch (err) {
    // Fail open on KV errors (don't block requests if rate limiting fails)
    console.error('Rate limit error:', err);
  }

  await next();
}

/**
 * Stricter rate limit for sensitive operations
 */
export async function strictRateLimit(c: Context<{ Bindings: CloudflareBindings }>, next: Next) {
  const ip = c.req.header('CF-Connecting-IP') || 'unknown';
  const key = `ratelimit:strict:${ip}`;
  const limit = 5; // 5 requests
  const windowMs = 15 * 60 * 1000; // 15 minutes

  try {
    const current = await c.env.RATE_LIMIT_KV.get(key);
    let count = current ? parseInt(current, 10) : 0;

    if (count >= limit) {
      return c.json({
        success: false,
        error: {
          code: '429',
          message: 'Too many attempts. Please try again later.'
        }
      }, 429);
    }

    count++;
    await c.env.RATE_LIMIT_KV.put(key, count.toString(), { expirationTtl: Math.ceil(windowMs / 1000) });
  } catch (err) {
    console.error('Strict rate limit error:', err);
  }

  await next();
}

/**
 * Per-business rate limit for sync operations
 */
export async function businessRateLimit(
  c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext?: { businessId?: string } } }>,
  next: Next
) {
  const businessId = c.req.header('X-Business-Id') || c.get('businessContext')?.businessId;
  if (!businessId) {
    await next();
    return;
  }

  const key = `ratelimit:business:${businessId}:sync`;
  const limit = 50; // 50 sync requests
  const windowMs = 60 * 1000; // per minute

  try {
    const current = await c.env.RATE_LIMIT_KV.get(key);
    let count = current ? parseInt(current, 10) : 0;

    if (count >= limit) {
      return c.json({
        success: false,
        error: {
          code: '429',
          message: 'Sync rate limit exceeded for this business'
        }
      }, 429);
    }

    count++;
    await c.env.RATE_LIMIT_KV.put(key, count.toString(), { expirationTtl: Math.ceil(windowMs / 1000) });
  } catch (err) {
    console.error('Business rate limit error:', err);
  }

  await next();
}
