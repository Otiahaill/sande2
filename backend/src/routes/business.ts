import { Context } from 'hono';
import { CloudflareBindings } from '../types';
import { createTenantDB } from '../db/tenant';
import { BusinessProfileRow } from '../db/rows';


/**
 * GET /api/business/me
 * Get current business info
 */
export async function getMyBusiness(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any; platformDB: any } }>) {
  const context = c.get('businessContext');
  const platformDB = c.get('platformDB');

  const business = await platformDB.getBusiness(context.businessId);
  if (!business) {
    return c.json({
      success: false,
      error: { code: '404', message: 'Business not found' }
    }, 404);
  }

  return c.json({
    success: true,
    data: {
      id: business.id,
      name: business.name,
      category: business.category,
      phone: business.phone,
      country_code: business.country_code,
      currency_code: business.currency_code,
      timezone: business.timezone,
      address: business.address,
      logo_r2_key: business.logo_r2_key,
      negative_stock_policy: business.negative_stock_policy,
      receipt_footer: business.receipt_footer,
      status: business.status,
      created_at: business.created_at,
    }
  });
}

/**
 * PUT /api/business/me
 * Update business info (owner only)
 */
export async function updateMyBusiness(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any; platformDB: any } }>) {
  const body = await c.req.json<{
    name?: string;
    category?: string;
    phone?: string;
    address?: string;
    negative_stock_policy?: 'allow' | 'warn' | 'block';
    receipt_footer?: string;
  }>();

  const context = c.get('businessContext');
  const now = Date.now();

  const updates: string[] = [];
  const values: (string | number | null)[] = [];

  if (body.name !== undefined) { updates.push('name = ?'); values.push(body.name); }
  if (body.category !== undefined) { updates.push('category = ?'); values.push(body.category); }
  if (body.phone !== undefined) { updates.push('phone = ?'); values.push(body.phone); }
  if (body.address !== undefined) { updates.push('address = ?'); values.push(body.address); }
  if (body.negative_stock_policy !== undefined) { updates.push('negative_stock_policy = ?'); values.push(body.negative_stock_policy); }
  if (body.receipt_footer !== undefined) { updates.push('receipt_footer = ?'); values.push(body.receipt_footer); }

  if (updates.length === 0) {
    return c.json({ success: true, data: { message: 'No changes' } });
  }

  updates.push('updated_at = ?');
  values.push(now);
  values.push(context.businessId);

  await c.env.DB.prepare(
    `UPDATE businesses SET ${updates.join(', ')} WHERE id = ?`
  ).bind(...values).run();

  // Also update business_profiles
  const profileUpdates: string[] = [];
  const profileValues: (string | number | null)[] = [];

  if (body.name !== undefined) { profileUpdates.push('name = ?'); profileValues.push(body.name); }
  if (body.category !== undefined) { profileUpdates.push('category = ?'); profileValues.push(body.category); }
  if (body.phone !== undefined) { profileUpdates.push('phone = ?'); profileValues.push(body.phone); }
  if (body.address !== undefined) { profileUpdates.push('address = ?'); profileValues.push(body.address); }
  if (body.negative_stock_policy !== undefined) { profileUpdates.push('negative_stock_policy = ?'); profileValues.push(body.negative_stock_policy); }
  if (body.receipt_footer !== undefined) { profileUpdates.push('receipt_footer = ?'); profileValues.push(body.receipt_footer); }

  if (profileUpdates.length > 0) {
    profileUpdates.push('updated_at = ?');
    profileValues.push(now);
    profileValues.push(context.businessId);

    await c.env.DB.prepare(
      `UPDATE business_profiles SET ${profileUpdates.join(', ')} WHERE business_id = ?`
    ).bind(...profileValues).run();
  }

  return c.json({
    success: true,
    data: { message: 'Business updated' }
  });
}

/**
 * GET /api/business/me/profile
 * Get business profile (settings)
 */
export async function getProfile(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any } }>) {
  const context = c.get('businessContext');
  const tenantDB = createTenantDB(c.env.DB, context);

  // Get business profile from tenant DB
  const profile = await c.env.DB.prepare(
    `SELECT * FROM business_profiles WHERE business_id = ?`
  ).bind(context.businessId).first<BusinessProfileRow>();

  if (!profile) {
    return c.json({
      success: false,
      error: { code: '404', message: 'Business profile not found' }
    }, 404);
  }

  return c.json({
    success: true,
    data: {
      name: profile.name,
      category: profile.category,
      phone: profile.phone,
      country_code: profile.country_code,
      currency_code: profile.currency_code,
      timezone: profile.timezone,
      address: profile.address,
      logo_r2_key: profile.logo_r2_key,
      negative_stock_policy: profile.negative_stock_policy,
      receipt_footer: profile.receipt_footer,
      schema_version: profile.schema_version,
    }
  });
}

/**
 * PUT /api/business/me/profile
 * Update business profile (owner only)
 */
export async function updateProfile(c: Context<{ Bindings: CloudflareBindings; Variables: { businessContext: any } }>) {
  const body = await c.req.json<{
    name?: string;
    category?: string;
    phone?: string;
    address?: string;
    negative_stock_policy?: 'allow' | 'warn' | 'block';
    receipt_footer?: string;
    logo_r2_key?: string;
  }>();

  const context = c.get('businessContext');
  const now = Date.now();

  const updates: string[] = [];
  const values: (string | number | null)[] = [];

  if (body.name !== undefined) { updates.push('name = ?'); values.push(body.name); }
  if (body.category !== undefined) { updates.push('category = ?'); values.push(body.category); }
  if (body.phone !== undefined) { updates.push('phone = ?'); values.push(body.phone); }
  if (body.address !== undefined) { updates.push('address = ?'); values.push(body.address); }
  if (body.negative_stock_policy !== undefined) { updates.push('negative_stock_policy = ?'); values.push(body.negative_stock_policy); }
  if (body.receipt_footer !== undefined) { updates.push('receipt_footer = ?'); values.push(body.receipt_footer); }
  if (body.logo_r2_key !== undefined) { updates.push('logo_r2_key = ?'); values.push(body.logo_r2_key); }

  if (updates.length === 0) {
    return c.json({ success: true, data: { message: 'No changes' } });
  }

  updates.push('updated_at = ?');
  values.push(now);
  values.push(context.businessId);

  await c.env.DB.prepare(
    `UPDATE business_profiles SET ${updates.join(', ')} WHERE business_id = ?`
  ).bind(...values).run();

  // Also update businesses table for core fields
  const coreUpdates: string[] = [];
  const coreValues: (string | number | null)[] = [];

  if (body.name !== undefined) { coreUpdates.push('name = ?'); coreValues.push(body.name); }
  if (body.category !== undefined) { coreUpdates.push('category = ?'); coreValues.push(body.category); }
  if (body.phone !== undefined) { coreUpdates.push('phone = ?'); coreValues.push(body.phone); }
  if (body.address !== undefined) { coreUpdates.push('address = ?'); coreValues.push(body.address); }
  if (body.negative_stock_policy !== undefined) { coreUpdates.push('negative_stock_policy = ?'); coreValues.push(body.negative_stock_policy); }
  if (body.receipt_footer !== undefined) { coreUpdates.push('receipt_footer = ?'); coreValues.push(body.receipt_footer); }
  if (body.logo_r2_key !== undefined) { coreUpdates.push('logo_r2_key = ?'); coreValues.push(body.logo_r2_key); }

  if (coreUpdates.length > 0) {
    coreUpdates.push('updated_at = ?');
    coreValues.push(now);
    coreValues.push(context.businessId);

    await c.env.DB.prepare(
      `UPDATE businesses SET ${coreUpdates.join(', ')} WHERE id = ?`
    ).bind(...coreValues).run();
  }

  return c.json({
    success: true,
    data: { message: 'Profile updated' }
  });
}

export const businessRoutes = {
  getMyBusiness,
  updateMyBusiness,
  getProfile,
  updateProfile,
};
