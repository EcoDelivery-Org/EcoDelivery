// API base: same origin, or localhost:8080 when opened via file://
const API_BASE = window.location.protocol === 'file:'
  ? 'http://localhost:8080/api'
  : `${window.location.origin}/api`;

const SESSION_KEY = 'ecodelivery.user';
const REFRESH_MS = 15000;

const state = {
  hubs: [], bikes: [], logs: [], reservations: [],
  tickets: [], couriers: [], companies: [], payments: [], fleet: null
};
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

const TICKET_STATUS = {
  OPEN:     { label: 'Aberto',    cls: 'badge-warning' },
  ASSIGNED: { label: 'Atribuído', cls: 'badge-info' },
  CLOSED:   { label: 'Fechado',   cls: 'badge-success' }
};

const TICKET_PRIORITY = {
  LOW:    { label: 'Baixa', cls: '' },
  MEDIUM: { label: 'Média', cls: 'badge-warning' },
  HIGH:   { label: 'Alta',  cls: 'badge-danger' }
};

const PAYMENT_TYPE = { DEPOSIT: 'Caução', OVERTIME: 'Horas extra', REFUND: 'Reembolso' };

const PAYMENT_STATUS = {
  PENDING:   { label: 'Pendente', cls: 'badge-warning' },
  SUCCEEDED: { label: 'Concluído', cls: 'badge-success' },
  FAILED:    { label: 'Falhou', cls: 'badge-danger' }
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
  if (name === 'payments') loadPayments().then(renderPayments);
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
  // Couriers first: the reservation and payment lists depend on the selected courier.
  await Promise.all([loadCouriers(), loadCompanies()]);
  await Promise.all([loadHubs(), loadBikes(), loadLogs(), loadReservations(),
                     loadTickets(), loadFleet(), loadPayments()]);
  renderBikes();          // depends on hubs
  renderReservations();   // depends on bikes and couriers
  renderTickets();
  renderPayments();
  renderCouriers();
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
  if (!state.hubs.length) return setEmpty(tbody, 5, 'Ainda não existem hubs.');

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
        <td class="actions">
          <button class="btn btn-sm" data-hub-edit="${h.id}">Editar</button>
          <button class="btn btn-sm btn-danger" data-hub-delete="${h.id}">Apagar</button>
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
      actions += ` <button class="btn btn-sm" data-bike-edit="${b.id}">Editar</button>`;
      actions += ` <button class="btn btn-sm" data-bike-ticket="${b.id}">Ticket</button>`;
    }
    if (b.status === 'AVAILABLE' || b.status === 'MAINTENANCE') {
      actions += ` <button class="btn btn-sm btn-danger" data-bike-status="RETIRED" data-id="${b.id}">Retirar</button>`;
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
  const courierId = $('reservationCourierId').value;
  try {
    state.reservations = await api(courierId ? `/reservations?courierId=${encodeURIComponent(courierId)}` : '/reservations');
  } catch {
    state.reservations = []; // e.g. unknown courier
  }
}

function renderReservations() {
  renderReservationBikeSelect();
  const tbody = $('reservationsTableBody');
  if (!state.reservations.length) return setEmpty(tbody, 6, 'Sem reservas.');

  tbody.innerHTML = state.reservations.map(r => {
    const st = RESERVATION_STATUS[r.status] || { label: r.status, cls: '' };
    const bike = state.bikes.find(b => b.id === r.bikeId);
    let actions = '';
    if (r.status === 'RESERVED') {
      actions = `<button class="btn btn-sm" data-res-start="${r.id}" data-courier="${r.courierId}">Iniciar</button>
         <button class="btn btn-sm btn-danger" data-res-cancel="${r.id}">Cancelar</button>`;
    } else if (r.status === 'IN_USE') {
      actions = `<select class="select-sm" data-end-hub-for="${r.id}">${hubOptions()}</select>
         <button class="btn btn-sm btn-primary" data-res-end="${r.id}">Terminar</button>`;
    }
    return `
      <tr>
        <td>#${r.id}</td>
        <td>${bike ? esc(bike.serialNumber) : `Bike #${r.bikeId}`}</td>
        <td>${esc(courierName(r.courierId))}</td>
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
  // Prefer the server-side fleet overview; fall back to counting the loaded bikes.
  const server = state.fleet?.bikesByStatus;
  const count = (status) => server ? (server[status] ?? 0) : state.bikes.filter(b => b.status === status).length;
  $('statOpenTickets').textContent = state.fleet?.openTickets ?? state.tickets.filter(t => t.status !== 'CLOSED').length;
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

  $('immediateUseBtn').addEventListener('click', () => runAction(async () => {
    const courierId = parseInt($('reservationCourierId').value, 10);
    const bikeId = parseInt($('reservationBikeId').value, 10);
    if (Number.isNaN(courierId) || Number.isNaN(bikeId)) throw new Error('Selecione estafeta e e-bike.');
    const data = await api('/reservations/immediate', { method: 'POST', body: JSON.stringify({ courierId, bikeId }) });
    toast(`Sessão #${data.id} iniciada.`, 'success');
  }));

  handleForm('createTicketForm', async () => {
    const payload = {
      bikeId: parseInt($('ticketBikeId').value, 10),
      description: $('ticketDescription').value.trim(),
      priority: $('ticketPriority').value
    };
    const t = await api('/maintenance/tickets', { method: 'POST', body: JSON.stringify(payload) });
    toast(`Ticket #${t.id} aberto. A e-bike passou a manutenção.`, 'success');
  });
  $('ticketStatusFilter').addEventListener('change', renderTickets);

  handleForm('createCompanyForm', async () => {
    const c = await api('/companies', { method: 'POST', body: JSON.stringify({ name: $('companyName').value.trim() }) });
    toast(`Empresa "${c.name}" criada.`, 'success');
  });

  handleForm('createCourierForm', async () => {
    const company = $('courierCompany').value;
    const payload = {
      name: $('courierName').value.trim(),
      email: $('courierEmail').value.trim(),
      phone: $('courierPhone').value.trim() || null,
      companyId: company ? parseInt(company, 10) : null
    };
    const c = await api('/couriers', { method: 'POST', body: JSON.stringify(payload) });
    toast(`Estafeta "${c.name}" criado.`, 'success');
  });

  $('paymentCourierId').addEventListener('change', async () => {
    await loadPayments();
    renderPayments();
  });

  handleForm('bikeEditForm', async () => {
    const id = $('bikeEditForm').dataset.id;
    await api(`/bikes/${id}`, {
      method: 'PUT',
      body: JSON.stringify({
        model: $('beModel').value.trim(),
        cargoCapacityKg: parseFloat($('beCargo').value),
        batteryLevel: parseInt($('beBattery').value, 10)
      })
    });
    const bike = state.bikes.find(b => String(b.id) === id);
    const hub = $('beHub').value;
    if (hub && bike && String(bike.hubId) !== hub) {
      await api(`/bikes/${id}/hub?hubId=${hub}`, { method: 'PATCH' });
    }
    $('bikeEditDialog').close();
    toast('E-bike atualizada.', 'success');
  }, { reset: false });

  handleForm('hubEditForm', async () => {
    const id = $('hubEditForm').dataset.id;
    await api(`/hubs/${id}`, {
      method: 'PUT',
      body: JSON.stringify({
        name: $('heName').value.trim(),
        address: $('heAddress').value.trim(),
        capacity: parseInt($('heCapacity').value, 10),
        latitude: numOrNull($('heLat').value),
        longitude: numOrNull($('heLon').value),
        boundaryRadiusMeters: numOrNull($('heRadius').value) ?? 50
      })
    });
    $('hubEditDialog').close();
    map.fitted = false;
    toast('Hub atualizado.', 'success');
  }, { reset: false });

  document.querySelectorAll('[data-close]').forEach(btn =>
    btn.addEventListener('click', () => $(btn.dataset.close).close()));

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
    const btn = e.target.closest('button');
    if (btn?.dataset.hubEdit) return openHubEdit(Number(btn.dataset.hubEdit));
    if (btn?.dataset.hubDelete) {
      const hub = state.hubs.find(h => String(h.id) === btn.dataset.hubDelete);
      if (!confirm(`Apagar o hub "${hub?.name}"?`)) return;
      return runAction(async () => {
        await api(`/hubs/${btn.dataset.hubDelete}`, { method: 'DELETE' });
        map.fitted = false;
        toast('Hub apagado.', 'success');
      });
    }
    const row = e.target.closest('[data-map-hub]');
    if (row) focusHubOnMap(Number(row.dataset.mapHub));
  });

  $('bikesTableBody').addEventListener('click', (e) => {
    const btn = e.target.closest('button');
    if (!btn) return;
    const id = btn.dataset.id;
    if (btn.dataset.bikeEdit) {
      openBikeEdit(Number(btn.dataset.bikeEdit));
    } else if (btn.dataset.bikeTicket) {
      $('ticketBikeId').value = btn.dataset.bikeTicket;
      showView('maintenance');
      $('ticketDescription').focus();
    } else if (btn.dataset.bikeStatus) {
      if (btn.dataset.bikeStatus === 'RETIRED' && !confirm('Retirar esta e-bike da frota? Não pode ser revertido.')) return;
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
    } else if (btn.dataset.resEnd) {
      const hubId = btn.parentElement.querySelector(`[data-end-hub-for="${btn.dataset.resEnd}"]`).value;
      if (!hubId) return toast('Escolha o hub onde a e-bike é devolvida.', 'error');
      runAction(async () => {
        await api(`/reservations/${btn.dataset.resEnd}/end?endHubId=${hubId}`, { method: 'POST' });
        toast(`Sessão #${btn.dataset.resEnd} terminada. Caução reembolsada.`, 'success');
      });
    }
  });

  $('ticketsTableBody').addEventListener('click', async (e) => {
    const btn = e.target.closest('button');
    if (!btn) return;
    if (btn.dataset.ticketAssign) {
      const tech = await askText('Atribuir técnico', 'Nome do técnico');
      if (!tech) return;
      runAction(async () => {
        await api(`/maintenance/tickets/${btn.dataset.ticketAssign}/assign?technician=${encodeURIComponent(tech)}`, { method: 'PATCH' });
        toast('Técnico atribuído.', 'success');
      });
    } else if (btn.dataset.ticketClose) {
      const resolution = await askText('Fechar ticket', 'Resolução');
      if (!resolution) return;
      runAction(async () => {
        await api(`/maintenance/tickets/${btn.dataset.ticketClose}/close?resolution=${encodeURIComponent(resolution)}`, { method: 'POST' });
        toast('Ticket fechado. A e-bike voltou a estar disponível.', 'success');
      });
    }
  });

  $('companiesTableBody').addEventListener('click', (e) => {
    const btn = e.target.closest('[data-company-delete]');
    if (!btn || !confirm('Apagar esta empresa?')) return;
    runAction(async () => {
      await api(`/companies/${btn.dataset.companyDelete}`, { method: 'DELETE' });
      toast('Empresa apagada.', 'success');
    });
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

// ================= Couriers & companies =================
function loadCouriers() {
  return safeLoad(async () => { state.couriers = await api('/couriers'); }, 'estafetas');
}

function loadCompanies() {
  return safeLoad(async () => { state.companies = await api('/companies'); }, 'empresas');
}

const courierName = (id) => state.couriers.find(c => c.id === id)?.name || (id ? `Estafeta #${id}` : '—');
const companyName = (id) => state.companies.find(c => c.id === id)?.name || '—';

function fillSelect(id, html) {
  const select = $(id);
  const current = select.value;
  select.innerHTML = html;
  if ([...select.options].some(o => o.value === current)) select.value = current;
}

function renderCouriers() {
  const options = state.couriers.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('');
  fillSelect('reservationCourierId', options);
  fillSelect('paymentCourierId', options);
  fillSelect('courierCompany', '<option value="">Sem empresa (não elegível)</option>' +
    state.companies.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join(''));

  const couriers = $('couriersTableBody');
  if (!state.couriers.length) setEmpty(couriers, 3, 'Ainda não existem estafetas.');
  else couriers.innerHTML = state.couriers.map(c => `
    <tr>
      <td><strong>${esc(c.name)}</strong><div class="sub">#${c.id}</div></td>
      <td>${esc(c.email)}<div class="sub">${esc(c.phone || '')}</div></td>
      <td>${c.companyId ? esc(companyName(c.companyId)) : '<span class="badge badge-danger">Sem empresa</span>'}</td>
    </tr>`).join('');

  const companies = $('companiesTableBody');
  if (!state.companies.length) setEmpty(companies, 3, 'Ainda não existem empresas.');
  else companies.innerHTML = state.companies.map(c => `
    <tr>
      <td><strong>${esc(c.name)}</strong></td>
      <td>${state.couriers.filter(x => x.companyId === c.id).length}</td>
      <td class="actions"><button class="btn btn-sm btn-danger" data-company-delete="${c.id}">Apagar</button></td>
    </tr>`).join('');
}

// ================= Maintenance tickets =================
function loadTickets() {
  return safeLoad(async () => { state.tickets = await api('/maintenance/tickets'); }, 'tickets');
}

function loadFleet() {
  return safeLoad(async () => { state.fleet = await api('/operator/fleet-status'); }, 'estado da frota');
}

function renderTickets() {
  fillSelect('ticketBikeId', '<option value="">Selecione…</option>' +
    state.bikes.filter(b => b.status !== 'RETIRED').map(b =>
      `<option value="${b.id}">${esc(b.serialNumber)} · ${esc(b.model)}</option>`).join(''));

  const filter = $('ticketStatusFilter').value;
  const tickets = (filter ? state.tickets.filter(t => t.status === filter) : state.tickets)
    .slice().sort((a, b) => String(b.openedAt).localeCompare(String(a.openedAt)));
  const tbody = $('ticketsTableBody');
  if (!tickets.length) return setEmpty(tbody, 7, 'Sem tickets.');

  const now = Date.now();
  tbody.innerHTML = tickets.map(t => {
    const st = TICKET_STATUS[t.status] || { label: t.status, cls: '' };
    const pr = TICKET_PRIORITY[t.priority] || { label: t.priority, cls: '' };
    const bike = state.bikes.find(b => b.id === t.bikeId);
    const overdue = t.status !== 'CLOSED' && t.slaDeadline && new Date(t.slaDeadline).getTime() < now;
    let actions = '';
    if (t.status !== 'CLOSED') {
      actions = `<button class="btn btn-sm" data-ticket-assign="${t.id}">${t.technician ? 'Reatribuir' : 'Atribuir'}</button>
        <button class="btn btn-sm btn-primary" data-ticket-close="${t.id}">Fechar</button>`;
    }
    const sub = t.status === 'CLOSED' ? esc(t.resolution || '') : (t.technician ? `Técnico: ${esc(t.technician)}` : '');
    return `
      <tr>
        <td>#${t.id}</td>
        <td>${bike ? esc(bike.serialNumber) : `Bike #${t.bikeId}`}</td>
        <td>${esc(t.description)}<div class="sub">${sub}</div></td>
        <td><span class="badge ${pr.cls}">${pr.label}</span></td>
        <td><span class="badge ${st.cls}">${st.label}</span></td>
        <td class="${overdue ? 'overdue' : ''}">${fmtDateTime(t.slaDeadline)}${overdue ? '<div class="sub overdue">SLA ultrapassado</div>' : ''}</td>
        <td class="actions">${actions}</td>
      </tr>`;
  }).join('');
}

// ================= Payments =================
async function loadPayments() {
  const courierId = $('paymentCourierId').value;
  if (!courierId) { state.payments = []; return; }
  try {
    state.payments = await api(`/payments?courierId=${encodeURIComponent(courierId)}`);
  } catch {
    state.payments = [];
  }
}

function renderPayments() {
  const tbody = $('paymentsTableBody');
  if (!state.payments.length) return setEmpty(tbody, 6, 'Sem pagamentos para este estafeta.');
  tbody.innerHTML = state.payments.map(p => {
    const st = PAYMENT_STATUS[p.status] || { label: p.status, cls: '' };
    const sign = p.type === 'REFUND' ? '+' : '−';
    return `
      <tr>
        <td>#${p.id}</td>
        <td class="sub">${fmtDateTime(p.createdAt)}</td>
        <td>${p.reservationId ? `#${p.reservationId}` : '—'}</td>
        <td>${PAYMENT_TYPE[p.type] || esc(p.type)}</td>
        <td>${sign} ${Number(p.amount).toFixed(2)} €</td>
        <td><span class="badge ${st.cls}">${st.label}</span></td>
      </tr>`;
  }).join('');
}

// ================= Edit dialogs =================
// Options for choosing the hub where a session ends (only hubs with free space).
function hubOptions() {
  return '<option value="">Hub de devolução…</option>' + state.hubs
    .filter(h => h.availableCapacity > 0)
    .map(h => `<option value="${h.id}">${esc(h.name)} (${h.currentBikes}/${h.capacity})</option>`)
    .join('');
}

function openBikeEdit(id) {
  const b = state.bikes.find(x => x.id === id);
  if (!b) return;
  $('bikeEditForm').dataset.id = id;
  $('beModel').value = b.model;
  $('beCargo').value = b.cargoCapacityKg;
  $('beBattery').value = b.batteryLevel;
  const select = $('beHub');
  select.innerHTML = state.hubs.map(h =>
    `<option value="${h.id}" ${h.id === b.hubId ? 'selected' : ''}>${esc(h.name)} (${h.currentBikes}/${h.capacity})</option>`).join('');
  if (b.status === 'IN_USE' || b.hubId == null) {
    select.insertAdjacentHTML('afterbegin', '<option value="" selected>Fora de um hub</option>');
    select.disabled = b.status === 'IN_USE';
  } else {
    select.disabled = false;
  }
  $('bikeEditDialog').showModal();
}

function openHubEdit(id) {
  const h = state.hubs.find(x => x.id === id);
  if (!h) return;
  $('hubEditForm').dataset.id = id;
  $('heName').value = h.name;
  $('heAddress').value = h.address;
  $('heCapacity').value = h.capacity;
  $('heRadius').value = h.boundaryRadiusMeters;
  $('heLat').value = '';
  $('heLon').value = '';
  $('hubEditDialog').showModal();
}

// Small reusable "type a value" dialog; resolves to the text, or null if cancelled.
function askText(title, label) {
  const dialog = $('promptDialog');
  $('promptTitle').textContent = title;
  $('promptLabel').textContent = label;
  $('promptInput').value = '';
  return new Promise(resolve => {
    dialog.addEventListener('close', () => {
      resolve(dialog.returnValue === 'ok' ? $('promptInput').value.trim() : null);
    }, { once: true });
    dialog.returnValue = 'cancel';
    dialog.showModal();
  });
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

function fmtDateTime(iso) {
  return iso ? new Date(iso).toLocaleString('pt-PT', { dateStyle: 'short', timeStyle: 'short' }) : '—';
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
