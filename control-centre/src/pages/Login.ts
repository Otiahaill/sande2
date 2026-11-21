/**
 * Admin login page - Platform admin authentication.
 * Validates against the backend /admin routes.
 */
import { apiClient } from '../api/client';

export function renderAdminLogin(container: HTMLElement, onLogin: () => void): void {
  container.innerHTML = '';

  const card = document.createElement('div');
  card.style.maxWidth = '400px';
  card.style.margin = '48px auto';
  card.style.padding = '32px';
  card.style.backgroundColor = '#ffffff';
  card.style.border = '1px solid #e5e7eb';
  card.style.borderRadius = '0.75rem';
  card.style.boxShadow = '0 1px 3px 0 rgb(0 0 0 / 0.1)';
  container.appendChild(card);

  const logo = document.createElement('div');
  logo.textContent = 'SANDE';
  logo.style.fontSize = '2rem';
  logo.style.fontWeight = '800';
  logo.style.color = '#111827';
  logo.style.textAlign = 'center';
  logo.style.marginBottom = '8px';
  card.appendChild(logo);

  const subtitle = document.createElement('p');
  subtitle.textContent = 'Platform Admin Sign-In';
  subtitle.style.textAlign = 'center';
  subtitle.style.color = '#6b7280';
  subtitle.style.marginBottom = '24px';
  card.appendChild(subtitle);

  // Email
  const emailLabel = document.createElement('label');
  emailLabel.textContent = 'Email';
  emailLabel.style.display = 'block';
  emailLabel.style.fontSize = '0.875rem';
  emailLabel.style.fontWeight = '600';
  emailLabel.style.color = '#374151';
  emailLabel.style.marginBottom = '8px';
  card.appendChild(emailLabel);

  const emailInput = document.createElement('input');
  emailInput.type = 'email';
  emailInput.placeholder = 'admin@sande.example.com';
  emailInput.style.width = '100%';
  emailInput.style.padding = '10px 12px';
  emailInput.style.border = '1px solid #d1d5db';
  emailInput.style.borderRadius = '0.375rem';
  emailInput.style.fontSize = '0.875rem';
  emailInput.style.marginBottom = '16px';
  emailInput.style.boxSizing = 'border-box';
  card.appendChild(emailInput);

  // Password
  const passLabel = document.createElement('label');
  passLabel.textContent = 'Password';
  passLabel.style.display = 'block';
  passLabel.style.fontSize = '0.875rem';
  passLabel.style.fontWeight = '600';
  passLabel.style.color = '#374151';
  passLabel.style.marginBottom = '8px';
  card.appendChild(passLabel);

  const passInput = document.createElement('input');
  passInput.type = 'password';
  passInput.placeholder = '••••••••';
  passInput.style.width = '100%';
  passInput.style.padding = '10px 12px';
  passInput.style.border = '1px solid #d1d5db';
  passInput.style.borderRadius = '0.375rem';
  passInput.style.fontSize = '0.875rem';
  passInput.style.marginBottom = '24px';
  passInput.style.boxSizing = 'border-box';
  card.appendChild(passInput);

  // Error message
  const errorDiv = document.createElement('div');
  errorDiv.style.color = '#ef4444';
  errorDiv.style.fontSize = '0.875rem';
  errorDiv.style.marginBottom = '16px';
  errorDiv.style.display = 'none';
  card.appendChild(errorDiv);

  // Login button
  const loginBtn = document.createElement('button');
  loginBtn.textContent = 'Sign In';
  loginBtn.style.width = '100%';
  loginBtn.style.padding = '10px 16px';
  loginBtn.style.borderRadius = '0.375rem';
  loginBtn.style.backgroundColor = '#111827';
  loginBtn.style.color = '#ffffff';
  loginBtn.style.fontWeight = '600';
  loginBtn.style.fontSize = '0.875rem';
  loginBtn.style.border = 'none';
  loginBtn.style.cursor = 'pointer';
  card.appendChild(loginBtn);

  loginBtn.onclick = async () => {
    loginBtn.disabled = true;
    loginBtn.textContent = 'Signing in...';
    errorDiv.style.display = 'none';

    try {
      // Validate credentials by calling a protected admin endpoint
      // We use the platform stats endpoint as a lightweight auth check
      const token = btoa(`${emailInput.value}:${passInput.value}`);
      apiClient.setToken(token);

      await apiClient.getPlatformStats();

      // Store token in sessionStorage
      sessionStorage.setItem('sande_admin_token', token);
      sessionStorage.setItem('sande_admin_email', emailInput.value);

      onLogin();
    } catch (err) {
      errorDiv.textContent = err instanceof Error ? err.message : 'Invalid email or password';
      errorDiv.style.display = 'block';
      loginBtn.disabled = false;
      loginBtn.textContent = 'Sign In';
    }
  };

  // Allow Enter key
  emailInput.onkeypress = (e) => { if (e.key === 'Enter') passInput.focus(); };
  passInput.onkeypress = (e) => { if (e.key === 'Enter') loginBtn.click(); };

  // Link to Android app
  const note = document.createElement('p');
  note.textContent = 'Need to set up the backend first? Configure the API base URL in the Android app Connection settings.';
  note.style.fontSize = '0.75rem';
  note.style.color = '#9ca3af';
  note.style.textAlign = 'center';
  note.style.marginTop = '24px';
  card.appendChild(note);
}
