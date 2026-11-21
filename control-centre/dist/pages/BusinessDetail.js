/**
 * BusinessDetail page - Business detail with members, devices, sync health.
 */
import { apiClient } from '../api/client.ts';
import { formatRelative, createStatCard, createTable } from '../components/index.ts';
export function renderBusinessDetail(container) {
    const businessId = window.location.hash.split('/').pop();
    if (!businessId) {
        container.innerHTML = '<div style="color:#ef4444">No business ID</div>';
        return;
    }
    container.innerHTML = '';
    const backBtn = document.createElement('button');
    backBtn.textContent = '← Back to Businesses';
    backBtn.style.padding = '8px 16px';
    backBtn.style.border = '1px solid #d1d5db';
    backBtn.style.borderRadius = '0.375rem';
    backBtn.style.backgroundColor = '#ffffff';
    backBtn.style.cursor = 'pointer';
    backBtn.style.marginBottom = '16px';
    backBtn.onclick = () => {
        window.location.hash = '/businesses';
        renderBusinesses(window.location.hash);
    };
    container.appendChild(backBtn);
    const title = document.createElement('h1');
    title.textContent = 'Loading business...';
    title.style.fontSize = '1.5rem';
    title.style.fontWeight = '700';
    title.style.color = '#111827';
    container.appendChild(title);
    const statusMsg = document.createElement('p');
    statusMsg.style.color = '#6b7280';
    statusMsg.style.marginBottom = '24px';
    container.appendChild(statusMsg);
    const statsRow = document.createElement('div');
    statsRow.style.display = 'flex';
    statsRow.style.gap = '16px';
    statsRow.style.flexWrap = 'wrap';
    statsRow.style.marginBottom = '24px';
    container.appendChild(statsRow);
    const membersSection = document.createElement('div');
    container.appendChild(membersSection);
    const devicesSection = document.createElement('div');
    container.appendChild(devicesSection);
    const syncSection = document.createElement('div');
    container.appendChild(syncSection);
    const actionsSection = document.createElement('div');
    container.appendChild(actionsSection);
    async function load() {
        try {
            const business = await apiClient.getBusiness(businessId);
            title.textContent = business.name;
            // Status badge
            const statusChip = document.createElement('span');
            statusChip.textContent = business.status;
            statusChip.style.padding = '4px 10px';
            statusChip.style.borderRadius = '9999px';
            statusChip.style.fontSize = '0.75rem';
            statusChip.style.fontWeight = '600';
            if (business.status === 'active') {
                statusChip.style.backgroundColor = '#dcfce7';
                statusChip.style.color = '#15803d';
            }
            else if (business.status === 'suspended') {
                statusChip.style.backgroundColor = '#fee2e2';
                statusChip.style.color = '#991b1b';
            }
            else {
                statusChip.style.backgroundColor = '#f3f4f6';
                statusChip.style.color = '#6b7280';
            }
            statusMsg.innerHTML = '';
            statusMsg.appendChild(statusChip);
            // Suspend/reactivate button
            actionsSection.innerHTML = '';
            const actionBtn = document.createElement('button');
            const isSuspended = business.status === 'suspended';
            actionBtn.textContent = isSuspended ? 'Reactivate Business' : 'Suspend Business';
            actionBtn.style.padding = '10px 20px';
            actionBtn.style.borderRadius = '0.375rem';
            actionBtn.style.fontWeight = '600';
            actionBtn.style.cursor = 'pointer';
            actionBtn.onclick = async () => {
                try {
                    await apiClient.updateBusinessStatus(businessId, isSuspended ? 'active' : 'suspended', 'Suspended via Control Centre');
                    actionBtn.textContent = isSuspended ? 'Suspend Business' : 'Reactivate Business';
                    statusChip.textContent = isSuspended ? 'active' : 'suspended';
                    statusChip.style.backgroundColor = isSuspended ? '#dcfce7' : '#fee2e2';
                    statusChip.style.color = isSuspended ? '#15803d' : '#991b1b';
                }
                catch (err) {
                    alert(err instanceof Error ? err.message : 'Failed to update business');
                }
            };
            actionsSection.appendChild(actionBtn);
            // Stats
            statsRow.innerHTML = '';
            createStatCard(statsRow, 'Members', String(business.member_count ?? 0), 'Owner + staff');
            createStatCard(statsRow, 'Devices', String(business.device_count ?? 0), 'Registered devices');
            createStatCard(statsRow, 'Country', business.country_code, 'ISO country code');
            createStatCard(statsRow, 'Currency', business.currency_code, business.currency_code);
            // Members table
            membersSection.innerHTML = '';
            const mTitle = document.createElement('h2');
            mTitle.textContent = 'Members';
            mTitle.style.fontSize = '1rem';
            mTitle.style.fontWeight = '600';
            mTitle.style.color = '#111827';
            mTitle.style.marginBottom = '8px';
            membersSection.appendChild(mTitle);
            const membersTable = document.createElement('div');
            membersSection.appendChild(membersTable);
            const memberships = business.memberships ?? [];
            if (memberships.length === 0) {
                membersTable.innerHTML = '<div style="color:#6b7280; padding:16px">No members</div>';
            }
            else {
                createTable(membersTable, [
                    { key: 'account_email', label: 'Email' },
                    { key: 'account_display_name', label: 'Name', format: (v) => String(v ?? '(no name)') },
                    { key: 'role', label: 'Role' },
                    { key: 'status', label: 'Status', format: (v) => {
                            const el = document.createElement('span');
                            const lower = String(v).toLowerCase();
                            if (lower === 'active')
                                el.style.color = '#10b981';
                            if (lower === 'suspended' || lower === 'revoked')
                                el.style.color = '#ef4444';
                            if (lower === 'invited')
                                el.style.color = '#f59e0b';
                            el.textContent = String(v).charAt(0).toUpperCase() + String(v).slice(1);
                            return el.textContent;
                        } },
                ], memberships);
            }
            // Devices table
            devicesSection.innerHTML = '';
            const dTitle = document.createElement('h2');
            dTitle.textContent = 'Devices';
            dTitle.style.fontSize = '1rem';
            dTitle.style.fontWeight = '600';
            dTitle.style.color = '#111827';
            dTitle.style.marginBottom = '8px';
            devicesSection.appendChild(dTitle);
            const devicesTable = document.createElement('div');
            devicesSection.appendChild(devicesTable);
            const devices = business.devices ?? [];
            if (devices.length === 0) {
                devicesTable.innerHTML = '<div style="color:#6b7280; padding:16px">No devices</div>';
            }
            else {
                createTable(devicesTable, [
                    { key: 'device_name', label: 'Device Name' },
                    { key: 'device_code', label: 'Device Code' },
                    { key: 'device_type', label: 'Type' },
                    { key: 'account_email', label: 'Account' },
                    { key: 'role', label: 'Role' },
                    { key: 'status', label: 'Status', format: (v) => {
                            const el = document.createElement('span');
                            const lower = String(v).toLowerCase();
                            if (lower === 'active')
                                el.style.color = '#10b981';
                            if (lower === 'pending_approval')
                                el.style.color = '#f59e0b';
                            if (lower === 'revoked' || lower === 'replaced')
                                el.style.color = '#ef4444';
                            el.textContent = String(v).charAt(0).toUpperCase() + String(v).slice(1).replace(/_/g, ' ');
                            return el.textContent;
                        } },
                    { key: 'last_seen_at', label: 'Last Seen', format: (v) => formatRelative(Number(v) || 0) },
                ], devices);
            }
            // Sync section
            syncSection.innerHTML = '';
            const sTitle = document.createElement('h2');
            sTitle.textContent = 'Sync Health';
            sTitle.style.fontSize = '1rem';
            sTitle.style.fontWeight = '600';
            sTitle.style.color = '#111827';
            sTitle.style.marginBottom = '8px';
            syncSection.appendChild(sTitle);
            const syncTable = document.createElement('div');
            syncSection.appendChild(syncTable);
            try {
                const syncHealth = await apiClient.getSyncHealth(businessId);
                createTable(syncTable, [
                    { key: 'total_batches_24h', label: 'Sync Batches (24h)' },
                    { key: 'failed_batches_24h', label: 'Failed Batches' },
                    { key: 'success_rate', label: 'Success Rate' },
                    { key: 'open_exceptions', label: 'Open Exceptions' },
                ], [syncHealth]);
                // Device cursors
                if (syncHealth.device_cursors && syncHealth.device_cursors.length > 0) {
                    const cursorSection = document.createElement('div');
                    syncSection.appendChild(cursorSection);
                    const cTitle = document.createElement('h3');
                    cTitle.textContent = 'Device Sync Cursors';
                    cTitle.style.fontSize = '0.875rem';
                    cTitle.style.fontWeight = '600';
                    cTitle.style.color = '#6b7280';
                    cTitle.style.marginTop = '16px';
                    cursorSection.appendChild(cTitle);
                    const cursorTable = document.createElement('div');
                    cursorSection.appendChild(cursorTable);
                    createTable(cursorTable, [
                        { key: 'device_code', label: 'Device' },
                        { key: 'cursor', label: 'Cursor' },
                        { key: 'pending_push_count', label: 'Pending Push' },
                        { key: 'last_sync_at', label: 'Last Sync', format: (v) => formatRelative(Number(v) || 0) },
                    ], syncHealth.device_cursors);
                }
            }
            catch (err) {
                syncTable.innerHTML = '<div style="color:#ef4444">Failed to load sync health</div>';
            }
        }
        catch (err) {
            title.textContent = 'Error loading business';
            statusMsg.textContent = err instanceof Error ? err.message : 'Unknown error';
        }
    }
    load();
}
