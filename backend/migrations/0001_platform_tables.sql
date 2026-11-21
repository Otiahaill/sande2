-- Migration 0001: Platform tables
-- Core platform entities: businesses, accounts, memberships, invitations, devices, tokens, recovery codes, sync, backups, app versions

-- Businesses table
CREATE TABLE IF NOT EXISTS businesses (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'retail',
    phone TEXT,
    country_code TEXT NOT NULL DEFAULT 'KE',
    currency_code TEXT NOT NULL DEFAULT 'KES',
    timezone TEXT NOT NULL DEFAULT 'Africa/Nairobi',
    address TEXT,
    logo_r2_key TEXT,
    negative_stock_policy TEXT NOT NULL DEFAULT 'warn',
    receipt_footer TEXT,
    status TEXT NOT NULL DEFAULT 'active', -- active, suspended, deleted
    schema_version INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted_at INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_businesses_status ON businesses(status);
CREATE INDEX IF NOT EXISTS idx_businesses_created_at ON businesses(created_at);

-- Accounts table (platform-level user accounts)
CREATE TABLE IF NOT EXISTS accounts (
    id TEXT PRIMARY KEY NOT NULL,
    email TEXT NOT NULL UNIQUE,
    email_verified INTEGER NOT NULL DEFAULT 0,
    display_name TEXT,
    photo_url TEXT,
    google_sub TEXT UNIQUE, -- Google OAuth subject
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted_at INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_accounts_email ON accounts(email);
CREATE INDEX IF NOT EXISTS idx_accounts_google_sub ON accounts(google_sub);

-- Memberships table (account -> business with role)
CREATE TABLE IF NOT EXISTS memberships (
    id TEXT PRIMARY KEY NOT NULL, -- business_id:account_id
    business_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    role TEXT NOT NULL, -- owner, staff
    status TEXT NOT NULL DEFAULT 'invited', -- invited, active, suspended, revoked
    invited_by TEXT,
    invited_at INTEGER,
    accepted_at INTEGER,
    revoked_at INTEGER,
    revoked_by TEXT,
    device_code TEXT, -- Primary device code if registered
    last_synced_at INTEGER,
    server_version INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    UNIQUE(business_id, account_id)
);

CREATE INDEX IF NOT EXISTS idx_memberships_business ON memberships(business_id);
CREATE INDEX IF NOT EXISTS idx_memberships_account ON memberships(account_id);
CREATE INDEX IF NOT EXISTS idx_memberships_status ON memberships(status);
CREATE INDEX IF NOT EXISTS idx_memberships_device_code ON memberships(device_code);

-- Invitations table
CREATE TABLE IF NOT EXISTS invitations (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    email TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'staff',
    invited_by TEXT NOT NULL,
    code_hash TEXT NOT NULL, -- SHA-256 of the invitation code
    expires_at INTEGER NOT NULL,
    accepted_at INTEGER,
    accepted_by TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
    FOREIGN KEY (invited_by) REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_invitations_business ON invitations(business_id);
CREATE INDEX IF NOT EXISTS idx_invitations_email ON invitations(email);
CREATE INDEX IF NOT EXISTS idx_invitations_code_hash ON invitations(code_hash);

-- Devices table
CREATE TABLE IF NOT EXISTS devices (
    id TEXT PRIMARY KEY NOT NULL, -- device_code
    business_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    device_code TEXT NOT NULL UNIQUE,
    device_name TEXT NOT NULL,
    device_type TEXT NOT NULL, -- android, ios, web
    status TEXT NOT NULL DEFAULT 'pending_approval', -- active, pending_approval, revoked, replaced
    approved_by TEXT,
    approved_at INTEGER,
    revoked_at INTEGER,
    revoked_by TEXT,
    last_seen_at INTEGER NOT NULL,
    last_sync_at INTEGER,
    push_token TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_devices_business ON devices(business_id);
CREATE INDEX IF NOT EXISTS idx_devices_account ON devices(account_id);
CREATE INDEX IF NOT EXISTS idx_devices_device_code ON devices(device_code);
CREATE INDEX IF NOT EXISTS idx_devices_status ON devices(status);

-- Refresh tokens table
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id TEXT PRIMARY KEY NOT NULL,
    account_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    token_hash TEXT NOT NULL, -- SHA-256 of the refresh token
    expires_at INTEGER NOT NULL,
    revoked_at INTEGER,
    replaced_by TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_account ON refresh_tokens(account_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_device ON refresh_tokens(device_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires ON refresh_tokens(expires_at);

-- Recovery codes table (owner-only)
CREATE TABLE IF NOT EXISTS recovery_codes (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    code_hash TEXT NOT NULL, -- SHA-256
    used_at INTEGER,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_recovery_codes_business_account ON recovery_codes(business_id, account_id);

-- Sync batches table
CREATE TABLE IF NOT EXISTS sync_batches (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    device_code TEXT NOT NULL,
    direction TEXT NOT NULL, -- push, pull
    status TEXT NOT NULL DEFAULT 'pending', -- pending, processing, completed, failed
    record_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    error_summary TEXT,
    started_at INTEGER NOT NULL,
    completed_at INTEGER,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sync_batches_business ON sync_batches(business_id);
CREATE INDEX IF NOT EXISTS idx_sync_batches_device ON sync_batches(device_code);
CREATE INDEX IF NOT EXISTS idx_sync_batches_status ON sync_batches(status);

-- Sync exceptions table
CREATE TABLE IF NOT EXISTS sync_exceptions (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    device_code TEXT NOT NULL,
    exception_type TEXT NOT NULL, -- STOCK_NEGATIVE, PRICE_MISMATCH, CLOCK_SKEW, REVOKED_DEVICE, VALIDATION, CONFLICT
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    local_version INTEGER NOT NULL,
    server_version INTEGER NOT NULL,
    description TEXT NOT NULL,
    local_data_json TEXT NOT NULL,
    server_data_json TEXT,
    resolution TEXT, -- local_wins, server_wins, manual, pending
    resolved_by TEXT,
    resolved_at INTEGER,
    status TEXT NOT NULL DEFAULT 'open', -- open, resolved, ignored
    created_at INTEGER NOT NULL,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sync_exceptions_business ON sync_exceptions(business_id);
CREATE INDEX IF NOT EXISTS idx_sync_exceptions_device ON sync_exceptions(device_code);
CREATE INDEX IF NOT EXISTS idx_sync_exceptions_status ON sync_exceptions(status);
CREATE INDEX IF NOT EXISTS idx_sync_exceptions_type ON sync_exceptions(exception_type);
CREATE INDEX IF NOT EXISTS idx_sync_exceptions_created ON sync_exceptions(created_at);

-- Change log table (for audit/debugging)
CREATE TABLE IF NOT EXISTS change_log (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    operation TEXT NOT NULL, -- create, update, delete
    actor_id TEXT NOT NULL,
    actor_role TEXT NOT NULL,
    device_code TEXT NOT NULL,
    before_json TEXT,
    after_json TEXT,
    metadata_json TEXT,
    server_seq INTEGER NOT NULL, -- Monotonic sequence per business
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_change_log_business ON change_log(business_id);
CREATE INDEX IF NOT EXISTS idx_change_log_entity ON change_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_change_log_actor ON change_log(actor_id);
CREATE INDEX IF NOT EXISTS idx_change_log_server_seq ON change_log(business_id, server_seq);
CREATE INDEX IF NOT EXISTS idx_change_log_created ON change_log(created_at);

-- Backups table
CREATE TABLE IF NOT EXISTS backups (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    r2_key TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    row_counts_json TEXT NOT NULL, -- JSON: {products: 100, sale_sessions: 50, ...}
    schema_version INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'completed', -- pending, completed, failed
    error TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_backups_business ON backups(business_id);
CREATE INDEX IF NOT EXISTS idx_backups_created ON backups(created_at);

-- App versions table (for minimum version enforcement)
CREATE TABLE IF NOT EXISTS app_versions (
    id TEXT PRIMARY KEY NOT NULL, -- version_code as string
    version_code INTEGER NOT NULL UNIQUE,
    version_name TEXT NOT NULL,
    min_supported_version_code INTEGER NOT NULL,
    platform TEXT NOT NULL, -- android, ios, web
    release_notes TEXT,
    download_url TEXT,
    is_mandatory INTEGER NOT NULL DEFAULT 0,
    released_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_app_versions_platform ON app_versions(platform);
CREATE INDEX IF NOT EXISTS idx_app_versions_version_code ON app_versions(version_code);

-- Platform admins table
CREATE TABLE IF NOT EXISTS platform_admins (
    id TEXT PRIMARY KEY NOT NULL, -- account_id
    account_id TEXT NOT NULL UNIQUE,
    role TEXT NOT NULL DEFAULT 'admin', -- admin, super_admin
    permissions_json TEXT, -- JSON array of permissions
    created_at INTEGER NOT NULL,
    created_by TEXT NOT NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_platform_admins_account ON platform_admins(account_id);

-- Support access grants
CREATE TABLE IF NOT EXISTS support_access_grants (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    granted_by TEXT NOT NULL, -- owner account_id
    granted_to TEXT NOT NULL, -- platform admin account_id or support account_id
    scope TEXT NOT NULL, -- full, read_only, financial_only, sync_only
    reason TEXT NOT NULL,
    ticket_ref TEXT,
    expires_at INTEGER NOT NULL,
    revoked_at INTEGER,
    revoked_by TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
    FOREIGN KEY (granted_by) REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_support_access_business ON support_access_grants(business_id);
CREATE INDEX IF NOT EXISTS idx_support_access_granted_to ON support_access_grants(granted_to);
CREATE INDEX IF NOT EXISTS idx_support_access_expires ON support_access_grants(expires_at);
