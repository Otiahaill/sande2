// Cloudflare Workers type definitions
export interface CloudflareBindings {
  DB: D1Database;
  R2_BACKUPS: R2Bucket;
  SESSION_KV: KVNamespace;
  RATE_LIMIT_KV: KVNamespace;
  SYNC_COORDINATOR: DurableObjectNamespace;
  GOOGLE_CLIENT_ID: string;
  GOOGLE_CLIENT_SECRET: string;
  JWT_SECRET: string;
  ADMIN_API_KEY: string;
  ENCRYPTION_KEY: string;
  SENTRY_DSN?: string;
}

export interface JWTPayload {
  sub: string; // account_id
  business_id: string;
  role: 'owner' | 'staff';
  device_code: string;
  iat: number;
  exp: number;
  type: 'access' | 'refresh';
}

export interface BusinessContext {
  businessId: string;
  accountId: string;
  role: 'owner' | 'staff';
  deviceCode: string;
  membershipId: string;
}

export interface SyncBatch {
  id: string;
  businessId: string;
  deviceCode: string;
  direction: 'push' | 'pull';
  status: 'pending' | 'processing' | 'completed' | 'failed';
  recordCount: number;
  successCount: number;
  errorCount: number;
  errorSummary?: string;
  startedAt: number;
  completedAt?: number;
}

export interface OutboxEntry {
  id: string;
  businessId: string;
  entityType: string;
  entityId: string;
  operation: 'create' | 'update' | 'delete';
  payloadJson: string;
  batchId?: string;
  attemptCount: number;
  lastError?: string;
  createdAt: number;
  updatedAt: number;
  synced: boolean;
  syncedAt: number;
}

export interface SyncCursor {
  id: string;
  businessId: string;
  deviceCode: string;
  cursor: number;
  lastPullAt: number;
  lastPushAt: number;
  pendingPushCount: number;
  updatedAt: number;
}

export interface SyncException {
  id: string;
  businessId: string;
  deviceCode: string;
  exceptionType: 'STOCK_NEGATIVE' | 'PRICE_MISMATCH' | 'CLOCK_SKEW' | 'REVOKED_DEVICE' | 'VALIDATION' | 'CONFLICT';
  entityType: string;
  entityId: string;
  localVersion: number;
  serverVersion: number;
  description: string;
  localDataJson: string;
  serverDataJson?: string;
  resolution?: 'local_wins' | 'server_wins' | 'manual' | 'pending';
  resolvedBy?: string;
  resolvedAt?: number;
  status: 'open' | 'resolved' | 'ignored';
  createdAt: number;
}

export interface ChangeLogEntry {
  id: string;
  businessId: string;
  entityType: string;
  entityId: string;
  operation: 'create' | 'update' | 'delete';
  actorId: string;
  actorRole: string;
  deviceCode: string;
  beforeJson?: string;
  afterJson?: string;
  metadataJson?: string;
  serverSeq: number;
  createdAt: number;
}

export interface ApiResponse<T = unknown> {
  success: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
    details?: unknown;
  };
  meta?: {
    timestamp: number;
    requestId: string;
  };
}

export interface PaginatedResponse<T> extends ApiResponse<T[]> {
  pagination: {
    cursor?: number;
    limit: number;
    hasMore: boolean;
    total?: number;
  };
}

export interface BusinessRegistrationInput {
  name: string;
  category: string;
  phone?: string;
  countryCode: string;
  currencyCode: string;
  timezone: string;
  address?: string;
}

export interface StaffInvitationInput {
  email: string;
  role: 'staff';
}

export interface DeviceRegistrationInput {
  deviceCode: string;
  deviceName: string;
  deviceType: 'android' | 'ios' | 'web';
  pushToken?: string;
}

export interface GoogleAuthInput {
  idToken: string;
  deviceCode: string;
  deviceName: string;
  deviceType: 'android' | 'ios' | 'web';
  pushToken?: string;
}

export interface RefreshTokenInput {
  refreshToken: string;
}

export interface SyncPushInput {
  batchId: string;
  entries: OutboxEntry[];
}

export interface SyncPullInput {
  cursor: number;
  limit: number;
}
