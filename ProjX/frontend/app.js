// API base: same origin, or localhost:8080 when opened via file://
const API_BASE = window.location.protocol === 'file:'
  ? 'http://localhost:8080/api'
  : `${window.location.origin}/api`;

const SESSION_KEY = 'ecodelivery.user';
const REFRESH_MS = 15000;

const state = { hubs: [], bikes: [], logs: [], reservations: [] };
let refreshTimer = null;

const $ = (id) => document.getElementById(id);

const BIKE_STATUS = {
  AVAILABLE:   { label: 'Disponível', cls: 'badge-success' },
  RESERVED:    { label: 'Reservada',  cls: 'badge-warning' },
  IN_USE:      { label: 'Em uso',     cls: 'badge-info' },
  MAINTENANCE: { label: 'Manutenção', cls: 'badge-danger' },
  RETIRED:     { label: 'Retirada',   cls: '' }
};

const RESERVATION_STATUS = {
  RESERVED:  { label: 'Reservada', cls: 'badge-warning' },
  IN_USE:    { label: 'Em uso',    cls: 'badge-info' },
  COMPLETED: { label: 'Concluída', cls: 'badge-success' },
  CANCELLED: { label: 'Cancelada', cls: '' },
  EXPIRED:   { label: 'Expirada',  cls: 'badge-danger' }
};

// ================= Bootstrapping & auth =================
document.addEventListener('DOMContentLoaded', () => {
  $('loginForm').addEventListener('submit', onLogin);
  $('logoutBtn').addEventListener('click', logout);

  const user = sessionStorage.getItem(SESSION_KEY);
  if (user) enterApp(user);
});

// Placeholder login: authentication is not implemented on the backend yet.
function onLogin(e) {
  e.preventDefault();
  const email = $('loginEmail').value.trim();
  if (!email || !$('loginPassword').value) return;
  sessionStorage.setItem(SESSION_KEY, email);
  $('loginForm').reset();
  enterApp(email);
}

function logout() {
  sessionStorage.removeItem(SESSION_KEY);
  clearInterval(refreshTimer);
  refreshTimer = null;
  $('appView').classList.add('hidden');
  $('loginView').classList.remove('hidden');
}

let appInitialised = false;
function enterApp(email) {
  $('userEmail').textContent = email;
  $('loginView').classList.add('hidden');
  $('appView').classList.remove('hidden');

  if (!appInitialised) {
    setupNavigation();
    setupForms();
    setupTableActions();
    initHubMap();
    appInitialised = true;
  }

  showView(location.hash.replace('#', '') || 'overview');
  refreshAll();
  refreshTimer = setInterval(refreshAll, REFRESH_MS);
}

// ================= Navigation =================
function setupNavigation() {
  document.querySelectorAll('.nav-item').forEach(btn =>
    btn.addEventListener('click', () => showView(btn.dataset.view)));

  document.querySelectorAll('[data-toggle]').forEach(btn =>
    btn.addEventListener('click', () => $(btn.dataset.toggle).classList.toggle('hidden')));

  document.querySelectorAll('[data-action="refresh"]').forEach(btn =>
    btn.addEventListener('click', refreshAll));

  window.addEventListener('hashchange', () => showView(location.hash.replace('#', '')));
}

function showView(name) {
  if (!$(`view-${name}`)) name = 'overview';
  document.querySelectorAll('.view').forEach(v => v.classList.toggle('active', v.id === `view-${name}`));
  document.querySelectorAll('.nav-item').forEach(n => n.classList.toggle('active', n.dataset.view === name));
  if (location.hash !== `#${name}`) location.hash = name;
  // Leaflet can't measure a hidden container, so recompute its size once visible.
  if (name === 'hubs' && map.instance) {
    map.instance.invalidateSize();
    if (!map.fitted) fitHubMap();
  }
}

// ================= API helpers =================
async function api(path, options = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: options.body ? { 'Content-Type': 'application/json' } : undefined
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.detail || err.message || `Erro ${res.status}`);
  }
  return res.status === 204 ? null : res.json();
}

async function refreshAll() {
  await Promise.all([loadHubs(), loadBikes(), loadLogs(), loadReservations()]);
  renderBikes();          // depends on hubs
  renderReservations();   // depends on bikes
  renderStats();
}

async function safeLoad(fn, label) {
  try { await fn(); } catch (err) { console.error(`Erro a carregar ${label}:`, err); }
}

// ================= Hubs =================
function loadHubs() {
  return safeLoad(async () => {
    state.hubs = await api('/hubs');
    renderHubs();
    renderHubMap();
    renderHubSelect();
  }, 'hubs');
}

function renderHubs() {
  const tbody = $('hubsTableBody');
  if (!state.hubs.length) return setEmpty(tbody, 4, 'Ainda não existem hubs.');

  tbody.innerHTML = state.hubs.map(h => {
    const pct = hubOccupancyPct(h);
    const full = h.availableCapacity <= 0;
    return `
      <tr class="clickable" data-map-hub="${h.id}" title="Ver no mapa">
        <td><strong>${esc(h.name)}</strong><div class="sub">${esc(h.address)}</div></td>
        <td><code>${h.latitude.toFixed(4)}, ${h.longitude.toFixed(4)}</code></td>
        <td>${h.boundaryRadiusMeters} m</td>
        <td>
          ${h.currentBikes} / ${h.capacity}
          <div class="bar"><div class="${full ? 'full' : ''}" style="width:${pct}%"></div></div>
        </td>
      </tr>`;
  }).join('');
}

const hubOccupancyPct = (h) =>
  h.capacity > 0 ? Math.min(100, Math.round((h.currentBikes / h.capacity) * 100)) : 0;

// ================= Hub map (Leaflet + OpenStreetMap) =================
const DEFAULT_CENTER = [40.6405, -8.6538]; // Aveiro
const map = { instance: null, layer: null, markers: new Map(), draft: null, fitted: false };

function initHubMap() {
  if (map.instance || !window.L) return; // Leaflet unavailable (e.g. offline)
  map.instance = L.map('hubMap').setView(DEFAULT_CENTER, 13);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(map.instance);
  map.layer = L.layerGroup().addTo(map.instance);

  // Clicking the map opens the "new hub" form pre-filled with that point.
  map.instance.on('click', (e) => {
    const { lat, lng } = e.latlng;
    $('hubFormCard').classList.remove('hidden');
    $('hubLat').value = lat.toFixed(6);
    $('hubLon').value = lng.toFixed(6);
    $('hubName').focus();
    setDraftMarker(lat, lng);
  });

  // Keep the draft marker in sync with manual lat/lon edits.
  ['hubLat', 'hubLon', 'hubRadius'].forEach(id => $(id).addEventListener('input', () => {
    const lat = numOrNull($('hubLat').value), lon = numOrNull($('hubLon').value);
    if (lat !== null && lon !== null) setDraftMarker(lat, lon);
    else clearDraftMarker();
  }));
  $('hubFormCard').querySelector('button[type="button"]').addEventListener('click', clearDraftMarker);
}

function setDraftMarker(lat, lon) {
  const radius = numOrNull($('hubRadius').value) ?? 50;
  if (!map.draft) {
    map.draft = L.circle([lat, lon], {
      radius, color: '#1d4ed8', weight: 2, dashArray: '4 4', fillOpacity: 0.1
    }).addTo(map.instance);
  } else {
    map.draft.setLatLng([lat, lon]).setRadius(radius);
  }
}

function clearDraftMarker() {
  if (map.draft) { map.draft.remove(); map.draft = null; }
}

function hubColor(h) {
  if (h.availableCapacity <= 0) return '#b42318';
  return hubOccupancyPct(h) >= 80 ? '#9a6700' : '#1f7a4d';
}

function renderHubMap() {
  if (!map.instance) return;
  const openId = [...map.markers].find(([, m]) => m.isPopupOpen())?.[0];
  map.layer.clearLayers();
  map.markers.clear();

  state.hubs.forEach(h => {
    if (h.latitude == null || h.longitude == null) return;
    const color = hubColor(h);
    const pos = [h.latitude, h.longitude];
    L.circle(pos, {
      radius: h.boundaryRadiusMeters, color, weight: 1, fillOpacity: 0.12, interactive: false
    }).addTo(map.layer);
    const marker = L.circleMarker(pos, {
      radius: 8, color: '#fff', weight: 2, fillColor: color, fillOpacity: 1
    }).bindPopup(`
      <strong>${esc(h.name)}</strong><br>
      <span class="sub">${esc(h.address)}</span><br>
      ${h.currentBikes} / ${h.capacity} e-bikes · raio ${h.boundaryRadiusMeters} m
    `).bindTooltip(esc(h.name)).addTo(map.layer);
    map.markers.set(h.id, marker);
  });

  if (openId !== undefined) map.markers.get(openId)?.openPopup();
  if (!map.fitted) fitHubMap();
}

function fitHubMap() {
  if (!map.instance || !map.markers.size) return;
  const bounds = L.latLngBounds([...map.markers.values()].map(m => m.getLatLng()));
  map.instance.fitBounds(bounds, { padding: [40, 40], maxZoom: 16 });
  map.fitted = true;
}

function focusHubOnMap(id) {
  const marker = map.markers.get(id);
  if (!marker) return;
  map.instance.flyTo(marker.getLatLng(), Math.max(map.instance.getZoom(), 16), { duration: 0.6 });
  marker.openPopup();
  $('hubMap').scrollIntoView({ behavior: 'smooth', block: 'center' });
}

function renderHubSelect() {
  const select = $('bikeHub');
  const current = select.value;
  select.innerHTML = '<option value="">Sem hub</option>' + state.hubs.map(h => {
    const full = h.availableCapacity <= 0;
    return `<option value="${h.id}" ${full ? 'disabled' : ''}>${esc(h.name)} (${h.currentBikes}/${h.capacity}${full ? ', cheio' : ''})</option>`;
  }).join('');
  select.value = current;
}

const hubName = (id) => state.hubs.find(h => h.id === id)?.name || (id ? `Hub #${id}` : '—');

// ================= Bikes =================
function loadBikes() {
  return safeLoad(async () => { state.bikes = await api('/bikes'); }, 'e-bikes');
}

function renderBikes() {
  const tbody = $('bikesTableBody');
  const filter = $('bikeStatusFilter').value;
  const bikes = filter ? state.bikes.filter(b => b.status === filter) : state.bikes;
  if (!bikes.length) return setEmpty(tbody, 6, 'Nenhuma e-bike encontrada.');

  tbody.innerHTML = bikes.map(b => {
    const st = BIKE_STATUS[b.status] || { label: b.status, cls: '' };
    let actions = '';
    if (b.status === 'AVAILABLE') {
      actions += `<button class="btn btn-sm" data-bike-status="MAINTENANCE" data-id="${b.id}">Manutenção</button>`;
    } else if (b.status === 'MAINTENANCE') {
      actions += `<button class="btn btn-sm" data-bike-status="AVAILABLE" data-id="${b.id}">Disponibilizar</button>`;
    }
    if (b.status !== 'RETIRED') {
      actions += b.active
        ? ` <button class="btn btn-sm btn-danger" data-bike-active="deactivate" data-id="${b.id}">Desativar</button>`
        : ` <button class="btn btn-sm" data-bike-active="activate" data-id="${b.id}">Ativar</button>`;
    }
    return `
      <tr>
        <td><strong>${esc(b.serialNumber)}</strong><div class="sub">${esc(b.model)}</div></td>
        <td>${b.cargoCapacityKg} kg</td>
        <td class="${b.batteryLevel < 20 ? 'low' : ''}">${b.batteryLevel}%</td>
        <td><span class="badge ${st.cls}">${st.label}</span>${b.active ? '' : ' <span class="badge">Inativa</span>'}</td>
        <td>${esc(hubName(b.hubId))}</td>
        <td class="actions">${actions}</td>
      </tr>`;
  }).join('');
}

function renderReservationBikeSelect() {
  const select = $('reservationBikeId');
  const current = select.value;
  const available = state.bikes.filter(b => b.status === 'AVAILABLE' && b.active);
  select.innerHTML = '<option value="">Selecione…</option>' + available.map(b =>
    `<option value="${b.id}">${esc(b.serialNumber)} · ${b.batteryLevel}% · ${esc(hubName(b.hubId))}</option>`
  ).join('');
  if (available.some(b => String(b.id) === current)) select.value = current;
}

// ================= Reservations =================
async function loadReservations() {
  const courierId = $('reservationCourierId').value || 1;
  try {
    state.reservations = await api(`/reservations?courierId=${encodeURIComponent(courierId)}`);
  } catch {
    state.reservations = []; // e.g. unknown courier
  }
}

function renderReservations() {
  renderReservationBikeSelect();
  const tbody = $('reservationsTableBody');
  if (!state.reservations.length) return setEmpty(tbody, 6, 'Este estafeta não tem reservas.');

  tbody.innerHTML = state.reservations.map(r => {
    const st = RESERVATION_STATUS[r.status] || { label: r.status, cls: '' };
    const bike = state.bikes.find(b => b.id === r.bikeId);
    const actions = r.status === 'RESERVED'
      ? `<button class="btn btn-sm" data-res-start="${r.id}" data-courier="${r.courierId}">Iniciar</button>
         <button class="btn btn-sm btn-danger" data-res-cancel="${r.id}">Cancelar</button>`
      : '';
    return `
      <tr>
        <td>#${r.id}</td>
        <td>${bike ? esc(bike.serialNumber) : `Bike #${r.bikeId}`}</td>
        <td>#${r.courierId}</td>
        <td><code>${fmtTime(r.plannedStart)} – ${fmtTime(r.plannedEnd)}</code></td>
        <td><span class="badge ${st.cls}">${st.label}</span></td>
        <td class="actions">${actions}</td>
      </tr>`;
  }).join('');
}

// ================= Logs =================
function loadLogs() {
  return safeLoad(async () => {
    const type = $('logTypeFilter').value;
    state.logs = await api(type ? `/inventory/logs?type=${type}` : '/inventory/logs');
    renderLogs();
  }, 'logs');
}

function renderLogs() {
  const logRow = (l, withDetails) => `
    <tr>
      <td class="sub">${l.timestamp ? new Date(l.timestamp).toLocaleString('pt-PT') : '—'}</td>
      <td><span class="badge">${l.assetType === 'E_BIKE' ? 'E-Bike' : 'Hub'}</span></td>
      <td><strong>${esc(l.assetIdentifier || '—')}</strong></td>
      <td>${esc(l.action)}</td>
      ${withDetails ? `<td class="sub">${esc(l.details || '—')}</td>` : ''}
    </tr>`;

  const tbody = $('logsTableBody');
  if (!state.logs.length) setEmpty(tbody, 5, 'Sem eventos registados.');
  else tbody.innerHTML = state.logs.map(l => logRow(l, true)).join('');

  const recent = $('recentLogsBody');
  const latest = state.logs.slice(0, 5);
  if (!latest.length) setEmpty(recent, 4, 'Sem atividade recente.');
  else recent.innerHTML = latest.map(l => logRow(l, false)).join('');
}

// ================= Stats =================
function renderStats() {
  const count = (status) => state.bikes.filter(b => b.status === status).length;
  $('statHubCount').textContent = state.hubs.length;
  $('statBikeCount').textContent = state.bikes.length;
  $('statAvailableBikes').textContent = count('AVAILABLE');
  $('statReservedBikes').textContent = count('RESERVED');
  $('statMaintenanceBikes').textContent = count('MAINTENANCE');
}

// ================= Forms =================
function setupForms() {
  handleForm('createHubForm', async () => {
    const payload = {
      name: $('hubName').value.trim(),
      address: $('hubAddress').value.trim(),
      capacity: parseInt($('hubCapacity').value, 10),
      latitude: numOrNull($('hubLat').value),
      longitude: numOrNull($('hubLon').value),
      boundaryRadiusMeters: numOrNull($('hubRadius').value) ?? 50
    };
    await api('/hubs', { method: 'POST', body: JSON.stringify(payload) });
    toast(`Hub "${payload.name}" criado.`, 'success');
    $('hubFormCard').classList.add('hidden');
    clearDraftMarker();
    map.fitted = false; // re-fit so the new hub is in view
  });

  handleForm('createBikeForm', async () => {
    const hub = $('bikeHub').value;
    const battery = parseInt($('bikeBattery').value, 10);
    const payload = {
      serialNumber: $('bikeSerial').value.trim(),
      model: $('bikeModel').value.trim(),
      cargoCapacityKg: parseFloat($('bikeCargo').value),
      batteryLevel: Number.isNaN(battery) ? 100 : battery,
      hubId: hub ? parseInt(hub, 10) : null
    };
    await api('/bikes', { method: 'POST', body: JSON.stringify(payload) });
    toast(`E-bike "${payload.serialNumber}" registada.`, 'success');
    $('bikeFormCard').classList.add('hidden');
  });

  handleForm('createReservationForm', async () => {
    const payload = {
      courierId: parseInt($('reservationCourierId').value, 10),
      bikeId: parseInt($('reservationBikeId').value, 10)
    };
    const data = await api('/reservations', { method: 'POST', body: JSON.stringify(payload) });
    toast(`Reserva #${data.id} criada. A e-bike fica bloqueada 15 minutos.`, 'success');
  }, { reset: false });

  $('hubSearchForm').addEventListener('submit', searchHubs);

  $('bikeStatusFilter').addEventListener('change', renderBikes);
  $('logTypeFilter').addEventListener('change', loadLogs);
  $('reservationCourierId').addEventListener('change', async () => {
    await loadReservations();
    renderReservations();
  });

  $('expireHoldsBtn').addEventListener('click', () => runAction(async () => {
    const expired = await api('/reservations/expire', { method: 'POST' });
    toast(expired.length ? `${expired.length} reserva(s) expirada(s).` : 'Nenhuma reserva expirada.');
  }));
}

// Wraps a form submission: disables the button, reports errors, refreshes data.
function handleForm(formId, onSubmit, { reset = true } = {}) {
  const form = $(formId);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = form.querySelector('button[type="submit"]');
    btn.disabled = true;
    try {
      await onSubmit();
      if (reset) form.reset();
      await refreshAll();
    } catch (err) {
      toast(err.message, 'error');
    } finally {
      btn.disabled = false;
    }
  });
}

async function runAction(fn) {
  try {
    await fn();
    await refreshAll();
  } catch (err) {
    toast(err.message, 'error');
  }
}

// ================= Table actions (event delegation) =================
function setupTableActions() {
  $('hubsTableBody').addEventListener('click', (e) => {
    const row = e.target.closest('[data-map-hub]');
    if (row) focusHubOnMap(Number(row.dataset.mapHub));
  });

  $('bikesTableBody').addEventListener('click', (e) => {
    const btn = e.target.closest('button');
    if (!btn) return;
    const id = btn.dataset.id;
    if (btn.dataset.bikeStatus) {
      runAction(async () => {
        await api(`/bikes/${id}/status?status=${btn.dataset.bikeStatus}`, { method: 'PATCH' });
        toast('Estado atualizado.', 'success');
      });
    } else if (btn.dataset.bikeActive) {
      runAction(async () => {
        await api(`/operator/bikes/${id}/${btn.dataset.bikeActive}`, { method: 'POST' });
        toast(btn.dataset.bikeActive === 'activate' ? 'E-bike ativada.' : 'E-bike desativada.', 'success');
      });
    }
  });

  $('reservationsTableBody').addEventListener('click', (e) => {
    const btn = e.target.closest('button');
    if (!btn) return;
    if (btn.dataset.resCancel) {
      runAction(async () => {
        await api(`/reservations/${btn.dataset.resCancel}/cancel`, { method: 'POST' });
        toast(`Reserva #${btn.dataset.resCancel} cancelada.`, 'success');
      });
    } else if (btn.dataset.resStart) {
      runAction(async () => {
        await api(`/reservations/${btn.dataset.resStart}/start?courierId=${btn.dataset.courier}`, { method: 'POST' });
        toast(`Reserva #${btn.dataset.resStart} iniciada.`, 'success');
      });
    }
  });

  $('nearbyHubsTableBody').addEventListener('click', (e) => {
    const btn = e.target.closest('[data-hub-id]');
    if (btn) loadHubBikes(btn.dataset.hubId, btn.dataset.hubName);
  });
}

// ================= Hub finder =================
async function searchHubs(e) {
  e.preventDefault();
  const btn = e.target.querySelector('button[type="submit"]');
  btn.disabled = true;
  const params = new URLSearchParams({
    address: $('searchAddress').value.trim(),
    radiusKm: $('searchRadius').value
  });
  try {
    const hubs = await api(`/hubs/nearby?${params}`);
    const tbody = $('nearbyHubsTableBody');
    if (!hubs.length) return setEmpty(tbody, 4, 'Nenhum hub neste raio.');
    tbody.innerHTML = hubs.map(h => `
      <tr>
        <td><strong>${esc(h.name)}</strong><div class="sub">${esc(h.address)}</div></td>
        <td>${h.distanceKm.toFixed(2)} km</td>
        <td><span class="badge ${h.availableBikes > 0 ? 'badge-success' : 'badge-danger'}">${h.availableBikes}</span></td>
        <td class="actions"><button class="btn btn-sm" data-hub-id="${h.id}" data-hub-name="${esc(h.name)}">Ver</button></td>
      </tr>`).join('');
  } catch (err) {
    toast(err.message, 'error');
  } finally {
    btn.disabled = false;
  }
}

async function loadHubBikes(hubId, name) {
  $('hubBikesTitle').textContent = `E-Bikes em ${name}`;
  const tbody = $('hubBikesTableBody');
  try {
    const bikes = await api(`/hubs/${hubId}/bikes`);
    if (!bikes.length) return setEmpty(tbody, 3, 'Sem e-bikes disponíveis neste hub.');
    tbody.innerHTML = bikes.map(b => `
      <tr>
        <td><strong>${esc(b.serialNumber)}</strong><div class="sub">${esc(b.model)}</div></td>
        <td>${b.cargoCapacityKg} kg</td>
        <td>${b.batteryLevel}%</td>
      </tr>`).join('');
  } catch (err) {
    toast(err.message, 'error');
  }
}

// ================= Utilities =================
let toastTimer = null;
function toast(message, type = 'info') {
  const el = $('toast');
  el.textContent = message;
  el.className = `toast ${type}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.className = 'toast hidden'; }, 4000);
}

function setEmpty(tbody, cols, text) {
  tbody.innerHTML = `<tr><td colspan="${cols}" class="empty">${text}</td></tr>`;
}

function fmtTime(iso) {
  return iso ? new Date(iso).toLocaleTimeString('pt-PT', { hour: '2-digit', minute: '2-digit' }) : '—';
}

function numOrNull(v) {
  const n = parseFloat(v);
  return Number.isNaN(n) ? null : n;
}

function esc(str) {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
}
