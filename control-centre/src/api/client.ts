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

export interface Business {
  id: string;
  name: string;
  category: string;
  phone?: string;
  country_code: string;
  currency_code: string;
  timezone: string;
  status: 'active' | 'suspended' | 'deleted';
  created_at: number;
  updated_at: number;
  deleted_at: number;
  member_count: number;
  device_count: number;
  last_sync_at?: number;
}

export interface BusinessDetail extends Business {
  memberships: Array<{
    id: string;
    account_id: string;
    role: 'owner' | 'staff';
    status: string;
    account_email: string;
    account_display_name?: string;
    device_code?: string;
    device_name?: string;
    device_status?: string;
    last_seen_at?: number;
    last_sync_at?: number;
  }>;
  devices: Array<{
    id: string;
    device_code: string;
    device_name: string;
    device_type: string;
    status: 'active' | 'pending_approval' | 'revoked' | 'replaced';
    account_email: string;
    account_display_name?: string;
    role?: string;
    last_seen_at: number;
    last_sync_at?: number;
  }>;
  sync_health: {
    total_batches_24h: number;
    failed_batches_24h: number;
    success_rate: string;
    open_exceptions: number;
  };
}

export interface MembershipAction {
  status: 'active' | 'suspended' | 'revoked';
}

export interface DeviceAction {
  action: 'revoke' | 'force_approve' | 'replace';
  reason?: string;
}

export interface SyncStatus {
  total_batches_24h: number;
  failed_batches_24h: number;
  success_rate: string;
  open_exceptions: number;
  device_cursors: Array<{
    device_code: string;
    cursor: number;
    last_pull_at: number;
    last_push_at: number;
    pending_push_count: number;
  }>;
}

export interface AuditLogEntry {
  id: string;
  business_id: string;
  entity_type: string;
  entity_id: string;
  operation: 'create' | 'update' | 'delete' | 'admin_status_change' | 'admin_device_action';
  actor_id: string;
  actor_role: string;
  device_code: string;
  server_seq: number;
  created_at: number;
}

export interface PlatformStats {
  active_businesses: number;
  total_accounts: number;
  active_memberships: number;
  active_devices: number;
  sync_batches_24h: number;
  open_exceptions: number;
  backups_24h: number;
}

export interface PlatformMonitoring {
  sync_errors_24h: Array<{ operation: string; count: number }>;
  exception_types_24h: Array<{ exception_type: string; count: number }>;
  d1_storage_mb: string;
  d1_rows_read_24h: string;
  d1_rows_written_24h: string;
  r2_storage_gb: string;
  worker_requests_24h: string;
  worker_errors_24h: string;
}

export interface ApiError {
  code: string;
  message: string;
  details?: unknown;
}

export interface ApiResponse<T = unknown> {
  success: boolean;
  data?: T;
  error?: ApiError;
  meta?: {
    timestamp: number;
    requestId: string;
  };
}

class ApiErrorClass extends Error {
  constructor(public code: string, public message: string, public status: number, public details?: unknown) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * API client for the Control Centre SPA.
 * All requests require a valid Bearer token (platform admin JWT).
 * Tokens are stored in sessionStorage and set by the login page.
 */
export class SandeApiClient {
  private baseUrl: string;
  private token: string | null;

  constructor(baseUrl: string, token: string | null = null) {
    this.baseUrl = baseUrl.replace(/\/+$/, '');
    this.token = token;
  }

  setToken(token: string | null) {
    this.token = token;
  }

  getToken(): string | null {
    return this.token;
  }

  private headers(): HeadersInit {
    const h: HeadersInit = {
      'Content-Type': 'application/json',
    };
    if (this.token) {
      h['Authorization'] = `Bearer ${this.token}`;
    }
    return h;
  }

  private async request<T>(
    method: 'GET' | 'POST' | 'PUT' | 'DELETE',
    path: string,
    body?: unknown
  ): Promise<ApiResponse<T>> {
    const url = `${this.baseUrl}/admin${path}`;
    const init: RequestInit = {
      method,
      headers: this.headers(),
    };
    if (body !== undefined) {
      init.body = JSON.stringify(body);
    }

    const res = await fetch(url, init);
    const text = await res.text();
    let json: unknown;
    try {
      json = JSON.parse(text);
    } catch {
      throw new ApiErrorClass(
        res.status.toString(),
        `HTTP ${res.status}: ${text.slice(0, 200)}`,
        res.status
      );
    }

    if (!res.ok || !json || !(json as ApiResponse<T>).success) {
      const err = (json as ApiResponse<T>)?.error;
      throw new ApiErrorClass(
        err?.code || res.status.toString(),
        err?.message || `Request failed: ${res.status}`,
        res.status,
        err?.details
      );
    }

    return json as ApiResponse<T>;
  }

  /** GET /admin/businesses - List all businesses (with optional status filter) */
  async listBusinesses(status?: string): Promise<Business[]> {
    const params = status ? `?status=${encodeURIComponent(status)}` : '';
    const res = await this.request<Business[]>(`GET`, `/businesses${params}`);
    return res.data ?? [];
  }

  /** GET /admin/businesses/:businessId - Get business details */
  async getBusiness(businessId: string): Promise<BusinessDetail> {
    const res = await this.request<BusinessDetail>(`GET`, `/businesses/${encodeURIComponent(businessId)}`);
    return res.data ?? {} as BusinessDetail;
  }

  /** PUT /admin/businesses/:businessId/status - Update business status */
  async updateBusinessStatus(businessId: string, status: 'active' | 'suspended' | 'deleted', reason?: string): Promise<void> {
    await this.request(`PUT`, `/businesses/${encodeURIComponent(businessId)}/status`, { status, reason });
  }

  /** GET /admin/businesses/:businessId/devices - List devices */
  async listDevices(businessId: string): Promise<Array<{
    id: string;
    device_code: string;
    device_name: string;
    device_type: string;
    status: string;
    account_email: string;
    account_display_name?: string;
    role?: string;
    last_seen_at: number;
    last_sync_at?: number;
    created_at: number;
  }>> {
    const res = await this.request<Array<{ id: string; device_code: string; device_name: string; device_type: string; status: string; account_email: string; account_display_name?: string; role?: string; last_seen_at: number; last_sync_at?: number; created_at: number }>>(`GET`, `/businesses/${encodeURIComponent(businessId)}/devices`);
    return res.data ?? [];
  }

  /** PUT /admin/businesses/:businessId/devices/:deviceCode - Admin device action */
  async adminDeviceAction(businessId: string, deviceCode: string, action: 'revoke' | 'force_approve' | 'replace', reason?: string): Promise<void> {
    await this.request(`PUT`, `/businesses/${encodeURIComponent(businessId)}/devices/${encodeURIComponent(deviceCode)}`, { action, reason });
  }

  /** GET /admin/businesses/:businessId/sync - Sync health */
  async getSyncHealth(businessId: string): Promise<SyncStatus> {
    const res = await this.request<SyncStatus>(`GET`, `/businesses/${encodeURIComponent(businessId)}/sync`);
    return res.data ?? { total_batches_24h: 0, failed_batches_24h: 0, success_rate: '100%', open_exceptions: 0, device_cursors: [] };
  }

  /** GET /admin/businesses/:businessId/exceptions - Sync exceptions */
  async getExceptions(businessId: string, status?: string): Promise<Array<{
    id: string;
    business_id: string;
    device_code: string;
    exception_type: string;
    entity_type: string;
    entity_id: string;
    local_version: number;
    server_version: number;
    description: string;
    status: string;
    created_at: number;
  }>> {
    const params = status ? `?status=${encodeURIComponent(status)}` : '';
    const res = await this.request<Array<{ id: string; business_id: string; device_code: string; exception_type: string; entity_type: string; entity_id: string; local_version: number; server_version: number; description: string; status: string; created_at: number }>>(`GET`, `/businesses/${encodeURIComponent(businessId)}/exceptions${params}`);
    return res.data ?? [];
  }

  /** GET /admin/platform/stats - Platform statistics */
  async getPlatformStats(): Promise<PlatformStats> {
    const res = await this.request<PlatformStats>(`GET`, `/platform/stats`);
    return res.data ?? { active_businesses: 0, total_accounts: 0, active_memberships: 0, active_devices: 0, sync_batches_24h: 0, open_exceptions: 0, backups_24h: 0 };
  }

  /** GET /admin/platform/monitoring - Platform monitoring */
  async getPlatformMonitoring(): Promise<PlatformMonitoring> {
    const res = await this.request<PlatformMonitoring>(`GET`, `/platform/monitoring`);
    return res.data ?? { sync_errors_24h: [], exception_types_24h: [], d1_storage_mb: 'N/A', d1_rows_read_24h: 'N/A', d1_rows_written_24h: 'N/A', r2_storage_gb: 'N/A', worker_requests_24h: 'N/A', worker_errors_24h: 'N/A' };
  }

  /** GET /admin/audit - Platform admin audit log */
  async getAuditLog(limit = 100, offset = 0, actorId?: string): Promise<AuditLogEntry[]> {
    const params = new URLSearchParams({ limit: String(limit), offset: String(offset) });
    if (actorId) params.append('actor_id', actorId);
    const res = await this.request<AuditLogEntry[]>(`GET`, `/audit?${params}`);
    return res.data ?? [];
  }
}

/**
 * Singleton client instance. Token set from sessionStorage on login.
 */
export const apiClient = new SandeApiClient(
  typeof window !== 'undefined' ? (window as any).__SANDE_API_BASE || '' : ''
);
export { ApiErrorClass };
