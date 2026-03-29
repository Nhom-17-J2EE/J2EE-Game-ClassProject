(function() {
  'use strict';

  const root = document.getElementById('friendshipNotificationsRoot');
  if (!root) return;

  const out = document.getElementById('friendshipNotificationsOut');
  const list = document.getElementById('friendRequestList');
  const ui = window.CaroUi || {};

  // ============== UTILITIES ==============

  function setStatus(message, ok) {
    if (ui.setStatus) {
      ui.setStatus(out, message, ok);
    } else if (out) {
      out.textContent = String(message || '');
      out.className = ok ? 'alert alert-success' : 'alert alert-danger';
    }
  }

  function showToast(message, type = 'info') {
    if (ui.toast) {
      ui.toast(message, { type });
    } else {
      console.log(`[${type}] ${message}`);
    }
  }

  function handleApiResponse(data, successMessage) {
    if (!data?.success) {
      const error = data?.error || 'Hoạt động thất bại';
      setStatus(error, false);
      showToast(error, 'danger');
      return false;
    }
    setStatus(successMessage || 'Thành công', true);
    showToast(successMessage || 'Thành công', 'success');
    return true;
  }

  async function apiCall(url, method = 'GET', payload = null) {
    try {
      const options = {
        method,
        headers: { 'Content-Type': 'application/json' }
      };
      if (payload && (method === 'POST' || method === 'PUT')) {
        options.body = JSON.stringify(payload);
      }
      const response = await fetch(url, options);
      return await response.json();
    } catch (error) {
      console.error('API call failed:', error);
      showToast(String(error.message || error), 'danger');
      return { success: false, error: String(error) };
    }
  }

  // ============== RENDERING ==============

  function showEmptyState() {
    if (!list) return;
    const existing = list.querySelector('.text-muted');
    if (existing) return;

    const div = document.createElement('div');
    div.className = 'text-muted small text-center py-4';
    div.textContent = 'Không có lời mời kết bạn đang chờ xử lý';
    list.appendChild(div);
  }

  function removeEmptyState() {
    if (!list) return;
    const placeholder = list.querySelector('.text-muted.text-center');
    if (placeholder && list.querySelectorAll('[data-friendship-id]').length > 0) {
      placeholder.remove();
    }
  }

  function createRequestRow(req) {
    const li = document.createElement('li');
    li.className = 'list-group-item';
    li.dataset.friendshipId = req.friendshipId;

    const row = document.createElement('div');
    row.className = 'd-flex align-items-center gap-3 py-2';

    // Avatar
    const img = document.createElement('img');
    img.src = req.requesterAvatarPath;
    img.alt = req.requesterName;
    img.width = 48;
    img.height = 48;
    img.className = 'rounded-circle flex-shrink-0 border';
    row.appendChild(img);

    // Info
    const info = document.createElement('div');
    info.className = 'flex-grow-1';
    const title = document.createElement('div');
    title.className = 'fw-semibold';
    title.textContent = req.requesterName;
    info.appendChild(title);
    const meta = document.createElement('div');
    meta.className = 'text-muted small';
    meta.textContent = req.requesterEmail || 'Không có email';
    info.appendChild(meta);
    row.appendChild(info);

    // Actions
    const actions = document.createElement('div');
    actions.className = 'flex-shrink-0 d-flex gap-2';

    const acceptBtn = document.createElement('button');
    acceptBtn.type = 'button';
    acceptBtn.className = 'btn btn-sm btn-outline-success';
    acceptBtn.textContent = 'Chấp nhận';
    acceptBtn.addEventListener('click', () => onAccept(req.friendshipId, li));
    actions.appendChild(acceptBtn);

    const declineBtn = document.createElement('button');
    declineBtn.type = 'button';
    declineBtn.className = 'btn btn-sm btn-outline-danger';
    declineBtn.textContent = 'Từ chối';
    declineBtn.addEventListener('click', () => onDecline(req.friendshipId, li));
    actions.appendChild(declineBtn);

    row.appendChild(actions);
    li.appendChild(row);
    return li;
  }

  // ============== EVENT HANDLERS ==============

  async function onAccept(friendshipId, row) {
    const data = await apiCall('/friendship/api/accept', 'POST', { friendshipId });
    if (handleApiResponse(data, 'Đã chấp nhận lời mời kết bạn')) {
      row.remove();
      removeEmptyState();
      if (!list.querySelector('[data-friendship-id]')) {
        showEmptyState();
      }
    }
  }

  async function onDecline(friendshipId, row) {
    const data = await apiCall('/friendship/api/decline', 'POST', { friendshipId });
    if (handleApiResponse(data, 'Đã từ chối lời mời kết bạn')) {
      row.remove();
      removeEmptyState();
      if (!list.querySelector('[data-friendship-id]')) {
        showEmptyState();
      }
    }
  }

  // ============== INITIALIZATION ==============

  async function loadNotifications() {
    if (!list) return;
    list.innerHTML = '';

    const data = await apiCall('/friendship/api/notifications');
    if (!data?.success) {
      showToast(data?.error || 'Lỗi tải dữ liệu', 'danger');
      showEmptyState();
      return;
    }

    const requests = data.friendRequests || [];
    if (requests.length === 0) {
      showEmptyState();
      return;
    }

    requests.forEach(req => {
      list.appendChild(createRequestRow(req));
    });
  }

  // Load on page load
  window.addEventListener('load', () => {
    loadNotifications();
  });

  // Expose for manual refresh
  window.friendshipNotificationsReload = loadNotifications;
})();
