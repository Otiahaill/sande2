/**
 * Dashboard page - Platform overview with key metrics.
 */
import { apiClient } from '../api/client';
import { formatCurrency, formatDate, formatRelative, createStatCard, createTable } from '../components/index';

export function renderDashboard(container: HTMLElement): void {
  container.innerHTML = '';

  const title = document.createElement('h1');
  title.textContent = 'Platform Dashboard';
  title.style.fontSize = '1.5rem';
  title.style.fontWeight = '700';
  title.style.color = '#111827';
  container.appendChild(title);

  const subtitle = document.createElement('p');
  subtitle.textContent = 'Overview of all businesses, devices, and sync health across the platform.';
  subtitle.style.color = '#6b7280';
  subtitle.style.marginBottom = '24px';
  container.appendChild(subtitle);

  // Stat cards
  const statsRow = document.createElement('div');
  statsRow.style.display = 'flex';
  statsRow.style.gap = '16px';
  statsRow.style.flexWrap = 'wrap';
  statsRow.style.marginBottom = '24px';
  container.appendChild(statsRow);

  // Load data
  Promise.all([
    apiClient.getPlatformStats().catch(() => null),
    apiClient.getPlatformMonitoring().catch(() => null),
  ]).then(([stats, monitoring]) => {
    statsRow.innerHTML = '';
    const s = stats ?? { active_businesses: 0, total_accounts: 0, active_memberships: 0, active_devices: 0, sync_batches_24h: 0, open_exceptions: 0, backups_24h: 0 };

    createStatCard(statsRow, 'Active Businesses', String(s.active_businesses), 'Businesses with at least one active device');
    createStatCard(statsRow, 'Total Accounts', String(s.total_accounts), 'Registered platform accounts');
    createStatCard(statsRow, 'Active Memberships', String(s.active_memberships), 'Owner + staff memberships');
    createStatCard(statsRow, 'Active Devices', String(s.active_devices), 'Devices currently online');
    createStatCard(statsRow, 'Sync Batches (24h)', String(s.sync_batches_24h), 'Successful sync batches');
    createStatCard(statsRow, 'Open Exceptions', String(s.open_exceptions), 'Needs owner attention', '#ef4444');

    // Monitoring section
    let monCard: HTMLDivElement | null = null;
    if (monitoring) {
      const m = monitoring;
      monCard = document.createElement('div');
      monCard.style.backgroundColor = '#ffffff';
      monCard.style.border = '1px solid #e5e7eb';
      monCard.style.borderRadius = '0.5rem';
      monCard.style.padding = '16px';
      monCard.style.marginBottom = '24px';

      const monTitle = document.createElement('h2');
      monTitle.textContent = 'Platform Monitoring';
      monTitle.style.fontSize = '1rem';
      monTitle.style.fontWeight = '600';
      monTitle.style.color = '#111827';
      monTitle.style.marginBottom = '12px';
      monCard.appendChild(monTitle);

      const monGrid = document.createElement('div');
      monGrid.style.display = 'grid';
      monGrid.style.gridTemplateColumns = 'repeat(auto-fit, minmax(200px, 1fr))';
      monGrid.style.gap = '12px';
      monCard.appendChild(monGrid);

      const metrics = [
        ['D1 Storage', m.d1_storage_mb],
        ['D1 Rows Read (24h)', m.d1_rows_read_24h],
        ['D1 Rows Written (24h)', m.d1_rows_written_24h],
        ['R2 Storage', m.r2_storage_gb],
        ['Worker Requests (24h)', m.worker_requests_24h],
        ['Worker Errors (24h)', m.worker_errors_24h],
      ];

      metrics.forEach(([label, val]) => {
        const card = document.createElement('div');
        card.style.backgroundColor = '#f9fafb';
        card.style.borderRadius = '0.375rem';
        card.style.padding = '12px';
        card.style.display = 'flex';
        card.style.flexDirection = 'column';

        const lbl = document.createElement('div');
        lbl.textContent = label;
        lbl.style.fontSize = '0.75rem';
        lbl.style.color = '#6b7280';
        card.appendChild(lbl);

        const valEl = document.createElement('div');
        valEl.textContent = val;
        valEl.style.fontSize = '1.25rem';
        valEl.style.fontWeight = '600';
        valEl.style.color = '#111827';
        card.appendChild(valEl);

        monGrid.appendChild(card);
      });

      container.appendChild(monCard);
    }

    // Recent sync errors
    if (monitoring && monitoring.sync_errors_24h && monitoring.sync_errors_24h.length > 0) {
      const errCard = document.createElement('div');
      errCard.style.backgroundColor = '#fef2f2';
      errCard.style.border = '1px solid #fecaca';
      errCard.style.borderRadius = '0.5rem';
      errCard.style.padding = '16px';
      monCard && container.insertBefore(errCard, container.children[container.children.length - 1] ?? null);

      const errTitle = document.createElement('h2');
      errTitle.textContent = 'Recent Sync Errors (24h)';
      errTitle.style.fontSize = '1rem';
      errTitle.style.fontWeight = '600';
      errTitle.style.color = '#ef4444';
      errTitle.style.marginBottom = '12px';
      errCard.appendChild(errTitle);

      const errList = document.createElement('ul');
      errList.style.listStyle = 'none';
      errList.style.padding = '0';
      monitoring.sync_errors_24h.forEach((e) => {
        const li = document.createElement('li');
        li.style.padding = '8px 12px';
        li.style.borderBottom = '1px solid #fee2e2';
        li.style.display = 'flex';
        li.style.justifyContent = 'space-between';
        li.style.alignItems = 'center';

        const op = document.createElement('span');
        op.textContent = e.operation;
        op.style.fontWeight = '600';
        li.appendChild(op);

        const cnt = document.createElement('span');
        cnt.textContent = String(e.count);
        li.appendChild(cnt);

        errList.appendChild(li);
      });
      errCard.appendChild(errList);

      if (!monCard) {
        container.appendChild(errCard);
      }
    }
  }).catch(() => {
    statsRow.innerHTML = '<div style="color:#ef4444">Failed to load platform stats</div>';
  });

  // Recent audit log section
  const auditSection = document.createElement('div');
  auditSection.style.marginTop = '24px';
  container.appendChild(auditSection);

  const auditTitle = document.createElement('h2');
  auditTitle.textContent = 'Recent Admin Actions';
  auditTitle.style.fontSize = '1rem';
  auditTitle.style.fontWeight = '600';
  auditTitle.style.color = '#111827';
  auditSection.appendChild(auditTitle);

  const auditTable = document.createElement('div');
  auditSection.appendChild(auditTable);

  apiClient.getAuditLog(10).then((entries) => {
    createTable(auditTable, [
        { key: 'created_at', label: 'Time', format: (v) => formatRelative(Number(v) || 0) },
        { key: 'actor_id', label: 'Actor' },
        { key: 'actor_role', label: 'Role' },
        { key: 'operation', label: 'Action' },
        { key: 'business_id', label: 'Business' },
        { key: 'entity_type', label: 'Entity Type' },
        { key: 'entity_id', label: 'Entity ID' },
      ], entries as unknown as Record<string, unknown>[]);
  }).catch(() => {
    auditTable.innerHTML = '<div style="color:#ef4444">Failed to load audit log</div>';
  });
}
