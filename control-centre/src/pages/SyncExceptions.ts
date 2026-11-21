/**
 * SyncExceptions page - Sync exception management for owner review.
 */
import { apiClient } from '../api/client.ts';
import { formatRelative, createTable } from '../components/index.ts';

export function renderSyncExceptions(container: HTMLElement): void {
  container.innerHTML = '';

  const title = document.createElement('h1');
  title.textContent = 'Sync Exceptions';
  title.style.fontSize = '1.5rem';
  title.style.fontWeight = '700';
  title.style.color = '#111827';
  container.appendChild(title);

  const subtitle = document.createElement('p');
  subtitle.textContent = 'Review and resolve synchronisation conflicts that require owner attention.';
  subtitle.style.color = '#6b7280';
  subtitle.style.marginBottom = '16px';
  container.appendChild(subtitle);

  // Filter
  const filterRow = document.createElement('div');
  filterRow.style.display = 'flex';
  filterRow.style.gap = '8px';
  filterRow.style.marginBottom = '16px';
  container.appendChild(filterRow);

  const filterBtn = (label: string, status: string | null) => {
    const btn = document.createElement('button');
    btn.textContent = label;
    btn.style.padding = '6px 12px';
    btn.style.borderRadius = '0.375rem';
    btn.style.border = '1px solid #d1d5db';
    btn.style.backgroundColor = status === null ? '#f3f4f6' : '#ffffff';
    btn.style.color = status === null ? '#1f2937' : '#374151';
    btn.style.fontSize = '0.875rem';
    btn.style.cursor = 'pointer';
    btn.onclick = () => renderExceptions(status);
    return btn;
  };

  filterRow.appendChild(filterBtn('All', null));
  filterRow.appendChild(filterBtn('Open', 'open'));
  filterRow.appendChild(filterBtn('Resolved', 'resolved'));
  filterRow.appendChild(filterBtn('Ignored', 'ignored'));

  const tableContainer = document.createElement('div');
  tableContainer.style.overflowX = 'auto';
  container.appendChild(tableContainer);

  const emptyMsg = document.createElement('div');
  emptyMsg.style.textAlign = 'center';
  emptyMsg.style.color = '#6b7280';
  emptyMsg.style.marginTop = '48px';
  container.appendChild(emptyMsg);

  async function renderExceptions(status: string | null) {
    emptyMsg.textContent = 'Loading exceptions...';
    tableContainer.innerHTML = '';

    try {
      const exceptions = await apiClient.getExceptions('default-business', status);
      emptyMsg.textContent = '';

      if (exceptions.length === 0) {
        emptyMsg.textContent = 'No sync exceptions found.';
        return;
      }

      createTable(tableContainer, [
        { key: 'created_at', label: 'Time', format: (v) => formatRelative(Number(v) || 0) },
        { key: 'exception_type', label: 'Type' },
        { key: 'entity_type', label: 'Entity' },
        { key: 'entity_id', label: 'Entity ID' },
        { key: 'device_code', label: 'Device' },
        { key: 'status', label: 'Status', format: (v) => {
          const el = document.createElement('span');
          const lower = String(v).toLowerCase();
          if (lower === 'open') el.style.color = '#f59e0b';
          if (lower === 'resolved') el.style.color = '#10b981';
          if (lower === 'ignored') el.style.color = '#6b7280';
          el.textContent = String(v).charAt(0).toUpperCase() + String(v).slice(1);
          return el.textContent;
        } },
        { key: 'description', label: 'Description', format: (v) => String(v ?? '').length > 60 ? String(v).slice(0, 60) + '…' : String(v) },
      ], exceptions);
    } catch (err) {
      emptyMsg.textContent = `Failed to load exceptions: ${err instanceof Error ? err.message : 'Unknown error'}`;
    }
  }

  renderExceptions(null);
}
