-- Migration 0002: Business-scoped tables
-- These tables are accessed via tenant-scoped DB layer that injects business_id

-- Products table
CREATE TABLE IF NOT EXISTS products (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    name TEXT NOT NULL,
    sku TEXT,
    category_id TEXT,
    category TEXT,
    price_cents INTEGER NOT NULL DEFAULT 0,
    unit_of_measure TEXT,
    conversion_ratio INTEGER NOT NULL DEFAULT 1,
    original_price_cents INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1,
    archived_at INTEGER NOT NULL DEFAULT 0,
    deleted_at INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_products_business ON products(business_id);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(business_id, category_id);
CREATE INDEX IF NOT EXISTS idx_products_active ON products(business_id, is_active);
CREATE INDEX IF NOT EXISTS idx_products_archived ON products(business_id, archived_at);
CREATE INDEX IF NOT EXISTS idx_products_deleted ON products(business_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_products_sku ON products(business_id, sku);

-- Product categories table
CREATE TABLE IF NOT EXISTS product_categories (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    name TEXT NOT NULL,
    parent_id TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_product_categories_business ON product_categories(business_id);
CREATE INDEX IF NOT EXISTS idx_product_categories_parent ON product_categories(business_id, parent_id);

-- Product price history table
CREATE TABLE IF NOT EXISTS product_price_history (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    product_id TEXT NOT NULL,
    old_price_cents INTEGER NOT NULL,
    new_price_cents INTEGER NOT NULL,
    changed_by TEXT NOT NULL,
    reason TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_product_price_history_business ON product_price_history(business_id);
CREATE INDEX IF NOT EXISTS idx_product_price_history_product ON product_price_history(business_id, product_id);
CREATE INDEX IF NOT EXISTS idx_product_price_history_created ON product_price_history(business_id, created_at);

-- Stock movements table (signed-delta ledger)
CREATE TABLE IF NOT EXISTS stock_movements (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    product_id TEXT NOT NULL,
    movement_type TEXT NOT NULL, -- RECEIVE, SALE, ADJUST, VOID, RETURN
    quantity_delta INTEGER NOT NULL, -- Signed: + for receive/adjust/void/return, - for sale
    unit_price_cents INTEGER, -- For SALE/RECEIVE with cost
    actor_id TEXT NOT NULL,
    actor_role TEXT NOT NULL,
    device_code TEXT NOT NULL,
    reason TEXT,
    reference_id TEXT, -- Links to sale_sessions.id, receipts.id, etc.
    reversal_of TEXT, -- ID of movement this reverses (for VOID)
    metadata_json TEXT, -- Extensible: batch #, supplier, etc.
    created_at INTEGER NOT NULL,
    synced INTEGER NOT NULL DEFAULT 0,
    server_seq INTEGER, -- Assigned during sync ingestion
    running_balance INTEGER, -- Running balance after this movement
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_stock_movements_business ON stock_movements(business_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_product ON stock_movements(business_id, product_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_actor ON stock_movements(business_id, actor_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_type ON stock_movements(business_id, movement_type);
CREATE INDEX IF NOT EXISTS idx_stock_movements_created ON stock_movements(business_id, created_at);
CREATE INDEX IF NOT EXISTS idx_stock_movements_synced ON stock_movements(business_id, synced);
CREATE INDEX IF NOT EXISTS idx_stock_movements_reference ON stock_movements(business_id, reference_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_server_seq ON stock_movements(business_id, server_seq);

-- Sale sessions table (replaces sale_headers)
CREATE TABLE IF NOT EXISTS sale_sessions (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    receipt_number TEXT NOT NULL,
    device_id TEXT NOT NULL,
    device_code TEXT NOT NULL,
    salesperson_id TEXT NOT NULL,
    salesperson_role TEXT NOT NULL,
    subtotal_cents INTEGER NOT NULL,
    tax_cents INTEGER NOT NULL,
    total_cents INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'completed', -- completed, voided, pending
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    synced INTEGER NOT NULL DEFAULT 0,
    server_seq INTEGER,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sale_sessions_business ON sale_sessions(business_id);
CREATE INDEX IF NOT EXISTS idx_sale_sessions_salesperson ON sale_sessions(business_id, salesperson_id);
CREATE INDEX IF NOT EXISTS idx_sale_sessions_device ON sale_sessions(business_id, device_id);
CREATE INDEX IF NOT EXISTS idx_sale_sessions_created ON sale_sessions(business_id, created_at);
CREATE INDEX IF NOT EXISTS idx_sale_sessions_status ON sale_sessions(business_id, status);
CREATE INDEX IF NOT EXISTS idx_sale_sessions_receipt_number ON sale_sessions(business_id, receipt_number);
CREATE INDEX IF NOT EXISTS idx_sale_sessions_synced ON sale_sessions(business_id, synced);
CREATE INDEX IF NOT EXISTS idx_sale_sessions_server_seq ON sale_sessions(business_id, server_seq);

-- Sale items table (product snapshots at sale time)
CREATE TABLE IF NOT EXISTS sale_items (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    product_id TEXT NOT NULL,
    product_name TEXT NOT NULL,
    category_name TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price_cents INTEGER NOT NULL,
    total_cents INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES sale_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_sale_items_business ON sale_items(business_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_session ON sale_items(business_id, session_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_product ON sale_items(business_id, product_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_created ON sale_items(business_id, created_at);

-- Receipts table
CREATE TABLE IF NOT EXISTS receipts (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    device_code TEXT NOT NULL,
    counter INTEGER NOT NULL,
    receipt_number TEXT NOT NULL,
    format TEXT NOT NULL DEFAULT 'thermal_58mm', -- thermal_58mm, thermal_80mm, pdf_a4, pdf_a5
    content_hash TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES sale_sessions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_receipts_business ON receipts(business_id);
CREATE INDEX IF NOT EXISTS idx_receipts_session ON receipts(business_id, session_id);
CREATE INDEX IF NOT EXISTS idx_receipts_device ON receipts(business_id, device_code);
CREATE INDEX IF NOT EXISTS idx_receipts_counter ON receipts(business_id, device_code, counter);
CREATE INDEX IF NOT EXISTS idx_receipts_created ON receipts(business_id, created_at);
CREATE INDEX IF NOT EXISTS idx_receipts_number ON receipts(business_id, receipt_number);

-- Counters table (per-device sequences)
CREATE TABLE IF NOT EXISTS counters (
    id TEXT PRIMARY KEY NOT NULL, -- business_id:device_code:counter_type
    business_id TEXT NOT NULL,
    device_code TEXT NOT NULL,
    counter_type TEXT NOT NULL, -- receipt, sale_session, stock_movement
    current_value INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL,
    UNIQUE(business_id, device_code, counter_type)
);

CREATE INDEX IF NOT EXISTS idx_counters_business ON counters(business_id);

-- Audit events table
CREATE TABLE IF NOT EXISTS audit_events (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    entity_type TEXT NOT NULL, -- product, sale_session, stock_movement, membership, device, settings
    entity_id TEXT NOT NULL,
    action TEXT NOT NULL, -- created, updated, deleted, voided, received, adjusted, synced, imported, restored
    actor_id TEXT NOT NULL,
    actor_role TEXT NOT NULL,
    device_code TEXT NOT NULL,
    severity TEXT NOT NULL DEFAULT 'info', -- info, warning, error, critical
    before_json TEXT,
    after_json TEXT,
    metadata_json TEXT,
    created_at INTEGER NOT NULL,
    synced INTEGER NOT NULL DEFAULT 0,
    server_seq INTEGER,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_audit_events_business ON audit_events(business_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_entity ON audit_events(business_id, entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_actor ON audit_events(business_id, actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_created ON audit_events(business_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_events_severity ON audit_events(business_id, severity);
CREATE INDEX IF NOT EXISTS idx_audit_events_synced ON audit_events(business_id, synced);
CREATE INDEX IF NOT EXISTS idx_audit_events_server_seq ON audit_events(business_id, server_seq);

-- Membership mirrors table (read-only local copy of server memberships)
CREATE TABLE IF NOT EXISTS membership_mirrors (
    id TEXT PRIMARY KEY NOT NULL, -- business_id:account_id
    business_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    email TEXT NOT NULL,
    role TEXT NOT NULL, -- owner, staff
    status TEXT NOT NULL, -- active, invited, suspended, revoked
    invited_by TEXT,
    invited_at INTEGER,
    accepted_at INTEGER,
    device_code TEXT,
    last_synced_at INTEGER NOT NULL,
    server_version INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_membership_mirrors_business ON membership_mirrors(business_id);
CREATE INDEX IF NOT EXISTS idx_membership_mirrors_account ON membership_mirrors(business_id, account_id);
CREATE INDEX IF NOT EXISTS idx_membership_mirrors_device ON membership_mirrors(business_id, device_code);
CREATE INDEX IF NOT EXISTS idx_membership_mirrors_synced ON membership_mirrors(business_id, last_synced_at);

-- Device registrations table
CREATE TABLE IF NOT EXISTS device_registrations (
    id TEXT PRIMARY KEY NOT NULL, -- device_code
    business_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    device_code TEXT NOT NULL,
    device_name TEXT NOT NULL,
    device_type TEXT NOT NULL, -- android, ios, web
    status TEXT NOT NULL, -- active, pending_approval, revoked, replaced
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

CREATE INDEX IF NOT EXISTS idx_device_registrations_business ON device_registrations(business_id);
CREATE INDEX IF NOT EXISTS idx_device_registrations_account ON device_registrations(business_id, account_id);
CREATE INDEX IF NOT EXISTS idx_device_registrations_code ON device_registrations(business_id, device_code);
CREATE INDEX IF NOT EXISTS idx_device_registrations_status ON device_registrations(business_id, status);

-- Outbox entries table
CREATE TABLE IF NOT EXISTS outbox_entries (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL,
    entity_type TEXT NOT NULL, -- product, sale_session, stock_movement, membership_mirror, device_registration, business_profile, counter
    entity_id TEXT NOT NULL,
    operation TEXT NOT NULL, -- create, update, delete
    payload_json TEXT NOT NULL,
    batch_id TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    synced INTEGER NOT NULL DEFAULT 0,
    synced_at INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_outbox_entries_business ON outbox_entries(business_id);
CREATE INDEX IF NOT EXISTS idx_outbox_entries_entity ON outbox_entries(business_id, entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_outbox_entries_operation ON outbox_entries(business_id, operation);
CREATE INDEX IF NOT EXISTS idx_outbox_entries_created ON outbox_entries(business_id, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_entries_synced ON outbox_entries(business_id, synced);
CREATE INDEX IF NOT EXISTS idx_outbox_entries_batch ON outbox_entries(business_id, batch_id);

-- Sync cursors table
CREATE TABLE IF NOT EXISTS sync_cursors (
    id TEXT PRIMARY KEY NOT NULL, -- business_id:device_code
    business_id TEXT NOT NULL,
    device_code TEXT NOT NULL,
    cursor INTEGER NOT NULL, -- Server-side sequence number
    last_pull_at INTEGER NOT NULL,
    last_push_at INTEGER NOT NULL,
    pending_push_count INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL,
    UNIQUE(business_id, device_code)
);

CREATE INDEX IF NOT EXISTS idx_sync_cursors_business ON sync_cursors(business_id);

-- Sync exceptions table (business-scoped mirror of platform table)
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
CREATE INDEX IF NOT EXISTS idx_sync_exceptions_device ON sync_exceptions(business_id, device_code);
CREATE INDEX IF NOT EXISTS idx_sync_exceptions_status ON sync_exceptions(business_id, status);
CREATE INDEX IF NOT EXISTS idx_sync_exceptions_type ON sync_exceptions(business_id, exception_type);
CREATE INDEX IF NOT EXISTS idx_sync_exceptions_created ON sync_exceptions(business_id, created_at);

-- Business profile table
CREATE TABLE IF NOT EXISTS business_profiles (
    id TEXT PRIMARY KEY NOT NULL,
    business_id TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    phone TEXT,
    country_code TEXT NOT NULL DEFAULT 'KE',
    currency_code TEXT NOT NULL DEFAULT 'KES',
    timezone TEXT NOT NULL DEFAULT 'Africa/Nairobi',
    address TEXT,
    logo_r2_key TEXT,
    negative_stock_policy TEXT NOT NULL DEFAULT 'warn',
    receipt_footer TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted_at INTEGER NOT NULL DEFAULT 0,
    schema_version INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_business_profiles_business ON business_profiles(business_id);
