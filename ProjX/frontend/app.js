// API Base URL: relative to current origin, or fallback to localhost:8080 if loaded via file://
const API_BASE = (window.location.protocol === 'file:')
  ? 'http://localhost:8080/api'
  : `${window.location.origin}/api`;

// State
let hubsData = [];
let bikesData = [];
let logsData = [];
let reservationsData = [];

// DOM Elements
const apiStatusDot = document.getElementById('apiStatusDot');
const apiStatusText = document.getElementById('apiStatusText');
const toastEl = document.getElementById('toast');

// Initialize
document.addEventListener('DOMContentLoaded', () => {
  setupTabs();
  setupForms();
  setupHubFinder();
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
  await Promise.all([loadHubs(), loadBikes(), loadLogs(), loadReservations()]);
  updateStats();
  updateReservationBikeSelect();
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

// ----------------- RESERVATIONS & HOLDS (US09) -----------------
async function loadReservations() {
  try {
    const courierIdInput = document.getElementById('reservationCourierId');
    const courierId = courierIdInput ? courierIdInput.value || 1 : 1;
    const res = await fetch(`${API_BASE}/reservations?courierId=${courierId}`);
    if (res.ok) {
      reservationsData = await res.json();
    } else {
      reservationsData = [];
    }
    renderReservationsTable(reservationsData);
  } catch (err) {
    console.error('Error loading reservations:', err);
  }
}

function renderReservationsTable(reservations) {
  const tbody = document.getElementById('reservationsTableBody');
  if (!tbody) return;

  if (!reservations || reservations.length === 0) {
    tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">No reservations placed yet.</td></tr>';
    return;
  }

  tbody.innerHTML = reservations.map(r => {
    let statusClass = 'badge-neutral';
    if (r.status === 'RESERVED') statusClass = 'badge-warning';
    else if (r.status === 'IN_USE') statusClass = 'badge-info';
    else if (r.status === 'COMPLETED') statusClass = 'badge-success';
    else if (r.status === 'EXPIRED') statusClass = 'badge-danger';
    else if (r.status === 'CANCELLED') statusClass = 'badge-danger';

    const bike = bikesData.find(b => b.id === r.bikeId);
    const bikeLabel = bike ? `${escapeHtml(bike.serialNumber)} (${escapeHtml(bike.model)})` : `Bike #${r.bikeId}`;

    const startStr = r.plannedStart ? new Date(r.plannedStart).toLocaleTimeString('pt-PT', { hour: '2-digit', minute: '2-digit' }) : '-';
    const endStr = r.plannedEnd ? new Date(r.plannedEnd).toLocaleTimeString('pt-PT', { hour: '2-digit', minute: '2-digit' }) : '-';

    let actionBtn = '-';
    if (r.status === 'RESERVED') {
      actionBtn = `<button class="btn btn-sm btn-outline" style="color: var(--danger); border-color: var(--danger);" onclick="cancelHold(${r.id})">Cancel Hold</button>`;
    }

    return `
      <tr>
        <td><strong>#${r.id}</strong></td>
        <td>${bikeLabel}</td>
        <td>Courier #${r.courierId}</td>
        <td style="font-size: 0.85rem;"><code>${startStr} &rarr; ${endStr}</code></td>
        <td><span class="badge ${statusClass}">${r.status}</span></td>
        <td>${actionBtn}</td>
      </tr>
    `;
  }).join('');
}

function updateReservationBikeSelect() {
  const select = document.getElementById('reservationBikeId');
  if (!select) return;

  const currentVal = select.value;
  const availableBikes = bikesData.filter(b => b.status === 'AVAILABLE');

  select.innerHTML = '<option value="">-- Select an Available E-Bike --</option>' +
    availableBikes.map(b => {
      const hub = hubsData.find(h => h.id === b.hubId);
      const hubName = hub ? hub.name : 'Unknown Hub';
      return `<option value="${b.id}">
        ${escapeHtml(b.serialNumber)} - ${escapeHtml(b.model)} (⚡ ${b.batteryLevel}%, at ${escapeHtml(hubName)})
      </option>`;
    }).join('');

  if (currentVal && availableBikes.some(b => b.id.toString() === currentVal.toString())) {
    select.value = currentVal;
  }
}

async function cancelHold(reservationId) {
  try {
    const res = await fetch(`${API_BASE}/reservations/${reservationId}/cancel`, {
      method: 'POST'
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.detail || 'Failed to cancel hold');
    }
    showToast(`Hold #${reservationId} cancelled. Vehicle returned to availability.`, 'success');
    await refreshAll();
  } catch (err) {
    showToast(err.message, 'error');
  }
}

async function triggerExpireHolds() {
  try {
    const res = await fetch(`${API_BASE}/reservations/expire`, {
      method: 'POST'
    });
    if (!res.ok) throw new Error('Failed to run expiration check');
    const expiredList = await res.json();
    if (expiredList.length > 0) {
      showToast(`${expiredList.length} expired hold(s) processed. Vehicles returned to availability!`, 'info');
    } else {
      showToast('No holds currently expired.', 'info');
    }
    await refreshAll();
  } catch (err) {
    showToast(err.message, 'error');
  }
}

// ----------------- STATS (Calculated from inventory & reservations) -----------------
function updateStats() {
  document.getElementById('statHubCount').textContent = hubsData.length;
  document.getElementById('statBikeCount').textContent = bikesData.length;
  document.getElementById('statAvailableBikes').textContent = bikesData.filter(b => b.status === 'AVAILABLE').length;
  const reservedEl = document.getElementById('statReservedBikes');
  if (reservedEl) {
    reservedEl.textContent = bikesData.filter(b => b.status === 'RESERVED').length;
  }
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

  // Create Reservation Form (US09)
  const reservationForm = document.getElementById('createReservationForm');
  if (reservationForm) {
    reservationForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const btn = document.getElementById('btnSubmitReservation');
      btn.disabled = true;
      btn.textContent = 'Placing Hold...';

      const courierId = parseInt(document.getElementById('reservationCourierId').value, 10);
      const bikeId = parseInt(document.getElementById('reservationBikeId').value, 10);

      try {
        const res = await fetch(`${API_BASE}/reservations`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ courierId, bikeId })
        });

        if (!res.ok) {
          const errorData = await res.json().catch(() => ({}));
          throw new Error(errorData.detail || errorData.message || 'Failed to place reservation hold');
        }

        const data = await res.json();
        showToast(`Vehicle hold #${data.id} placed for 15 minutes! Vehicle availability is guaranteed.`, 'success');
        await refreshAll();
      } catch (err) {
        showToast(err.message, 'error');
      } finally {
        btn.disabled = false;
        btn.textContent = '⏱️ Place Temporal Hold (15 min)';
      }
    });
  }
}

// ----------------- HUB FINDER (US08) -----------------
function setupHubFinder() {
  const searchForm = document.getElementById('hubSearchForm');
  searchForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = document.getElementById('btnSearchHubs');
    btn.disabled = true;
    btn.textContent = 'Searching...';

    const address = document.getElementById('searchAddress').value.trim();
    const radiusKm = document.getElementById('searchRadius').value;
    const params = new URLSearchParams({ address, radiusKm });

    try {
      const res = await fetch(`${API_BASE}/hubs/nearby?${params}`);
      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.detail || errorData.message || 'Failed to search hubs');
      }
      renderNearbyHubs(await res.json());
    } catch (err) {
      showToast(err.message, 'error');
    } finally {
      btn.disabled = false;
      btn.textContent = 'Search Hubs';
    }
  });

  document.getElementById('nearbyHubsTableBody').addEventListener('click', (e) => {
    const btn = e.target.closest('.view-hub-bikes');
    if (btn) loadHubBikes(btn.dataset.hubId, btn.dataset.hubName);
  });
}

function renderNearbyHubs(hubs) {
  const tbody = document.getElementById('nearbyHubsTableBody');
  if (!hubs || hubs.length === 0) {
    tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No hubs found within this radius.</td></tr>';
    return;
  }

  tbody.innerHTML = hubs.map(h => `
    <tr>
      <td>
        <strong>${escapeHtml(h.name)}</strong>
        <div class="text-muted" style="font-size: 0.8rem;">${escapeHtml(h.address)}</div>
      </td>
      <td>${h.distanceKm.toFixed(2)} km</td>
      <td>
        <span class="badge ${h.availableBikes > 0 ? 'badge-success' : 'badge-danger'}">${h.availableBikes}</span>
      </td>
      <td>
        <button class="btn btn-sm btn-outline view-hub-bikes" data-hub-id="${h.id}" data-hub-name="${escapeHtml(h.name)}">View E-Bikes</button>
      </td>
    </tr>
  `).join('');
}

async function loadHubBikes(hubId, hubName) {
  document.getElementById('hubBikesTitle').textContent = `Available E-Bikes at ${hubName}`;
  const tbody = document.getElementById('hubBikesTableBody');
  try {
    const res = await fetch(`${API_BASE}/hubs/${hubId}/bikes`);
    if (!res.ok) {
      const errorData = await res.json().catch(() => ({}));
      throw new Error(errorData.detail || errorData.message || 'Failed to load available e-bikes');
    }
    const bikes = await res.json();
    if (bikes.length === 0) {
      tbody.innerHTML = '<tr><td colspan="3" class="text-center text-muted">No e-bikes can be booked at this hub right now.</td></tr>';
      return;
    }
    tbody.innerHTML = bikes.map(b => `
      <tr>
        <td>
          <strong>${escapeHtml(b.serialNumber)}</strong>
          <div class="text-muted" style="font-size: 0.8rem;">${escapeHtml(b.model)}</div>
        </td>
        <td>${b.cargoCapacityKg} kg</td>
        <td><span style="font-weight: 600; color: var(--primary-dark);">⚡ ${b.batteryLevel}%</span></td>
      </tr>
    `).join('');
  } catch (err) {
    showToast(err.message, 'error');
  }
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
