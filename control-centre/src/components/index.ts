/**
 * Reusable UI components for the SANDE Control Centre SPA.
 * Built with vanilla DOM APIs (no framework) for minimal bundle size.
 */

import { apiClient } from '../api/client.ts';

export function formatCurrency(cents: number): string {
  return (cents / 100).toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD',
  });
}

export function formatDate(ts: number): string {
  return new Date(ts).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatRelative(ts: number): string {
  const now = Date.now();
  const diff = now - ts;
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(ts).toLocaleDateString();
}

export function statusBadge(el: HTMLElement, status: string): void {
  const lower = status.toLowerCase();
  let color = '#6b7280'; // gray
  if (status === 'active' || status === 'completed') color = '#10b981'; // green
  if (status === 'pending' || status === 'pending_approval') color = '#f59e0b'; // amber
  if (status === 'suspended' || status === 'revoked' || status === 'error') color = '#ef4444'; // red
  if (status === 'warning') color = '#f59e0b'; // amber

  el.style.backgroundColor = color;
  el.style.color = '#ffffff';
  el.style.borderRadius = '9999px';
  el.style.padding = '2px 8px';
  el.style.fontSize = '0.75rem';
  el.style.fontWeight = '600';
  el.style.textTransform = 'capitalize';
}

export function createTable(
  container: HTMLElement,
  columns: { key: string; label: string; format?: (val: unknown) => string }[],
  rows: Array<Record<string, unknown>>,
  onRowClick?: (row: Record<string, unknown>) => void
): void {
  container.innerHTML = '';

  const table = document.createElement('table');
  table.style.width = '100%';
  table.style.borderCollapse = 'collapse';

  // Header
  const thead = document.createElement('thead');
  const headerRow = document.createElement('tr');
  columns.forEach((col) => {
    const th = document.createElement('th');
    th.textContent = col.label;
    th.style.textAlign = 'left';
    th.style.padding = '8px 12px';
    th.style.fontWeight = '600';
    th.style.borderBottom = '2px solid #e5e7eb';
    headerRow.appendChild(th);
  });
  thead.appendChild(headerRow);
  table.appendChild(thead);

  // Body
  const tbody = document.createElement('tbody');
  rows.forEach((row) => {
    const tr = document.createElement('tr');
    tr.style.borderBottom = '1px solid #f3f4f6';
    tr.style.cursor = onRowClick ? 'pointer' : 'default';

    columns.forEach((col) => {
      const td = document.createElement('td');
      td.style.padding = '8px 12px';
      td.style.fontSize = '0.875rem';

      let val: string;
      if (col.format) {
        val = col.format(row[col.key]);
      } else {
        val = String(row[col.key] ?? '');
      }

      // Format currency for known money fields
      if (col.key === 'total' || col.key === 'subtotal' || col.key === 'tax' || col.key === 'totalCents' || col.key === 'subtotalCents') {
        td.textContent = formatCurrency(Number(row[col.key]) || 0);
      } else if (col.key === 'created_at' || col.key === 'updated_at' || col.key === 'last_seen_at' || col.key === 'last_sync_at') {
        td.textContent = formatRelative(Number(row[col.key]) || 0);
      } else {
        td.textContent = val;
      }

      tr.appendChild(td);
    });

    if (onRowClick) {
      tr.onclick = () => onRowClick(row);
    }

    tbody.appendChild(tr);
  });
  table.appendChild(tbody);
  container.appendChild(table);
}

export function createStatCard(
  container: HTMLElement,
  title: string,
  value: string,
  subtitle?: string,
  color: string = '#3b82f6'
): void {
  const card = document.createElement('div');
  card.style.backgroundColor = '#ffffff';
  card.style.border = '1px solid #e5e7eb';
  card.style.borderRadius = '0.5rem';
  card.style.padding = '16px';
  card.style.display = 'flex';
  card.style.flexDirection = 'column';

  const colorDiv = document.createElement('div');
  colorDiv.style.width = '4px';
  colorDiv.style.height = '4px';
  colorDiv.style.backgroundColor = color;
  colorDiv.style.marginBottom = '8px';
  card.appendChild(colorDiv);

  const titleEl = document.createElement('div');
  titleEl.textContent = title;
  titleEl.style.fontSize = '0.75rem';
  titleEl.style.fontWeight = '600';
  titleEl.style.color = '#6b7280';
  titleEl.style.textTransform = 'uppercase';
  card.appendChild(titleEl);

  const valueEl = document.createElement('div');
  valueEl.textContent = value;
  valueEl.style.fontSize = '1.875rem';
  valueEl.style.fontWeight = '700';
  valueEl.style.color = '#111827';
  card.appendChild(valueEl);

  if (subtitle) {
    const subEl = document.createElement('div');
    subEl.textContent = subtitle;
    subEl.style.fontSize = '0.75rem';
    subEl.style.color = '#6b7280';
    subEl.style.marginTop = '4px';
    card.appendChild(subEl);
  }

  container.appendChild(card);
}

export function createStatusChip(status: string): HTMLElement {
  const el = document.createElement('span');
  statusBadge(el, status);
  el.textContent = status;
  return el;
}
