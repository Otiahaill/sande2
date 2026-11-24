/**
 * Row shapes for the D1 tables.
 *
 * D1 returns every row as `Record<string, unknown>`. These interfaces
 * describe the actual columns so the query code is type checked.
 *
 * Generated from backend/migrations/*.sql by tools/gen_row_types.py.
 */

export interface AccountRow {
  id: string;
  email: string;
  email_verified: number;
  display_name: string;
  photo_url: string;
  google_sub: string;
  created_at: number;
  updated_at: number;
  deleted_at: number;
}

export interface AppVersionRow {
  id: string;
  version_code: number;
  version_name: string;
  min_supported_version_code: number;
  platform: string;
  release_notes: string;
  download_url: string;
  is_mandatory: number;
  released_at: number;
  created_at: number;
}

export interface AuditEventRow {
  id: string;
  business_id: string;
  entity_type: string;
  entity_id: string;
  action: string;
  actor_id: string;
  actor_role: string;
  device_code: string;
  severity: string;
  before_json: string;
  after_json: string;
  metadata_json: string;
  created_at: number;
  synced: number;
  server_seq: number;
}

export interface BackupRow {
  id: string;
  business_id: string;
  r2_key: string;
  size_bytes: number;
  row_counts_json: string;
  schema_version: number;
  status: string;
  error: string;
  created_at: number;
}

export interface BusinessRow {
  id: string;
  name: string;
  category: string;
  phone: string;
  country_code: string;
  currency_code: string;
  timezone: string;
  address: string;
  logo_r2_key: string;
  negative_stock_policy: string;
  receipt_footer: string;
  status: string;
  schema_version: number;
  created_at: number;
  updated_at: number;
  deleted_at: number;
}

export interface ChangeLogRow {
  id: string;
  business_id: string;
  entity_type: string;
  entity_id: string;
  operation: string;
  actor_id: string;
  actor_role: string;
  device_code: string;
  before_json: string;
  after_json: string;
  metadata_json: string;
  server_seq: number;
  created_at: number;
}

export interface CounterRow {
  id: string;
  business_id: string;
  device_code: string;
  counter_type: string;
  current_value: number;
  updated_at: number;
}

export interface DeviceRow {
  id: string;
  business_id: string;
  account_id: string;
  device_code: string;
  device_name: string;
  device_type: string;
  status: string;
  approved_by: string;
  approved_at: number;
  revoked_at: number;
  revoked_by: string;
  last_seen_at: number;
  last_sync_at: number;
  push_token: string;
  created_at: number;
  updated_at: number;
}

export interface InvitationRow {
  id: string;
  business_id: string;
  email: string;
  role: string;
  invited_by: string;
  code_hash: string;
  expires_at: number;
  accepted_at: number;
  accepted_by: string;
  created_at: number;
}

export interface MembershipRow {
  id: string;
  business_id: string;
  account_id: string;
  role: string;
  status: string;
  invited_by: string;
  invited_at: number;
  accepted_at: number;
  revoked_at: number;
  revoked_by: string;
  device_code: string;
  last_synced_at: number;
  server_version: number;
  created_at: number;
  updated_at: number;
}

export interface OutboxEntryRow {
  id: string;
  business_id: string;
  entity_type: string;
  entity_id: string;
  operation: string;
  payload_json: string;
  batch_id: string;
  attempt_count: number;
  last_error: string;
  created_at: number;
  updated_at: number;
  synced: number;
  synced_at: number;
}

export interface PlatformAdminRow {
  id: string;
  account_id: string;
  role: string;
  permissions_json: string;
  created_at: number;
  created_by: string;
}

export interface ProductCategoryRow {
  id: string;
  business_id: string;
  name: string;
  parent_id: string;
  is_active: number;
  created_at: number;
  updated_at: number;
}

export interface ProductPriceHistoryRow {
  id: string;
  business_id: string;
  product_id: string;
  old_price_cents: number;
  new_price_cents: number;
  changed_by: string;
  reason: string;
  created_at: number;
}

export interface ProductRow {
  id: string;
  business_id: string;
  name: string;
  sku: string;
  category_id: string;
  category: string;
  price_cents: number;
  unit_of_measure: string;
  conversion_ratio: number;
  original_price_cents: number;
  is_active: number;
  archived_at: number;
  deleted_at: number;
  created_at: number;
  updated_at: number;
}

export interface ReceiptRow {
  id: string;
  business_id: string;
  session_id: string;
  device_code: string;
  counter: number;
  receipt_number: string;
  format: string;
  content_hash: string;
  created_at: number;
}

export interface RecoveryCodeRow {
  id: string;
  business_id: string;
  account_id: string;
  code_hash: string;
  used_at: number;
  created_at: number;
}

export interface RefreshTokenRow {
  id: string;
  account_id: string;
  device_id: string;
  token_hash: string;
  expires_at: number;
  revoked_at: number;
  replaced_by: string;
  created_at: number;
}

export interface SaleItemRow {
  id: string;
  business_id: string;
  session_id: string;
  product_id: string;
  product_name: string;
  category_name: string;
  quantity: number;
  unit_price_cents: number;
  total_cents: number;
  created_at: number;
}

export interface SaleSessionRow {
  id: string;
  business_id: string;
  receipt_number: string;
  device_id: string;
  device_code: string;
  salesperson_id: string;
  salesperson_role: string;
  subtotal_cents: number;
  tax_cents: number;
  total_cents: number;
  status: string;
  created_at: number;
  updated_at: number;
  synced: number;
  server_seq: number;
}

export interface StockMovementRow {
  id: string;
  business_id: string;
  product_id: string;
  movement_type: string;
  quantity_delta: number;
  unit_price_cents: number;
  actor_id: string;
  actor_role: string;
  device_code: string;
  reason: string;
  reference_id: string;
  reversal_of: string;
  metadata_json: string;
  created_at: number;
  synced: number;
  server_seq: number;
  running_balance: number;
}

export interface SupportAccessGrantRow {
  id: string;
  business_id: string;
  granted_by: string;
  granted_to: string;
  scope: string;
  reason: string;
  ticket_ref: string;
  expires_at: number;
  revoked_at: number;
  revoked_by: string;
  created_at: number;
}

export interface SyncBatchRow {
  id: string;
  business_id: string;
  device_code: string;
  direction: string;
  status: string;
  record_count: number;
  success_count: number;
  error_count: number;
  error_summary: string;
  started_at: number;
  completed_at: number;
}

export interface SyncCursorRow {
  id: string;
  business_id: string;
  device_code: string;
  cursor: number;
  last_pull_at: number;
  last_push_at: number;
  pending_push_count: number;
  updated_at: number;
}

export interface SyncExceptionRow {
  id: string;
  business_id: string;
  device_code: string;
  exception_type: string;
  entity_type: string;
  entity_id: string;
  local_version: number;
  server_version: number;
  description: string;
  local_data_json: string;
  server_data_json: string;
  resolution: string;
  resolved_by: string;
  resolved_at: number;
  status: string;
  created_at: number;
}
