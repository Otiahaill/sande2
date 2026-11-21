import { Context } from 'hono';
import { CloudflareBindings } from '../types';

/**
 * GET /health
 * Health check endpoint
 */
export async function healthCheck(c: Context<{ Bindings: CloudflareBindings }>) {
  const start = Date.now();
  let dbHealthy = false;
  let r2Healthy = false;
  let kvHealthy = false;

  // Check D1
  try {
    await c.env.DB.prepare('SELECT 1').first();
    dbHealthy = true;
  } catch (err) {
    console.error('D1 health check failed:', err);
  }

  // Check R2
  try {
    await c.env.R2_BACKUPS.head('health-check');
    r2Healthy = true;
  } catch (err) {
    // Object might not exist, that's OK - bucket exists
    try {
      await c.env.R2_BACKUPS.list({ limit: 1 });
      r2Healthy = true;
    } catch (err2) {
      console.error('R2 health check failed:', err2);
    }
  }

  // Check KV
  try {
    await c.env.SESSION_KV.get('health-check');
    kvHealthy = true;
  } catch (err) {
    console.error('KV health check failed:', err);
  }

  const latency = Date.now() - start;
  const healthy = dbHealthy && r2Healthy && kvHealthy;

  return c.json({
    status: healthy ? 'healthy' : 'degraded',
    timestamp: new Date().toISOString(),
    latency_ms: latency,
    checks: {
      d1: dbHealthy ? 'ok' : 'failed',
      r2: r2Healthy ? 'ok' : 'failed',
      kv: kvHealthy ? 'ok' : 'failed',
    },
    version: '1.0.0',
  }, healthy ? 200 : 503);
}
