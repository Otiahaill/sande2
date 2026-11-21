/**
 * Businesses page - List all businesses with status filters and quick actions.
 */
import { apiClient } from '../api/client.ts';
import { formatCurrency, formatRelative, createStatCard, createTable } from '../components/index.ts';

export function renderBusinesses(container: HTMLElement): void {
  container.innerHTML = '';

  const title = document.createElement('h1');
  title.textContent = 'Business Directory';
  title.style.fontSize = '1.5rem';
  title.style.fontWeight = '700';
  title.style.color = '#111827';
  container.appendChild(title);

  const subtitle = document.createElement('p');
  subtitle.textContent = 'View, search, and manage all businesses on the platform.';
  subtitle.style.color = '#6b7280';
  subtitle.style.marginBottom = '16px';
  container.appendChild(subtitle);

  // Filter chips
  const filterRow = document.createElement('div');
  filterRow.style.display = 'flex';
  filterRow.style.gap = '8px';
  filterRow.style.marginBottom = '16px';
  filterRow.style.flexWrap = 'wrap';
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
    btn.onclick = () => renderWithFilter(status);
    return btn;
  };

  filterRow.appendChild(filterBtn('All', null));
  filterRow.appendChild(filterBtn('Active', 'active'));
  filterRow.appendChild(filterBtn('Suspended', 'suspended'));
  filterRow.appendChild(filterBtn('Deleted', 'deleted'));

  // Table container
  const tableContainer = document.createElement('div');
  tableContainer.style.overflowX = 'auto';
  container.appendChild(tableContainer);

  const emptyMsg = document.createElement('div');
  emptyMsg.style.textAlign = 'center';
  emptyMsg.style.color = '#6b7280';
  emptyMsg.style.marginTop = '48px';
  container.appendChild(emptyMsg);

  async function renderWithFilter(status: string | null) {
    emptyMsg.textContent = 'Loading businesses...';
    tableContainer.innerHTML = '';

    try {
      const businesses = await apiClient.listBusinesses(status);
      emptyMsg.textContent = '';

      if (businesses.length === 0) {
        emptyMsg.textContent = 'No businesses found.';
        return;
      }

      createTable(tableContainer, [
        { key: 'name', label: 'Business Name' },
        { key: 'category', label: 'Category' },
        { key: 'country_code', label: 'Country' },
        { key: 'currency_code', label: 'Currency' },
        { key: 'status', label: 'Status', format: (v) => {
          const el = document.createElement('span');
          const lower = String(v).toLowerCase();
          if (lower === 'active') el.style.color = '#10b981';
          if (lower === 'suspended') el.style.color = '#ef4444';
          if (lower === 'deleted') el.style.color = '#6b7280';
          el.textContent = String(v).charAt(0).toUpperCase() + String(v).slice(1);
          return el.textContent;
        } },
        { key: 'member_count', label: 'Members' },
        { key: 'device_count', label: 'Devices' },
      ], businesses, (row) => {
        // Navigate to business detail
        window.location.hash = `/business/${row.id}`;
      });
    } catch (err) {
      emptyMsg.textContent = `Failed to load businesses: ${err instanceof Error ? err.message : 'Unknown error'}`;
    }
  }

  renderWithFilter(null);
}
