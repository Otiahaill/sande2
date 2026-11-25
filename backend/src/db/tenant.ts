import { D1Database, D1PreparedStatement } from '@cloudflare/workers-types';
import { BusinessContext, OutboxEntry, SyncCursor, SyncException, ChangeLogEntry } from '../types';
import { AccountRow, AppVersionRow, BusinessRow, DeviceRow, InvitationRow, MembershipRow, PlatformAdminRow, RecoveryCodeRow, RefreshTokenRow, SaleItemRow, SaleSessionRow, SupportAccessGrantRow } from './rows';



/**
 * Unwraps the rows from a D1 result.
 *
 * `D1PreparedStatement.all()` resolves to a `D1Result`; the rows themselves
 * are in `.results`. Code that wants the rows uses this helper so the
 * distinction is explicit.
 */
export async function rows<T = Record<string, unknown>>(
  result: Promise<D1Result<T>>
): Promise<T[]> {
  return (await result).results;
}


/**
 * Tenant-scoped database access layer.
 * All queries automatically inject business_id from context.
 * This is the ONLY way to access business-scoped D1 tables.
 */
export class TenantDB {
  private db: D1Database;
  private context: BusinessContext;

  constructor(db: D1Database, context: BusinessContext) {
    this.db = db;
    this.context = context;
  }

  // ===== Query Builders =====

  /**
   * Creates a SELECT query with automatic business_id filtering
   */
  select(columns: string, table: string): D1PreparedStatement {
    return this.db.prepare(
      `SELECT ${columns} FROM ${table} WHERE business_id = ?`
    ).bind(this.context.businessId);
  }

  /**
   * Creates an INSERT query with automatic business_id injection
   */
  insert(table: string, columns: string[], values: (string | number | null)[]): D1PreparedStatement {
    const placeholders = columns.map(() => '?').join(', ');
    const colsWithBiz = ['business_id', ...columns];
    const valsWithBiz = [this.context.businessId, ...values];
    return this.db.prepare(
      `INSERT INTO ${table} (${colsWithBiz.join(', ')}) VALUES (${placeholders})`
    ).bind(...valsWithBiz);
  }

  /**
   * Creates an UPDATE query with automatic business_id filtering
   */
  update(table: string, setClause: string, whereClause: string, values: (string | number | null)[]): D1PreparedStatement {
    return this.db.prepare(
      `UPDATE ${table} SET ${setClause} WHERE business_id = ? AND ${whereClause}`
    ).bind(this.context.businessId, ...values);
  }

  /**
   * Creates a DELETE query with automatic business_id filtering
   */
  delete(table: string, whereClause: string, values: (string | number | null)[]): D1PreparedStatement {
    return this.db.prepare(
      `DELETE FROM ${table} WHERE business_id = ? AND ${whereClause}`
    ).bind(this.context.businessId, ...values);
  }

  /**
   * Executes a batch of statements atomically
   */
  async batch(statements: D1PreparedStatement[]): Promise<D1Result[]> {
    return this.db.batch(statements);
  }

  // ===== Business-Scoped Table Access =====

  // Products
  async getProduct(id: string) {
    return this.select('*', 'products').bind(id).first();
  }

  async listProducts(activeOnly = true) {
    let query = this.select('*', 'products');
    if (activeOnly) {
      query = query.bind('is_active = 1 AND deleted_at = 0');
    }
    return rows( query.all());
  }

  async createProduct(data: {
    id: string;
    name: string;
    sku?: string;
    category_id?: string;
    category?: string;
    price_cents: number;
    unit_of_measure?: string;
    conversion_ratio?: number;
    original_price_cents?: number;
  }) {
    const now = Date.now();
    return this.insert('products', [
      'id', 'name', 'sku', 'category_id', 'category',
      'price_cents', 'unit_of_measure', 'conversion_ratio',
      'original_price_cents', 'is_active', 'archived_at', 'deleted_at',
      'created_at', 'updated_at'
    ], [
      data.id, data.name, data.sku ?? null, data.category_id ?? null, data.category ?? null,
      data.price_cents, data.unit_of_measure ?? null, data.conversion_ratio ?? 1,
      data.original_price_cents ?? 0, 1, 0, 0,
      now, now
    ]).run();
  }

  async updateProduct(id: string, data: Partial<{
    name: string;
    sku: string;
    category_id: string;
    category: string;
    price_cents: number;
    unit_of_measure: string;
    conversion_ratio: number;
  }>) {
    const sets: string[] = [];
    const values: (string | number | null)[] = [];
    Object.entries(data).forEach(([key, value]) => {
      if (value !== undefined) {
        sets.push(`${key} = ?`);
        values.push(value);
      }
    });
    if (sets.length === 0) return { success: true };
    sets.push('updated_at = ?');
    values.push(Date.now());
    return this.update('products', sets.join(', '), 'id = ?', [...values, id]).run();
  }

  async archiveProduct(id: string) {
    const now = Date.now();
    return this.update('products', 'is_active = 0, archived_at = ?, updated_at = ?', 'id = ?', [now, now, id]).run();
  }

  async deleteProduct(id: string) {
    const now = Date.now();
    return this.update('products', 'deleted_at = ?, updated_at = ?', 'id = ?', [now, now, id]).run();
  }

  // Stock Movements
  async createStockMovement(data: {
    id: string;
    product_id: string;
    movement_type: string;
    quantity_delta: number;
    unit_price_cents?: number;
    actor_id: string;
    actor_role: string;
    device_code: string;
    reason?: string;
    reference_id?: string;
    reversal_of?: string;
    metadata_json?: string;
  }) {
    const now = Date.now();
    return this.insert('stock_movements', [
      'id', 'product_id', 'movement_type', 'quantity_delta', 'unit_price_cents',
      'actor_id', 'actor_role', 'device_code', 'reason', 'reference_id',
      'reversal_of', 'metadata_json', 'created_at', 'synced'
    ], [
      data.id, data.product_id, data.movement_type, data.quantity_delta, data.unit_price_cents ?? null,
      data.actor_id, data.actor_role, data.device_code, data.reason ?? null, data.reference_id ?? null,
      data.reversal_of ?? null, data.metadata_json ?? null, now, 0
    ]).run();
  }

  async getStockBalance(productId: string): Promise<number> {
    const result = await this.select('COALESCE(SUM(quantity_delta), 0) as balance', 'stock_movements')
      .bind('product_id = ?', productId)
      .first<{ balance: number }>();
    return result?.balance ?? 0;
  }

  async getStockMovements(filters: {
    product_id?: string;
    movement_type?: string;
    actor_id?: string;
    reference_id?: string;
    start_date?: number;
    end_date?: number;
    limit?: number;
    offset?: number;
  }) {
    let query = this.select('*', 'stock_movements');
    const conditions: string[] = [];
    const params: (string | number)[] = [];

    if (filters.product_id) {
      conditions.push('product_id = ?');
      params.push(filters.product_id);
    }
    if (filters.movement_type) {
      conditions.push('movement_type = ?');
      params.push(filters.movement_type);
    }
    if (filters.actor_id) {
      conditions.push('actor_id = ?');
      params.push(filters.actor_id);
    }
    if (filters.reference_id) {
      conditions.push('reference_id = ?');
      params.push(filters.reference_id);
    }
    if (filters.start_date) {
      conditions.push('created_at >= ?');
      params.push(filters.start_date);
    }
    if (filters.end_date) {
      conditions.push('created_at <= ?');
      params.push(filters.end_date);
    }

    if (conditions.length > 0) {
      query = query.bind(...params).bind(conditions.join(' AND '));
    }

    query = query.bind('ORDER BY created_at DESC');

    if (filters.limit) {
      query = query.bind(`LIMIT ${filters.limit}`);
    }
    if (filters.offset) {
      query = query.bind(`OFFSET ${filters.offset}`);
    }

    return rows( query.all());
  }

  // Sale Sessions
  async createSaleSession(data: {
    id: string;
    receipt_number: string;
    device_id: string;
    device_code: string;
    salesperson_id: string;
    salesperson_role: string;
    subtotal_cents: number;
    tax_cents: number;
    total_cents: number;
    status?: string;
  }) {
    const now = Date.now();
    return this.insert('sale_sessions', [
      'id', 'receipt_number', 'device_id', 'device_code',
      'salesperson_id', 'salesperson_role', 'subtotal_cents', 'tax_cents', 'total_cents',
      'status', 'created_at', 'updated_at', 'synced'
    ], [
      data.id, data.receipt_number, data.device_id, data.device_code,
      data.salesperson_id, data.salesperson_role, data.subtotal_cents, data.tax_cents, data.total_cents,
      data.status ?? 'completed', now, now, 0
    ]).run();
  }

  async getSaleSession(id: string) {
    return this.select('*', 'sale_sessions').bind(id).first();
  }

  async listSaleSessions(filters: {
    status?: string;
    salesperson_id?: string;
    start_date?: number;
    end_date?: number;
    limit?: number;
    offset?: number;
  }) {
    let query = this.select('*', 'sale_sessions');
    const conditions: string[] = [];
    const params: (string | number)[] = [];

    if (filters.status) {
      conditions.push('status = ?');
      params.push(filters.status);
    }
    if (filters.salesperson_id) {
      conditions.push('salesperson_id = ?');
      params.push(filters.salesperson_id);
    }
    if (filters.start_date) {
      conditions.push('created_at >= ?');
      params.push(filters.start_date);
    }
    if (filters.end_date) {
      conditions.push('created_at <= ?');
      params.push(filters.end_date);
    }

    if (conditions.length > 0) {
      query = query.bind(...params).bind(conditions.join(' AND '));
    }

    query = query.bind('ORDER BY created_at DESC');

    if (filters.limit) {
      query = query.bind(`LIMIT ${filters.limit}`);
    }
    if (filters.offset) {
      query = query.bind(`OFFSET ${filters.offset}`);
    }

    return rows( query.all());
  }

  // Sale Items
  async createSaleItems(items: Array<{
    id: string;
    session_id: string;
    product_id: string;
    product_name: string;
    category_name: string;
    quantity: number;
    unit_price_cents: number;
    total_cents: number;
  }>) {
    const now = Date.now();
    const statements = items.map(item =>
      this.insert('sale_items', [
        'id', 'session_id', 'product_id', 'product_name', 'category_name',
        'quantity', 'unit_price_cents', 'total_cents', 'created_at'
      ], [
        item.id, item.session_id, item.product_id, item.product_name, item.category_name,
        item.quantity, item.unit_price_cents, item.total_cents, now
      ])
    );
    return this.batch(statements);
  }

  async getSaleItems(sessionId: string) {
    return rows( this.select('*', 'sale_items').bind('session_id = ?', sessionId).all());
  }

  // Receipts
  async createReceipt(data: {
    id: string;
    session_id: string;
    device_code: string;
    counter: number;
    receipt_number: string;
    format: string;
    content_hash: string;
  }) {
    const now = Date.now();
    return this.insert('receipts', [
      'id', 'session_id', 'device_code', 'counter', 'receipt_number', 'format', 'content_hash', 'created_at'
    ], [
      data.id, data.session_id, data.device_code, data.counter, data.receipt_number, data.format, data.content_hash, now
    ]).run();
  }

  async getReceipt(id: string) {
    return this.select('*', 'receipts').bind(id).first();
  }

  async getReceiptBySession(sessionId: string) {
    return this.select('*', 'receipts').bind('session_id = ?', sessionId).first();
  }

  // Counters
  async getNextCounter(deviceCode: string, counterType: string): Promise<number> {
    const id = `${this.context.businessId}:${deviceCode}:${counterType}`;
    const now = Date.now();

    // Atomic increment
    const result = await this.db.prepare(
      `INSERT INTO counters (id, business_id, device_code, counter_type, current_value, updated_at)
       VALUES (?, ?, ?, ?, 0, ?)
       ON CONFLICT(business_id, device_code, counter_type) DO UPDATE SET
         current_value = current_value + 1,
         updated_at = excluded.updated_at
       RETURNING current_value`
    ).bind(id, this.context.businessId, deviceCode, counterType, now).first<{ current_value: number }>();

    return result?.current_value ?? 1;
  }

  async getCounter(deviceCode: string, counterType: string): Promise<number> {
    const id = `${this.context.businessId}:${deviceCode}:${counterType}`;
    const result = await this.select('current_value', 'counters').bind(id).first<{ current_value: number }>();
    return result?.current_value ?? 0;
  }

  // Audit Events
  async createAuditEvent(data: {
    id: string;
    entity_type: string;
    entity_id: string;
    action: string;
    actor_id: string;
    actor_role: string;
    device_code: string;
    severity?: string;
    before_json?: string;
    after_json?: string;
    metadata_json?: string;
  }) {
    const now = Date.now();
    return this.insert('audit_events', [
      'id', 'entity_type', 'entity_id', 'action', 'actor_id', 'actor_role',
      'device_code', 'severity', 'before_json', 'after_json', 'metadata_json',
      'created_at', 'synced'
    ], [
      data.id, data.entity_type, data.entity_id, data.action, data.actor_id, data.actor_role,
      data.device_code, data.severity ?? 'info', data.before_json ?? null, data.after_json ?? null,
      data.metadata_json ?? null, now, 0
    ]).run();
  }

  async getAuditEvents(filters: {
    entity_type?: string;
    entity_id?: string;
    actor_id?: string;
    severity?: string[];
    start_date?: number;
    end_date?: number;
    limit?: number;
    offset?: number;
  }) {
    let query = this.select('*', 'audit_events');
    const conditions: string[] = [];
    const params: (string | number)[] = [];

    if (filters.entity_type) {
      conditions.push('entity_type = ?');
      params.push(filters.entity_type);
    }
    if (filters.entity_id) {
      conditions.push('entity_id = ?');
      params.push(filters.entity_id);
    }
    if (filters.actor_id) {
      conditions.push('actor_id = ?');
      params.push(filters.actor_id);
    }
    if (filters.severity && filters.severity.length > 0) {
      conditions.push(`severity IN (${filters.severity.map(() => '?').join(', ')})`);
      params.push(...filters.severity);
    }
    if (filters.start_date) {
      conditions.push('created_at >= ?');
      params.push(filters.start_date);
    }
    if (filters.end_date) {
      conditions.push('created_at <= ?');
      params.push(filters.end_date);
    }

    if (conditions.length > 0) {
      query = query.bind(...params).bind(conditions.join(' AND '));
    }

    query = query.bind('ORDER BY created_at DESC');

    if (filters.limit) {
      query = query.bind(`LIMIT ${filters.limit}`);
    }
    if (filters.offset) {
      query = query.bind(`OFFSET ${filters.offset}`);
    }

    return rows( query.all());
  }

  // Outbox Entries
  async createOutboxEntry(entry: Omit<OutboxEntry, 'businessId' | 'createdAt' | 'updatedAt' | 'synced' | 'syncedAt'>) {
    const now = Date.now();
    return this.insert('outbox_entries', [
      'id', 'entity_type', 'entity_id', 'operation', 'payload_json',
      'batch_id', 'attempt_count', 'last_error', 'created_at', 'updated_at', 'synced', 'synced_at'
    ], [
      entry.id, entry.entityType, entry.entityId, entry.operation, entry.payloadJson,
      entry.batchId ?? null, 0, null, now, now, 0, 0
    ]).run();
  }

  async getUnsyncedOutboxEntries(limit = 100) {
    return rows( this.select('*', 'outbox_entries')
      .bind('synced = 0')
      .bind('ORDER BY created_at ASC')
      .bind(`LIMIT ${limit}`)
      .all());
  }

  async getOutboxEntriesByBatch(batchId: string) {
    return rows( this.select('*', 'outbox_entries')
      .bind('batch_id = ?', batchId)
      .all());
  }

  async markOutboxSynced(ids: string[]) {
    if (ids.length === 0) return;
    const placeholders = ids.map(() => '?').join(', ');
    const now = Date.now();
    await this.db.prepare(
      `UPDATE outbox_entries SET synced = 1, synced_at = ?, updated_at = ? WHERE business_id = ? AND id IN (${placeholders})`
    ).bind(now, now, this.context.businessId, ...ids).run();
  }

  async recordOutboxAttempt(id: string, error: string) {
    const now = Date.now();
    return this.update('outbox_entries', 'attempt_count = attempt_count + 1, last_error = ?, updated_at = ?', 'id = ?', [error, now, id]).run();
  }

  // Sync Cursor
  async getSyncCursor(deviceCode: string) {
    const id = `${this.context.businessId}:${deviceCode}`;
    return this.select('*', 'sync_cursors').bind(id).first<SyncCursor>();
  }

  async upsertSyncCursor(cursor: Omit<SyncCursor, 'businessId' | 'id' | 'updatedAt'> & { id?: string }) {
    const id = cursor.id ?? `${this.context.businessId}:${cursor.deviceCode}`;
    const now = Date.now();
    return this.db.prepare(
      `INSERT INTO sync_cursors (id, business_id, device_code, cursor, last_pull_at, last_push_at, pending_push_count, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         cursor = excluded.cursor,
         last_pull_at = excluded.last_pull_at,
         last_push_at = excluded.last_push_at,
         pending_push_count = excluded.pending_push_count,
         updated_at = excluded.updated_at`
    ).bind(id, this.context.businessId, cursor.deviceCode, cursor.cursor, cursor.lastPullAt, cursor.lastPushAt, cursor.pendingPushCount, now).run();
  }

  // Sync Exceptions
  async createSyncException(exception: Omit<SyncException, 'businessId' | 'createdAt'>) {
    const now = Date.now();
    return this.insert('sync_exceptions', [
      'id', 'device_code', 'exception_type', 'entity_type', 'entity_id',
      'local_version', 'server_version', 'description', 'local_data_json',
      'server_data_json', 'resolution', 'resolved_by', 'resolved_at', 'status', 'created_at'
    ], [
      exception.id, exception.deviceCode, exception.exceptionType, exception.entityType, exception.entityId,
      exception.localVersion, exception.serverVersion, exception.description, exception.localDataJson,
      exception.serverDataJson ?? null, exception.resolution ?? null, exception.resolvedBy ?? null, exception.resolvedAt ?? 0, exception.status, now
    ]).run();
  }

  async getSyncExceptions(filters: {
    device_code?: string;
    status?: string;
    exception_type?: string;
    limit?: number;
    offset?: number;
  }) {
    let query = this.select('*', 'sync_exceptions');
    const conditions: string[] = [];
    const params: (string | number)[] = [];

    if (filters.device_code) {
      conditions.push('device_code = ?');
      params.push(filters.device_code);
    }
    if (filters.status) {
      conditions.push('status = ?');
      params.push(filters.status);
    }
    if (filters.exception_type) {
      conditions.push('exception_type = ?');
      params.push(filters.exception_type);
    }

    if (conditions.length > 0) {
      query = query.bind(...params).bind(conditions.join(' AND '));
    }

    query = query.bind('ORDER BY created_at DESC');

    if (filters.limit) {
      query = query.bind(`LIMIT ${filters.limit}`);
    }
    if (filters.offset) {
      query = query.bind(`OFFSET ${filters.offset}`);
    }

    return rows( query.all());
  }

  async resolveSyncException(id: string, resolution: 'local_wins' | 'server_wins' | 'manual' | 'pending', resolvedBy: string) {
    const now = Date.now();
    return this.update('sync_exceptions', 'resolution = ?, resolved_by = ?, resolved_at = ?, status = ?', 'id = ?', [resolution, resolvedBy, now, 'resolved', id]).run();
  }

  // Change Log
  async createChangeLog(entry: Omit<ChangeLogEntry, 'businessId' | 'serverSeq' | 'createdAt'>) {
    // Get next server sequence
    const seqResult = await this.db.prepare(
      `SELECT COALESCE(MAX(server_seq), 0) + 1 as next_seq FROM change_log WHERE business_id = ?`
    ).bind(this.context.businessId).first<{ next_seq: number }>();
    const serverSeq = seqResult?.next_seq ?? 1;

    const now = Date.now();
    return this.insert('change_log', [
      'id', 'entity_type', 'entity_id', 'operation', 'actor_id', 'actor_role',
      'device_code', 'before_json', 'after_json', 'metadata_json', 'server_seq', 'created_at'
    ], [
      entry.id, entry.entityType, entry.entityId, entry.operation, entry.actorId, entry.actorRole,
      entry.deviceCode, entry.beforeJson ?? null, entry.afterJson ?? null, entry.metadataJson ?? null, serverSeq, now
    ]).run();
  }

  async getChangeLog(filters: {
    entity_type?: string;
    entity_id?: string;
    actor_id?: string;
    start_seq?: number;
    end_seq?: number;
    limit?: number;
  }) {
    let query = this.select('*', 'change_log');
    const conditions: string[] = [];
    const params: (string | number)[] = [];

    if (filters.entity_type) {
      conditions.push('entity_type = ?');
      params.push(filters.entity_type);
    }
    if (filters.entity_id) {
      conditions.push('entity_id = ?');
      params.push(filters.entity_id);
    }
    if (filters.actor_id) {
      conditions.push('actor_id = ?');
      params.push(filters.actor_id);
    }
    if (filters.start_seq) {
      conditions.push('server_seq >= ?');
      params.push(filters.start_seq);
    }
    if (filters.end_seq) {
      conditions.push('server_seq <= ?');
      params.push(filters.end_seq);
    }

    if (conditions.length > 0) {
      query = query.bind(...params).bind(conditions.join(' AND '));
    }

    query = query.bind('ORDER BY server_seq ASC');

    if (filters.limit) {
      query = query.bind(`LIMIT ${filters.limit}`);
    }

    return rows( query.all());
  }

  // Reports
  async getSalesSummary(startDate: number, endDate: number) {
    const result = await this.select(
      `COUNT(*) as transaction_count,
       COALESCE(SUM(total_cents), 0) as total_sales,
       COALESCE(SUM(tax_cents), 0) as total_tax`,
      'sale_sessions'
    ).bind('status = ? AND created_at BETWEEN ? AND ?', 'completed', startDate, endDate).first();
    return result;
  }

  async getTopProducts(startDate: number, endDate: number, limit = 10) {
    return rows<SaleItemRow>( this.db.prepare(`
      SELECT si.product_id, si.product_name, SUM(si.quantity) as quantity_sold, SUM(si.total_cents) as revenue
      FROM sale_items si
      JOIN sale_sessions ss ON si.session_id = ss.id
      WHERE ss.business_id = ? AND ss.status = 'completed' AND ss.created_at BETWEEN ? AND ?
      GROUP BY si.product_id, si.product_name
      ORDER BY revenue DESC
      LIMIT ?
    `).bind(this.context.businessId, startDate, endDate, limit).all<SaleItemRow>());
  }

  async getSalesByHour(startDate: number, endDate: number) {
    return rows<SaleSessionRow>( this.db.prepare(`
      SELECT
        CAST(strftime('%H', datetime(created_at / 1000, 'unixepoch', '${this.getTimezone()}')) AS INTEGER) as hour,
        COUNT(*) as count,
        COALESCE(SUM(total_cents), 0) as total
      FROM sale_sessions
      WHERE business_id = ? AND status = 'completed' AND created_at BETWEEN ? AND ?
      GROUP BY hour
      ORDER BY hour
    `).bind(this.context.businessId, startDate, endDate).all<SaleSessionRow>());
  }

  async getSalesByCashier(startDate: number, endDate: number) {
    return rows<SaleSessionRow>( this.db.prepare(`
      SELECT salesperson_id, salesperson_role, COUNT(*) as count, COALESCE(SUM(total_cents), 0) as total
      FROM sale_sessions
      WHERE business_id = ? AND status = 'completed' AND created_at BETWEEN ? AND ?
      GROUP BY salesperson_id, salesperson_role
      ORDER BY total DESC
    `).bind(this.context.businessId, startDate, endDate).all<SaleSessionRow>());
  }

  private getTimezone(): string {
    // This would ideally come from business profile
    return 'Africa/Nairobi';
  }
}

/**
 * Creates a tenant-scoped database instance
 */
export function createTenantDB(db: D1Database, context: BusinessContext): TenantDB {
  return new TenantDB(db, context);
}

/**
 * Platform database (non-tenant-scoped) for admin operations
 */
export class PlatformDB {
  private db: D1Database;

  constructor(db: D1Database) {
    this.db = db;
  }

  // Business management
  async createBusiness(data: {
    id: string;
    name: string;
    category: string;
    phone?: string;
    country_code: string;
    currency_code: string;
    timezone: string;
    address?: string;
  }) {
    const now = Date.now();
    return this.db.prepare(
      `INSERT INTO businesses (id, name, category, phone, country_code, currency_code, timezone, address, status, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'active', ?, ?)`
    ).bind(data.id, data.name, data.category, data.phone ?? null, data.country_code, data.currency_code, data.timezone, data.address ?? null, now, now).run();
  }

  async getBusiness(id: string) {
    return this.db.prepare('SELECT * FROM businesses WHERE id = ?').bind(id).first<BusinessRow>();
  }

  async listBusinesses(status?: string) {
    let query = this.db.prepare('SELECT * FROM businesses');
    if (status) {
      query = query.bind('WHERE status = ?', status);
    }
    return rows( query.bind('ORDER BY created_at DESC').all());
  }

  async updateBusinessStatus(id: string, status: string) {
    const now = Date.now();
    return this.db.prepare('UPDATE businesses SET status = ?, updated_at = ? WHERE id = ?').bind(status, now, id).run();
  }

  // Account management
  async getAccountByEmail(email: string) {
    return this.db.prepare('SELECT * FROM accounts WHERE email = ?').bind(email).first<AccountRow>();
  }

  async getAccountByGoogleSub(sub: string) {
    return this.db.prepare('SELECT * FROM accounts WHERE google_sub = ?').bind(sub).first<AccountRow>();
  }

  async createAccount(data: { id: string; email: string; display_name?: string; photo_url?: string; google_sub: string }) {
    const now = Date.now();
    return this.db.prepare(
      `INSERT INTO accounts (id, email, display_name, photo_url, google_sub, email_verified, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, 1, ?, ?)`
    ).bind(data.id, data.email, data.display_name ?? null, data.photo_url ?? null, data.google_sub, now, now).run();
  }

  // Membership management
  async createMembership(data: {
    id: string;
    business_id: string;
    account_id: string;
    role: string;
    status: string;
    invited_by?: string;
  }) {
    const now = Date.now();
    return this.db.prepare(
      `INSERT INTO memberships (id, business_id, account_id, role, status, invited_by, invited_at, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).bind(data.id, data.business_id, data.account_id, data.role, data.status, data.invited_by ?? null, data.invited_by ? now : null, now, now).run();
  }

  async getMembership(businessId: string, accountId: string) {
    return this.db.prepare('SELECT * FROM memberships WHERE business_id = ? AND account_id = ?').bind(businessId, accountId).first<MembershipRow>();
  }

  async getMembershipsByBusiness(businessId: string) {
    return rows<MembershipRow>( this.db.prepare('SELECT * FROM memberships WHERE business_id = ?').bind(businessId).all<MembershipRow>());
  }

  async getMembershipsByAccount(accountId: string) {
    return rows<MembershipRow>( this.db.prepare('SELECT * FROM memberships WHERE account_id = ?').bind(accountId).all<MembershipRow>());
  }

  async updateMembershipStatus(businessId: string, accountId: string, status: string) {
    const now = Date.now();
    return this.db.prepare('UPDATE memberships SET status = ?, updated_at = ? WHERE business_id = ? AND account_id = ?').bind(status, now, businessId, accountId).run();
  }

  // Device management
  async createDevice(data: {
    id: string;
    business_id: string;
    account_id: string;
    device_code: string;
    device_name: string;
    device_type: string;
    status: string;
  }) {
    const now = Date.now();
    return this.db.prepare(
      `INSERT INTO devices (id, business_id, account_id, device_code, device_name, device_type, status, last_seen_at, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).bind(data.id, data.business_id, data.account_id, data.device_code, data.device_name, data.device_type, data.status, now, now, now).run();
  }

  async getDevice(deviceCode: string) {
    return this.db.prepare('SELECT * FROM devices WHERE device_code = ?').bind(deviceCode).first<DeviceRow>();
  }

  async getDevicesByBusiness(businessId: string) {
    return rows<DeviceRow>( this.db.prepare('SELECT * FROM devices WHERE business_id = ?').bind(businessId).all<DeviceRow>());
  }

  async getActiveDeviceCount(businessId: string) {
    const result = await this.db.prepare('SELECT COUNT(*) as count FROM devices WHERE business_id = ? AND status = ?').bind(businessId, 'active').first<{ count: number }>();
    return result?.count ?? 0;
  }

  async getActiveDeviceCountByAccount(accountId: string) {
    const result = await this.db.prepare('SELECT COUNT(*) as count FROM devices WHERE account_id = ? AND status = ?').bind(accountId, 'active').first<{ count: number }>();
    return result?.count ?? 0;
  }

  async updateDeviceStatus(deviceCode: string, status: string, approvedBy?: string) {
    const now = Date.now();
    if (status === 'active' && approvedBy) {
      return this.db.prepare('UPDATE devices SET status = ?, approved_by = ?, approved_at = ?, updated_at = ? WHERE device_code = ?').bind(status, approvedBy, now, now, deviceCode).run();
    }
    if (status === 'revoked') {
      return this.db.prepare('UPDATE devices SET status = ?, revoked_at = ?, updated_at = ? WHERE device_code = ?').bind(status, now, now, deviceCode).run();
    }
    return this.db.prepare('UPDATE devices SET status = ?, updated_at = ? WHERE device_code = ?').bind(status, now, deviceCode).run();
  }

  // Invitations
  async createInvitation(data: {
    id: string;
    business_id: string;
    email: string;
    role: string;
    invited_by: string;
    code_hash: string;
    expires_at: number;
  }) {
    const now = Date.now();
    return this.db.prepare(
      `INSERT INTO invitations (id, business_id, email, role, invited_by, code_hash, expires_at, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
    ).bind(data.id, data.business_id, data.email, data.role, data.invited_by, data.code_hash, data.expires_at, now).run();
  }

  async getInvitationByCodeHash(codeHash: string) {
    return this.db.prepare('SELECT * FROM invitations WHERE code_hash = ? AND expires_at > ?').bind(codeHash, Date.now()).first<InvitationRow>();
  }

  async acceptInvitation(id: string, acceptedBy: string) {
    const now = Date.now();
    return this.db.prepare('UPDATE invitations SET accepted_at = ?, accepted_by = ? WHERE id = ?').bind(now, acceptedBy, id).run();
  }

  // Refresh tokens
  async createRefreshToken(data: {
    id: string;
    account_id: string;
    device_id: string;
    token_hash: string;
    expires_at: number;
  }) {
    const now = Date.now();
    return this.db.prepare(
      `INSERT INTO refresh_tokens (id, account_id, device_id, token_hash, expires_at, created_at)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(data.id, data.account_id, data.device_id, data.token_hash, data.expires_at, now).run();
  }

  async getRefreshToken(tokenHash: string) {
    return this.db.prepare('SELECT * FROM refresh_tokens WHERE token_hash = ? AND expires_at > ? AND revoked_at IS NULL').bind(tokenHash, Date.now()).first<RefreshTokenRow>();
  }

  async revokeRefreshToken(tokenHash: string, replacedBy?: string) {
    const now = Date.now();
    return this.db.prepare('UPDATE refresh_tokens SET revoked_at = ?, replaced_by = ? WHERE token_hash = ?').bind(now, replacedBy ?? null, tokenHash).run();
  }

  // Recovery codes
  async createRecoveryCodes(businessId: string, accountId: string, codeHashes: string[]) {
    const now = Date.now();
    const statements = codeHashes.map(hash =>
      this.db.prepare(
        `INSERT INTO recovery_codes (id, business_id, account_id, code_hash, created_at)
         VALUES (?, ?, ?, ?, ?)`
      ).bind(crypto.randomUUID(), businessId, accountId, hash, now)
    );
    return this.db.batch(statements);
  }

  async verifyRecoveryCode(businessId: string, accountId: string, codeHash: string) {
    return this.db.prepare(
      `SELECT * FROM recovery_codes WHERE business_id = ? AND account_id = ? AND code_hash = ? AND used_at IS NULL`
    ).bind(businessId, accountId, codeHash).first<RecoveryCodeRow>();
  }

  async useRecoveryCode(id: string) {
    const now = Date.now();
    return this.db.prepare('UPDATE recovery_codes SET used_at = ? WHERE id = ?').bind(now, id).run();
  }

  // Sync batches
  async createSyncBatch(data: {
    id: string;
    business_id: string;
    device_code: string;
    direction: 'push' | 'pull';
  }) {
    const now = Date.now();
    return this.db.prepare(
      `INSERT INTO sync_batches (id, business_id, device_code, direction, status, record_count, started_at)
       VALUES (?, ?, ?, ?, 'pending', 0, ?)`
    ).bind(data.id, data.business_id, data.device_code, data.direction, now).run();
  }

  async updateSyncBatch(id: string, updates: {
    status?: string;
    record_count?: number;
    success_count?: number;
    error_count?: number;
    error_summary?: string;
    completed_at?: number;
  }) {
    const sets: string[] = [];
    const values: (string | number | null)[] = [];
    Object.entries(updates).forEach(([key, value]) => {
      if (value !== undefined) {
        sets.push(`${key} = ?`);
        values.push(value);
      }
    });
    if (sets.length === 0) return;
    values.push(id);
    return this.db.prepare(`UPDATE sync_batches SET ${sets.join(', ')} WHERE id = ?`).bind(...values).run();
  }

  // Platform admins
  async getPlatformAdmin(accountId: string) {
    return this.db.prepare('SELECT * FROM platform_admins WHERE account_id = ?').bind(accountId).first<PlatformAdminRow>();
  }

  async isPlatformAdmin(accountId: string): Promise<boolean> {
    const admin = await this.getPlatformAdmin(accountId);
    return !!admin;
  }

  // Support access
  async createSupportAccessGrant(data: {
    id: string;
    business_id: string;
    granted_by: string;
    granted_to: string;
    scope: string;
    reason: string;
    ticket_ref?: string;
    expires_at: number;
  }) {
    const now = Date.now();
    return this.db.prepare(
      `INSERT INTO support_access_grants (id, business_id, granted_by, granted_to, scope, reason, ticket_ref, expires_at, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).bind(data.id, data.business_id, data.granted_by, data.granted_to, data.scope, data.reason, data.ticket_ref ?? null, data.expires_at, now).run();
  }

  async getSupportAccessGrants(businessId: string) {
    return rows<SupportAccessGrantRow>( this.db.prepare('SELECT * FROM support_access_grants WHERE business_id = ? AND revoked_at IS NULL AND expires_at > ?').bind(businessId, Date.now()).all<SupportAccessGrantRow>());
  }

  async revokeSupportAccessGrant(id: string, revokedBy: string) {
    const now = Date.now();
    return this.db.prepare('UPDATE support_access_grants SET revoked_at = ?, revoked_by = ? WHERE id = ?').bind(now, revokedBy, id).run();
  }

  // App versions
  async getAppVersions(platform: string) {
    return rows<AppVersionRow>( this.db.prepare('SELECT * FROM app_versions WHERE platform = ? ORDER BY version_code DESC').bind(platform).all<AppVersionRow>());
  }

  async getMinSupportedVersion(platform: string) {
    const result = await this.db.prepare('SELECT min_supported_version_code FROM app_versions WHERE platform = ? ORDER BY version_code ASC LIMIT 1').bind(platform).first<{ min_supported_version_code: number }>();
    return result?.min_supported_version_code ?? 1;
  }
}

export function createPlatformDB(db: D1Database): PlatformDB {
  return new PlatformDB(db);
}
