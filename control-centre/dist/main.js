/**
 * SANDE Control Centre - Main entry point with client-side routing.
 *
 * Routes:
 *   /                 - Dashboard
 *   /businesses       - Business directory
 *   /business/:id     - Business detail (members, devices, sync)
 *   /sync-exceptions  - Sync exception management
 *   /login            - Admin login
 *
 * Auth: Bearer token stored in sessionStorage.
 */
import { apiClient } from '../api/client.ts';
import { renderDashboard } from './pages/Dashboard.ts';
import { renderBusinesses } from './pages/Businesses.ts';
import { renderBusinessDetail } from './pages/BusinessDetail.ts';
import { renderSyncExceptions } from './pages/SyncExceptions.ts';
import { renderAdminLogin } from './pages/Login.ts';
const app = document.getElementById('app');
if (!app) {
    throw new Error('#app element not found in index.html');
}
// Check for existing token
const savedToken = sessionStorage.getItem('sande_admin_token');
if (savedToken) {
    apiClient.setToken(savedToken);
}
async function render() {
    const hash = window.location.hash.slice(1) || '/';
    const path = hash.split('?')[0];
    const params = new URLSearchParams(hash.split('?')[1] || '');
    app.innerHTML = '';
    // Navigation bar (shown on all pages except login)
    if (path !== '/login') {
        const nav = document.createElement('nav');
        nav.style.display = 'flex';
        nav.style.gap = '24px';
        nav.style.padding = '16px 24px';
        nav.style.borderBottom = '1px solid #e5e7eb';
        nav.style.backgroundColor = '#ffffff';
        nav.style.position = 'sticky';
        nav.style.top = '0';
        nav.style.zIndex = '100';
        const navItem = (label, route, active) => {
            const a = document.createElement('a');
            a.textContent = label;
            a.href = `#${route}`;
            a.style.textDecoration = 'none';
            a.style.color = active ? '#111827' : '#6b7280';
            a.style.fontWeight = active ? '700' : '500';
            a.style.fontSize = '0.875rem';
            a.onclick = (e) => {
                e.preventDefault();
                window.location.hash = route;
                render();
            };
            return a;
        };
        nav.appendChild(navItem('Dashboard', '/', path === '/'));
        nav.appendChild(navItem('Businesses', '/businesses', path === '/businesses'));
        nav.appendChild(navItem('Sync Exceptions', '/sync-exceptions', path === '/sync-exceptions'));
        const userEmail = sessionStorage.getItem('sande_admin_email');
        const userLabel = document.createElement('span');
        userLabel.textContent = userEmail ?? 'Admin';
        userLabel.style.fontSize = '0.875rem';
        userLabel.style.color = '#6b7280';
        userLabel.style.marginLeft = 'auto';
        userLabel.style.display = 'flex';
        userLabel.style.alignItems = 'center';
        const logoutBtn = document.createElement('button');
        logoutBtn.textContent = 'Sign Out';
        logoutBtn.style.padding = '6px 12px';
        logoutBtn.style.borderRadius = '0.375rem';
        logoutBtn.style.backgroundColor = '#f3f4f6';
        logoutBtn.style.color = '#374151';
        logoutBtn.style.border = 'none';
        logoutBtn.style.cursor = 'pointer';
        logoutBtn.style.fontSize = '0.875rem';
        logoutBtn.onclick = () => {
            sessionStorage.removeItem('sande_admin_token');
            sessionStorage.removeItem('sande_admin_email');
            apiClient.setToken(null);
            window.location.hash = '/login';
            render();
        };
        const rightGroup = document.createElement('div');
        rightGroup.style.display = 'flex';
        rightGroup.style.alignItems = 'center';
        rightGroup.style.gap = '12px';
        rightGroup.appendChild(userLabel);
        rightGroup.appendChild(logoutBtn);
        nav.appendChild(rightGroup);
        app.appendChild(nav);
    }
    // Route rendering
    try {
        if (path === '/login') {
            if (sessionStorage.getItem('sande_admin_token')) {
                window.location.hash = '/';
                render();
                return;
            }
            renderAdminLogin(app, () => {
                window.location.hash = '/';
                render();
            });
        }
        else if (path === '/') {
            await renderDashboard(app);
        }
        else if (path === '/businesses') {
            await renderBusinesses(app);
        }
        else if (path.startsWith('/business/')) {
            const id = path.split('/').pop();
            await renderBusinessDetail(app);
        }
        else if (path === '/sync-exceptions') {
            await renderSyncExceptions(app);
        }
        else {
            app.innerHTML = '<div style="color:#ef4444;padding:24px">Route not found</div>';
        }
    }
    catch (err) {
        app.innerHTML = `<div style="color:#ef4444;padding:24px">Error: ${err instanceof Error ? err.message : 'Unknown error'}</div>`;
    }
}
// Initial render
render();
// Handle hash changes
window.addEventListener('hashchange', render);
