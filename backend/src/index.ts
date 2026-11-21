import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { logger } from 'hono/logger';
import { prettyJSON } from 'hono/pretty-json';
import { HTTPException } from 'hono/http-exception';
import { authMiddleware, requireOwner, requireAdmin } from './middleware/auth';
import { rateLimitMiddleware } from './middleware/rateLimit';
import { minVersionMiddleware, securityHeadersMiddleware, tenantIsolationMiddleware } from './middleware/security';
import { businessDb } from './db/tenant';
import { createPlatformDB } from './db/tenant';
import { googleAuth, registerBusiness, acceptInvitation, registerDevice, refreshToken, logout } from './routes/auth';
import { businessRoutes } from './routes/business';
import { membershipRoutes } from './routes/memberships';
import { deviceRoutes } from './routes/devices';
import { syncRoutes } from './routes/sync';
import { adminRoutes } from './routes/admin';
import { backupRoutes } from './routes/backup';
import { healthCheck } from './routes/health';

type Bindings = CloudflareBindings;

const app = new Hono<{ Bindings: Bindings }>();

// Global middleware
app.use('*', logger());
app.use('*', prettyJSON());
app.use('*', cors({
  origin: ['*'],
  allowHeaders: ['Content-Type', 'Authorization', 'X-Device-Code', 'X-Business-Id', 'X-App-Version', 'X-App-Platform'],
  allowMethods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  maxAge: 86400,
}));
app.use('*', rateLimitMiddleware);
app.use('*', securityHeadersMiddleware);

// Health check (no auth)
app.get('/health', healthCheck);

// Public auth routes
app.post('/auth/google', googleAuth);
app.post('/auth/refresh', refreshToken);
app.post('/auth/logout', logout);

// Protected routes (require valid access token)
const protectedRoutes = app.basePath('/api');

protectedRoutes.use('*', authMiddleware);
protectedRoutes.use('*', minVersionMiddleware);
protectedRoutes.use('*', tenantIsolationMiddleware);

// Business registration & info
protectedRoutes.post('/business/register', registerBusiness);
protectedRoutes.get('/business/me', businessRoutes.getMyBusiness);
protectedRoutes.put('/business/me', requireOwner, businessRoutes.updateMyBusiness);
protectedRoutes.get('/business/me/profile', businessRoutes.getProfile);
protectedRoutes.put('/business/me/profile', requireOwner, businessRoutes.updateProfile);

// Membership management (owner only)
protectedRoutes.get('/memberships', requireOwner, membershipRoutes.list);
protectedRoutes.post('/memberships/invite', requireOwner, membershipRoutes.invite);
protectedRoutes.post('/memberships/accept', acceptInvitation);
protectedRoutes.put('/memberships/:membershipId', requireOwner, membershipRoutes.update);
protectedRoutes.delete('/memberships/:membershipId', requireOwner, membershipRoutes.revoke);

// Device management
protectedRoutes.get('/devices', deviceRoutes.list);
protectedRoutes.post('/devices/register', deviceRoutes.register);
protectedRoutes.put('/devices/:deviceCode', deviceRoutes.update);
protectedRoutes.post('/devices/:deviceCode/approve', requireOwner, deviceRoutes.approve);
protectedRoutes.delete('/devices/:deviceCode', requireOwner, deviceRoutes.revoke);

// Synchronization
protectedRoutes.post('/sync/push', syncRoutes.push);
protectedRoutes.get('/sync/pull', syncRoutes.pull);
protectedRoutes.get('/sync/status', syncRoutes.status);
protectedRoutes.get('/sync/exceptions', syncRoutes.exceptions);
protectedRoutes.put('/sync/exceptions/:exceptionId/resolve', syncRoutes.resolveException);

// Backup & Restore
protectedRoutes.post('/backup/create', requireOwner, backupRoutes.create);
protectedRoutes.get('/backup/list', requireOwner, backupRoutes.list);
protectedRoutes.post('/backup/restore', requireOwner, backupRoutes.restore);
protectedRoutes.get('/backup/status', backupRoutes.status);

// Admin routes (separate auth)
const adminRoutesApp = app.basePath('/admin');
adminRoutesApp.use('*', requireAdmin);

adminRoutesApp.get('/businesses', adminRoutes.listBusinesses);
adminRoutesApp.get('/businesses/:businessId', adminRoutes.getBusiness);
adminRoutesApp.put('/businesses/:businessId/status', adminRoutes.updateBusinessStatus);
adminRoutesApp.get('/businesses/:businessId/devices', adminRoutes.listBusinessDevices);
adminRoutesApp.put('/businesses/:businessId/devices/:deviceCode', adminRoutes.adminDeviceAction);
adminRoutesApp.get('/businesses/:businessId/sync', adminRoutes.getSyncHealth);
adminRoutesApp.get('/businesses/:businessId/exceptions', adminRoutes.getBusinessExceptions);
adminRoutesApp.get('/platform/stats', adminRoutes.getPlatformStats);
adminRoutesApp.get('/platform/monitoring', adminRoutes.getPlatformMonitoring);
adminRoutesApp.get('/audit', adminRoutes.getAuditLog);

// Error handler
app.onError((err, c) => {
  if (err instanceof HTTPException) {
    return c.json({
      success: false,
      error: {
        code: err.status.toString(),
        message: err.message,
      },
    }, err.status);
  }

  console.error('Unhandled error:', err);
  return c.json({
    success: false,
    error: {
      code: '500',
      message: 'Internal server error',
    },
  }, 500);
});

// 404 handler
app.notFound((c) => {
  return c.json({
    success: false,
    error: {
      code: '404',
      message: 'Not found',
    },
  }, 404);
});

export default app;
