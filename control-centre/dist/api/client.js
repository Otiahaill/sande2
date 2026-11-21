/**
 * SANDE Control Centre API Client
 *
 * Communicates with the Cloudflare Workers backend admin routes:
 *   GET /admin/businesses
 *   GET /admin/businesses/:businessId
 *   PUT /admin/businesses/:businessId/status
 *   GET /admin/businesses/:businessId/devices
 *   PUT /admin/businesses/:businessId/devices/:deviceCode
 *   GET /admin/businesses/:businessId/sync
 *   GET /admin/businesses/:businessId/exceptions
 *   GET /admin/platform/stats
 *   GET /admin/platform/monitoring
 *   GET /admin/audit
 *
 * Auth: Bearer token (platform admin JWT) in Authorization header.
 * The backend verifies the admin via platform_admins table.
 */
class ApiErrorClass extends Error {
    code;
    message;
    status;
    details;
    constructor(code, message, status, details) {
        super(message);
        this.code = code;
        this.message = message;
        this.status = status;
        this.details = details;
        this.name = 'ApiError';
    }
}
/**
 * API client for the Control Centre SPA.
 * All requests require a valid Bearer token (platform admin JWT).
 * Tokens are stored in sessionStorage and set by the login page.
 */
export class SandeApiClient {
    baseUrl;
    token;
    constructor(baseUrl, token = null) {
        this.baseUrl = baseUrl.replace(/\/+$/, '');
        this.token = token;
    }
    setToken(token) {
        this.token = token;
    }
    getToken() {
        return this.token;
    }
    headers() {
        const h = {
            'Content-Type': 'application/json',
        };
        if (this.token) {
            h['Authorization'] = `Bearer ${this.token}`;
        }
        return h;
    }
    async request(method, path, body) {
        const url = `${this.baseUrl}/admin${path}`;
        const init = {
            method,
            headers: this.headers(),
        };
        if (body !== undefined) {
            init.body = JSON.stringify(body);
        }
        const res = await fetch(url, init);
        const text = await res.text();
        let json;
        try {
            json = JSON.parse(text);
        }
        catch {
            throw new ApiErrorClass(res.status.toString(), `HTTP ${res.status}: ${text.slice(0, 200)}`, res.status);
        }
        if (!res.ok || !json || !json.success) {
            const err = json?.error;
            throw new ApiErrorClass(err?.code || res.status.toString(), err?.message || `Request failed: ${res.status}`, res.status, err?.details);
        }
        return json;
    }
    /** GET /admin/businesses - List all businesses (with optional status filter) */
    async listBusinesses(status) {
        const params = status ? `?status=${encodeURIComponent(status)}` : '';
        const res = await this.request(`GET`, `/businesses${params}`);
        return res.data ?? [];
    }
    /** GET /admin/businesses/:businessId - Get business details */
    async getBusiness(businessId) {
        const res = await this.request(`GET`, `/businesses/${encodeURIComponent(businessId)}`);
        return res.data ?? {};
    }
    /** PUT /admin/businesses/:businessId/status - Update business status */
    async updateBusinessStatus(businessId, status, reason) {
        await this.request(`PUT`, `/businesses/${encodeURIComponent(businessId)}/status`, { status, reason });
    }
    /** GET /admin/businesses/:businessId/devices - List devices */
    async listDevices(businessId) {
        const res = await this.request(`GET`, `/businesses/${encodeURIComponent(businessId)}/devices`);
        return res.data ?? [];
    }
    /** PUT /admin/businesses/:businessId/devices/:deviceCode - Admin device action */
    async adminDeviceAction(businessId, deviceCode, action, reason) {
        await this.request(`PUT`, `/businesses/${encodeURIComponent(businessId)}/devices/${encodeURIComponent(deviceCode)}`, { action, reason });
    }
    /** GET /admin/businesses/:businessId/sync - Sync health */
    async getSyncHealth(businessId) {
        const res = await this.request(`GET`, `/businesses/${encodeURIComponent(businessId)}/sync`);
        return res.data ?? { total_batches_24h: 0, failed_batches_24h: 0, success_rate: '100%', open_exceptions: 0, device_cursors: [] };
    }
    /** GET /admin/businesses/:businessId/exceptions - Sync exceptions */
    async getExceptions(businessId, status) {
        const params = status ? `?status=${encodeURIComponent(status)}` : '';
        const res = await this.request(`GET`, `/businesses/${encodeURIComponent(businessId)}/exceptions${params}`);
        return res.data ?? [];
    }
    /** GET /admin/platform/stats - Platform statistics */
    async getPlatformStats() {
        const res = await this.request(`GET`, `/platform/stats`);
        return res.data ?? { active_businesses: 0, total_accounts: 0, active_memberships: 0, active_devices: 0, sync_batches_24h: 0, open_exceptions: 0, backups_24h: 0 };
    }
    /** GET /admin/platform/monitoring - Platform monitoring */
    async getPlatformMonitoring() {
        const res = await this.request(`GET`, `/platform/monitoring`);
        return res.data ?? { sync_errors_24h: [], exception_types_24h: [], d1_storage_mb: 'N/A', d1_rows_read_24h: 'N/A', d1_rows_written_24h: 'N/A', r2_storage_gb: 'N/A', worker_requests_24h: 'N/A', worker_errors_24h: 'N/A' };
    }
    /** GET /admin/audit - Platform admin audit log */
    async getAuditLog(limit = 100, offset = 0, actorId) {
        const params = new URLSearchParams({ limit: String(limit), offset: String(offset) });
        if (actorId)
            params.append('actor_id', actorId);
        const res = await this.request(`GET`, `/audit?${params}`);
        return res.data ?? [];
    }
}
/**
 * Singleton client instance. Token set from sessionStorage on login.
 */
export const apiClient = new SandeApiClient(typeof window !== 'undefined' ? window.__SANDE_API_BASE || '' : '');
export { ApiErrorClass };
