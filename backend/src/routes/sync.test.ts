import { describe, it, expect, beforeEach, vi } from 'vitest';
import { Hono } from 'hono';
import { authMiddleware } from '../middleware/auth';
import { push } from './sync';

/**
 * Route tests for the sync push endpoint.
 *
 * The most important thing these cover is the payload contract: the app
 * serializes outbox entries to snake_case JSON, and the backend reads exactly
 * those property names. A mismatch here silently drops data on every device,
 * so it is pinned by these tests.
 */

interface RecordedStatement {
  sql: string;
  bound: unknown[];
}

/** A D1 stand-in that records every statement it is asked to run. */
function createMockDB() {
  const statements: RecordedStatement[] = [];

  const prepare = (sql: string) => {
    const record: RecordedStatement = { sql, bound: [] };
    statements.push(record);

    const stmt = {
      bind(...values: unknown[]) {
        record.bound = values;
        return stmt;
      },
      async run() {
        return { success: true, meta: { changes: 1 }, results: [] };
      },
      async first<T>() {
        return null as T | null;
      },
      async all<T>() {
        return { results: [] as T[] };
      },
      async raw() {
        return [];
      },
    };
    return stmt;
  };

  const db = {
    prepare,
    async batch(stmts: unknown[]) {
      return stmts.map(() => ({ success: true, meta: { changes: 1 }, results: [] }));
    },
    async exec() {
      return { success: true };
    },
  } as unknown as D1Database;

  return { db, statements };
}

/**
 * Builds a Hono app with the sync push route behind a stub auth middleware,
 * so the route can be exercised without a real JWT or a real D1.
 *
 * The mocked database is handed to the app as its environment, because the
 * route reads it from `c.env.DB`.
 */
function createApp(db: D1Database, businessContext: unknown) {
  const app = new Hono<{ Bindings: { DB: D1Database }; Variables: { businessContext: any } }>();

  app.use('*', async (c, next) => {
    c.set('businessContext', businessContext);
    await next();
  });
  app.post('/api/sync/push', push);

  return app;
}

const CONTEXT = {
  businessId: 'biz1',
  accountId: 'acct1',
  role: 'owner' as const,
  deviceCode: 'DEV123',
  membershipId: 'mem1',
};

function entry(overrides: Partial<any> = {}) {
  return {
    id: 'ob1',
    businessId: 'biz1',
    entityType: 'product',
    entityId: 'prod1',
    operation: 'create',
    payloadJson: JSON.stringify({
      id: 'prod1',
      name: 'Hammer',
      sku: 'HM-1',
      category_id: 'cat1',
      category: 'Tools',
      price_cents: 15000,
      unit_of_measure: 'piece',
      conversion_ratio: 1,
      original_price_cents: 15000,
      created_at: 1700000000000,
    }),
    attemptCount: 0,
    createdAt: 1700000000000,
    updatedAt: 1700000000000,
    synced: false,
    syncedAt: 0,
    ...overrides,
  };
}

describe('POST /api/sync/push', () => {
  let db: D1Database;
  let statements: RecordedStatement[];

  beforeEach(() => {
    const mock = createMockDB();
    db = mock.db;
    statements = mock.statements;
  });

  it('accepts a well-formed outbox entry', async () => {
    const app = createApp(db, CONTEXT);

    const res = await app.request('/api/sync/push', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ batchId: 'batch1', entries: [entry()] }),
    }, { DB: db });

    expect(res.status).toBe(200);
    const body = (await res.json()) as any;
    expect(body.success).toBe(true);
    expect(body.data.processed).toBe(1);
    expect(body.data.success).toBe(1);
    expect(body.data.errors).toBe(0);
    expect(body.data.batchId).toBe('batch1');
  });

  it('binds the snake_case payload fields the backend expects', async () => {
    const app = createApp(db, CONTEXT);

    await app.request('/api/sync/push', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ batchId: 'batch1', entries: [entry()] }),
    }, { DB: db });

    const productInsert = statements.find((s) =>
      s.sql.includes('INSERT INTO products')
    );
    expect(productInsert).toBeDefined();

    // The SQL selects the columns in this order; the bound values follow.
    const bound = productInsert!.bound;
    const payload = JSON.parse(entry().payloadJson);

    expect(bound[0]).toBe(payload.id);
    expect(bound[1]).toBe('biz1');
    expect(bound[2]).toBe(payload.name);
    expect(bound[3]).toBe(payload.sku);
    expect(bound[4]).toBe(payload.category_id);
    expect(bound[5]).toBe(payload.category);
    expect(bound[6]).toBe(payload.price_cents);
    expect(bound[7]).toBe(payload.unit_of_measure);
    expect(bound[8]).toBe(payload.conversion_ratio);
    expect(bound[9]).toBe(payload.original_price_cents);
    expect(bound[10]).toBe(payload.created_at);
  });

  it('rejects an entry that belongs to another business', async () => {
    const app = createApp(db, CONTEXT);

    const res = await app.request('/api/sync/push', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        batchId: 'batch1',
        entries: [entry({ businessId: 'other-biz' })],
      }),
    }, { DB: db });

    expect(res.status).toBe(200);
    const body = (await res.json()) as any;
    expect(body.data.success).toBe(0);
    expect(body.data.errors).toBe(1);
    expect(body.data.errorDetails?.[0]).toContain('Business ID mismatch');
  });

  it('reports an unknown entity type as an error', async () => {
    const app = createApp(db, CONTEXT);

    const res = await app.request('/api/sync/push', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        batchId: 'batch1',
        entries: [entry({ entityType: 'not_a_real_type' })],
      }),
    }, { DB: db });

    const body = (await res.json()) as any;
    expect(body.data.success).toBe(0);
    expect(body.data.errors).toBe(1);
    expect(body.data.errorDetails?.[0]).toContain('Unknown entity type');
  });

  it('reports a payload that is not valid JSON', async () => {
    const app = createApp(db, CONTEXT);

    const res = await app.request('/api/sync/push', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        batchId: 'batch1',
        // This is what Kotlin's data class toString() produces. The backend
        // must reject it rather than storing a broken row.
        entries: [entry({ payloadJson: 'SaleSession(id=abc, totalCents=100)' })],
      }),
    }, { DB: db });

    const body = (await res.json()) as any;
    expect(body.data.success).toBe(0);
    expect(body.data.errors).toBe(1);
  });

  it('creates a sync batch record for the push', async () => {
    const app = createApp(db, CONTEXT);

    await app.request('/api/sync/push', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ batchId: 'batch1', entries: [entry()] }),
    }, { DB: db });

    const batchInsert = statements.find((s) =>
      s.sql.includes('INSERT INTO sync_batches')
    );
    expect(batchInsert).toBeDefined();
    expect(batchInsert!.bound).toContain('batch1');
  });

  it('writes a change log entry for every applied change', async () => {
    const app = createApp(db, CONTEXT);

    await app.request('/api/sync/push', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ batchId: 'batch1', entries: [entry()] }),
    }, { DB: db });

    const changeLog = statements.find((s) => s.sql.includes('INSERT INTO change_log'));
    expect(changeLog).toBeDefined();
    expect(changeLog!.bound).toContain('product');
    expect(changeLog!.bound).toContain('prod1');
  });

  it('generates a batch id when the client does not send one', async () => {
    const app = createApp(db, CONTEXT);

    const res = await app.request('/api/sync/push', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ entries: [entry()] }),
    }, { DB: db });

    const body = (await res.json()) as any;
    expect(body.data.batchId).toBeTruthy();
    expect(typeof body.data.batchId).toBe('string');
  });

  it('applies a sale_session payload using the snake_case columns', async () => {
    const app = createApp(db, CONTEXT);

    const saleEntry = entry({
      entityType: 'sale_session',
      entityId: 'session1',
      payloadJson: JSON.stringify({
        id: 'session1',
        receipt_number: 'SAL-ABC-00001',
        device_id: 'device1',
        device_code: 'DEV123',
        salesperson_id: 'acct1',
        salesperson_role: 'owner',
        subtotal_cents: 1000,
        tax_cents: 160,
        total_cents: 1160,
        status: 'completed',
        created_at: 1700000000000,
        updated_at: 1700000000000,
      }),
    });

    const res = await app.request('/api/sync/push', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ batchId: 'batch1', entries: [saleEntry] }),
    }, { DB: db });

    const body = (await res.json()) as any;
    expect(body.data.success).toBe(1);

    const insert = statements.find((s) => s.sql.includes('INSERT INTO sale_sessions'));
    expect(insert).toBeDefined();
    const payload = JSON.parse(saleEntry.payloadJson);
    expect(insert!.bound[0]).toBe(payload.id);
    expect(insert!.bound[1]).toBe('biz1');
    expect(insert!.bound[2]).toBe(payload.receipt_number);
    expect(insert!.bound[3]).toBe(payload.device_id);
    expect(insert!.bound[4]).toBe(payload.device_code);
    expect(insert!.bound[7]).toBe(payload.subtotal_cents);
    expect(insert!.bound[8]).toBe(payload.tax_cents);
    expect(insert!.bound[9]).toBe(payload.total_cents);
    expect(insert!.bound[10]).toBe(payload.status);
    expect(insert!.bound[11]).toBe(payload.created_at);
  });

  it('keeps a signed stock movement delta intact', async () => {
    const app = createApp(db, CONTEXT);

    const movementEntry = entry({
      entityType: 'stock_movement',
      entityId: 'sm1',
      payloadJson: JSON.stringify({
        id: 'sm1',
        product_id: 'prod1',
        movement_type: 'SALE',
        quantity_delta: -3,
        unit_price_cents: 250,
        actor_id: 'acct1',
        actor_role: 'owner',
        device_code: 'DEV123',
        reason: 'Sale',
        reference_id: 'session1',
        reversal_of: null,
        metadata_json: null,
        created_at: 1700000000000,
      }),
    });

    const res = await app.request('/api/sync/push', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ batchId: 'batch1', entries: [movementEntry] }),
    }, { DB: db });

    const body = (await res.json()) as any;
    expect(body.data.success).toBe(1);

    const insert = statements.find((s) =>
      s.sql.includes('INSERT INTO stock_movements')
    );
    expect(insert).toBeDefined();
    const payload = JSON.parse(movementEntry.payloadJson);
    expect(insert!.bound[0]).toBe(payload.id);
    expect(insert!.bound[1]).toBe('biz1');
    expect(insert!.bound[2]).toBe(payload.product_id);
    expect(insert!.bound[3]).toBe(payload.movement_type);
    // The negative delta must survive the round trip.
    expect(insert!.bound[4]).toBe(-3);
    expect(insert!.bound[5]).toBe(payload.unit_price_cents);
  });

  it('processes a mixed batch and counts successes and failures separately', async () => {
    const app = createApp(db, CONTEXT);

    const res = await app.request('/api/sync/push', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        batchId: 'batch1',
        entries: [
          entry({ id: 'ob1', entityType: 'product' }),
          entry({ id: 'ob2', entityType: 'not_a_real_type' }),
          entry({ id: 'ob3', entityType: 'sale_session', payloadJson: '{"id":"s3"}' }),
        ],
      }),
    }, { DB: db });

    const body = (await res.json()) as any;
    expect(body.data.processed).toBe(3);
    // The middle entry has an unknown entity type, the others apply.
    expect(body.data.success).toBe(2);
    expect(body.data.errors).toBe(1);
  });
});
