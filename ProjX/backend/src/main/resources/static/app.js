// API Base URL: relative to current origin, or fallback to localhost:8080 if loaded via file://
const API_BASE = (window.location.protocol === 'file:')
  ? 'http://localhost:8080/api'
  : `${window.location.origin}/api`;

// State
let hubsData = [];
let bikesData = [];
let logsData = [];

// DOM Elements
const apiStatusDot = document.getElementById('apiStatusDot');
const apiStatusText = document.getElementById('apiStatusText');
const toastEl = document.getElementById('toast');

// Initialize
document.addEventListener('DOMContentLoaded', () => {
  setupTabs();
  setupForms();
  checkBackendHealth();
  refreshAll();

  // Polling every 15 seconds to keep inventory fresh
  setInterval(refreshAll, 15000);
});

// Tab navigation
function setupTabs() {
  const tabs = document.querySelectorAll('.tab-btn');
  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      tabs.forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

      tab.classList.add('active');
      const target = document.getElementById(tab.dataset.tab);
      if (target) target.classList.add('active');
    });
  });
}

// Health check using US01 endpoint
async function checkBackendHealth() {
  try {
    const res = await fetch(`${API_BASE}/inventory/logs`);
    if (res.ok) {
      apiStatusDot.className = 'status-indicator online';
      apiStatusText.textContent = 'Backend Online';
    } else {
      throw new Error();
    }
  } catch (err) {
    apiStatusDot.className = 'status-indicator offline';
    apiStatusText.textContent = 'Backend Offline';
  }
}

// Refresh all sections
async function refreshAll() {
  await Promise.all([loadHubs(), loadBikes(), loadLogs()]);
  updateStats();
}

// ----------------- HUBS (US01) -----------------
async function loadHubs() {
  try {
    const res = await fetch(`${API_BASE}/hubs`);
    if (!res.ok) throw new Error('Failed to fetch hubs');
    hubsData = await res.json();
    renderHubsTable(hubsData);
    updateHubSelect(hubsData);
  } catch (err) {
    console.error('Error loading hubs:', err);
  }
}

function renderHubsTable(hubs) {
  const tbody = document.getElementById('hubsTableBody');
  if (!hubs || hubs.length === 0) {
    tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No hubs provisioned yet.</td></tr>';
    return;
  }

  tbody.innerHTML = hubs.map(h => {
    const occupancyPercent = h.capacity > 0 ? Math.round((h.currentBikes / h.capacity) * 100) : 0;
    const isFull = h.availableCapacity <= 0;
    const isLow = h.availableCapacity === 1;

    let badgeClass = 'badge-success';
    if (isFull) badgeClass = 'badge-danger';
    else if (isLow) badgeClass = 'badge-warning';

    return `
      <tr>
        <td>
          <strong>${escapeHtml(h.name)}</strong>
          <div class="text-muted" style="font-size: 0.8rem;">${escapeHtml(h.address)}</div>
        </td>
        <td>
          <code>${h.latitude.toFixed(4)}, ${h.longitude.toFixed(4)}</code>
          <div class="text-muted" style="font-size: 0.78rem;">Radius: ${h.boundaryRadiusMeters}m geofence</div>
        </td>
        <td>
          <strong>${h.capacity}</strong> max
        </td>
        <td>
          <span class="badge ${badgeClass}">${h.currentBikes} / ${h.capacity}</span>
          <div class="progress-bar">
            <div class="progress-fill ${isFull ? 'full' : ''}" style="width: ${Math.min(100, occupancyPercent)}%;"></div>
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

function updateHubSelect(hubs) {
  const select = document.getElementById('bikeHub');
  if (!select) return;

  const currentVal = select.value;
  select.innerHTML = '<option value="">-- No Hub Assigned --</option>' +
    hubs.map(h => {
      const full = h.availableCapacity <= 0;
      return `<option value="${h.id}" ${full ? 'disabled' : ''}>
        ${escapeHtml(h.name)} (${h.currentBikes}/${h.capacity} bikes${full ? ' - FULL' : ''})
      </option>`;
    }).join('');

  if (currentVal) select.value = currentVal;
}

// ----------------- BIKES (US01) -----------------
async function loadBikes() {
  try {
    const res = await fetch(`${API_BASE}/bikes`);
    if (!res.ok) throw new Error('Failed to fetch bikes');
    bikesData = await res.json();
    renderBikesTable(bikesData);
  } catch (err) {
    console.error('Error loading bikes:', err);
  }
}

function renderBikesTable(bikes) {
  const tbody = document.getElementById('bikesTableBody');
  if (!bikes || bikes.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No e-bikes registered yet.</td></tr>';
    return;
  }

  tbody.innerHTML = bikes.map(b => {
    let statusClass = 'badge-neutral';
    if (b.status === 'AVAILABLE') statusClass = 'badge-success';
    else if (b.status === 'IN_USE') statusClass = 'badge-info';
    else if (b.status === 'MAINTENANCE') statusClass = 'badge-danger';
    else if (b.status === 'RESERVED') statusClass = 'badge-warning';

    const hub = hubsData.find(h => h.id === b.hubId);
    const hubName = hub ? hub.name : (b.hubId ? `Hub #${b.hubId}` : 'None');

    return `
      <tr>
        <td>
          <strong>${escapeHtml(b.serialNumber)}</strong>
          <div class="text-muted" style="font-size: 0.8rem;">${escapeHtml(b.model)}</div>
        </td>
        <td>${b.cargoCapacityKg} kg</td>
        <td>
          <span style="font-weight: 600; color: ${b.batteryLevel > 30 ? 'var(--primary-dark)' : 'var(--danger)'}">
            ⚡ ${b.batteryLevel}%
          </span>
        </td>
        <td>
          <span class="badge ${statusClass}">${b.status}</span>
        </td>
        <td>${escapeHtml(hubName)}</td>
      </tr>
    `;
  }).join('');
}

// ----------------- SYSTEM INVENTORY LOGS (US01) -----------------
async function loadLogs() {
  try {
    const type = document.getElementById('logTypeFilter')?.value || '';
    const url = type ? `${API_BASE}/inventory/logs?type=${type}` : `${API_BASE}/inventory/logs`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('Failed to fetch inventory logs');
    logsData = await res.json();
    renderLogsTable(logsData);
  } catch (err) {
    console.error('Error loading inventory logs:', err);
  }
}

function renderLogsTable(logs) {
  const tbody = document.getElementById('logsTableBody');
  if (!logs || logs.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No inventory events recorded yet.</td></tr>';
    return;
  }

  tbody.innerHTML = logs.map(l => {
    const typeBadge = l.assetType === 'HUB' ? 'badge-info' : 'badge-neutral';
    const formattedDate = l.timestamp ? new Date(l.timestamp).toLocaleString('pt-PT') : '-';

    return `
      <tr>
        <td style="white-space: nowrap; font-size: 0.8rem; color: var(--text-muted);">${formattedDate}</td>
        <td><span class="badge ${typeBadge}">${l.assetType}</span></td>
        <td><strong>${escapeHtml(l.assetIdentifier || '-')}</strong></td>
        <td><span class="badge badge-success">${l.action}</span></td>
        <td style="font-size: 0.85rem;">${escapeHtml(l.details || '-')}</td>
      </tr>
    `;
  }).join('');
}

// ----------------- STATS (Calculated from US01 inventory) -----------------
function updateStats() {
  document.getElementById('statHubCount').textContent = hubsData.length;
  document.getElementById('statBikeCount').textContent = bikesData.length;
  document.getElementById('statAvailableBikes').textContent = bikesData.filter(b => b.status === 'AVAILABLE').length;
  document.getElementById('statLogCount').textContent = logsData.length;
}

// ----------------- FORMS -----------------
function setupForms() {
  // Create Hub Form
  const hubForm = document.getElementById('createHubForm');
  hubForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = document.getElementById('btnSubmitHub');
    btn.disabled = true;
    btn.textContent = 'Provisioning...';

    const payload = {
      name: document.getElementById('hubName').value.trim(),
      address: document.getElementById('hubAddress').value.trim(),
      capacity: parseInt(document.getElementById('hubCapacity').value, 10),
      latitude: parseFloat(document.getElementById('hubLat').value) || null,
      longitude: parseFloat(document.getElementById('hubLon').value) || null,
      boundaryRadiusMeters: parseFloat(document.getElementById('hubRadius').value) || 50.0
    };

    try {
      const res = await fetch(`${API_BASE}/hubs`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.detail || errorData.message || 'Failed to provision hub');
      }

      showToast(`Hub '${payload.name}' provisioned successfully!`, 'success');
      hubForm.reset();
      document.getElementById('hubRadius').value = '50';
      document.getElementById('hubCapacity').value = '10';

      await Promise.all([loadHubs(), loadLogs()]);
      updateStats();
    } catch (err) {
      showToast(err.message, 'error');
    } finally {
      btn.disabled = false;
      btn.textContent = '+ Provision Hub';
    }
  });

  // Create Bike Form
  const bikeForm = document.getElementById('createBikeForm');
  bikeForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = document.getElementById('btnSubmitBike');
    btn.disabled = true;
    btn.textContent = 'Registering...';

    const hubVal = document.getElementById('bikeHub').value;
    const payload = {
      serialNumber: document.getElementById('bikeSerial').value.trim(),
      model: document.getElementById('bikeModel').value.trim(),
      cargoCapacityKg: parseFloat(document.getElementById('bikeCargo').value),
      batteryLevel: parseInt(document.getElementById('bikeBattery').value, 10) || 100,
      hubId: hubVal ? parseInt(hubVal, 10) : null
    };

    try {
      const res = await fetch(`${API_BASE}/bikes`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.detail || errorData.message || 'Failed to register e-bike');
      }

      showToast(`E-Bike '${payload.serialNumber}' registered successfully!`, 'success');
      bikeForm.reset();
      document.getElementById('bikeCargo').value = '50.0';
      document.getElementById('bikeBattery').value = '100';

      await Promise.all([loadBikes(), loadHubs(), loadLogs()]);
      updateStats();
    } catch (err) {
      showToast(err.message, 'error');
    } finally {
      btn.disabled = false;
      btn.textContent = '+ Register E-Bike';
    }
  });
}

// ----------------- TOAST -----------------
function showToast(message, type = 'info') {
  toastEl.textContent = message;
  toastEl.className = `toast ${type}`;
  setTimeout(() => {
    toastEl.className = 'toast hidden';
  }, 4000);
}

// Utilities
function escapeHtml(str) {
  if (!str) return '';
  return str.toString()
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
