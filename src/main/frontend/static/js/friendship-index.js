(function() {
  'use strict';

  const root = document.getElementById('friendshipRoot');
  if (!root) return;

  const uid = root.dataset.currentUserId || window.CaroUser?.get?.()?.userId || '';
  const form = document.getElementById('sendByEmailForm');
  const out = document.getElementById('out');
  if (!form || !out) return;

  const ui = window.CaroUi || {};

  const lists = {
    friends: document.getElementById('friendsList'),
    pending: document.getElementById('pendingRequestsList'),
    sent: document.getElementById('sentRequestsList')
  };

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

  function ensureLogin() {
    if (!uid) {
      const msg = 'Bạn cần đăng nhập để sử dụng tính năng này';
      setStatus(msg, false);
      showToast(msg, 'warning');
      return false;
    }
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

  function ensureLists() {
    return lists.friends && lists.pending && lists.sent;
  }

  function removeEmptyPlaceholder(listEl) {
    if (!listEl) return;
    const placeholder = listEl.querySelector('.list-group-item.text-muted');
    if (placeholder) placeholder.remove();
  }

  function showEmptyState(listEl, message) {
    if (!listEl) return;
    const hasItems = listEl.querySelectorAll('.list-group-item:not(.text-muted)').length > 0;
    if (hasItems) return;

    const li = document.createElement('li');
    li.className = 'list-group-item text-muted small text-center py-3';
    li.textContent = message;
    listEl.appendChild(li);
  }

  function removeRowFromButton(btn) {
    const row = btn?.closest('.list-group-item');
    if (row) row.remove();
  }

  function createButton(className, text, onClick) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = className;
    btn.textContent = text;
    if (onClick) btn.addEventListener('click', onClick);
    return btn;
  }

  function createLink(href, text, className = 'btn btn-sm btn-outline-primary') {
    const a = document.createElement('a');
    a.href = href;
    a.textContent = text;
    a.className = className;
    return a;
  }

  function appPath(path) {
    return window.CaroUrl?.path?.(path) || path;
  }

  // ============== LOAD INDEX DATA ==============

  async function loadIndexData() {
    if (!ensureLogin() || !ensureLists()) return;

    const data = await apiCall('/friendship/api/index');
    if (!data?.success) return;

    // Load friends
    renderFriends(data.friends || []);

    // Load pending requests
    renderPendingRequests(data.pendingRequests || []);

    // Load sent requests
    renderSentRequests(data.sentRequests || []);
  }

  function renderFriends(friends) {
    if (!lists.friends) return;
    lists.friends.innerHTML = '';
    if (!friends || friends.length === 0) {
      showEmptyState(lists.friends, 'Bạn chưa có bạn bè');
      return;
    }
    friends.forEach(friend => createFriendRow(friend));
    
    // Update stat badges
    const badge = document.getElementById('friend-count-badge');
    if (badge) badge.textContent = `${friends.length} bạn`;
    const display = document.getElementById('friendCountDisplay');
    if (display) display.textContent = String(friends.length);
  }

  function renderPendingRequests(requests) {
    if (!lists.pending) return;
    lists.pending.innerHTML = '';
    if (!requests || requests.length === 0) {
      showEmptyState(lists.pending, 'Bạn không có lời mời chưa xử lý');
      return;
    }
    requests.forEach(req => createPendingRequestRow(req));
    
    // Update stat badges
    const badge = document.getElementById('pending-count-badge');
    if (badge) badge.textContent = `${requests.length} mục`;
    const display = document.getElementById('pendingCountDisplay');
    if (display) display.textContent = String(requests.length);
  }

  function renderSentRequests(requests) {
    if (!lists.sent) return;
    lists.sent.innerHTML = '';
    if (!requests || requests.length === 0) {
      showEmptyState(lists.sent, 'Bạn không có lời mời đã gửi nào');
      return;
    }
    requests.forEach(req => createSentRequestRow(req));
    
    // Update stat badges
    const badge = document.getElementById('sent-count-badge');
    if (badge) badge.textContent = `${requests.length} mục`;
    const display = document.getElementById('sentCountDisplay');
    if (display) display.textContent = String(requests.length);
  }

  function createFriendRow(friend) {
    const li = document.createElement('li');
    li.className = 'list-group-item';
    li.dataset.friendId = friend.userId;

    const row = document.createElement('div');
    row.className = 'd-flex align-items-center gap-3 py-2';

    // Avatar
    const img = document.createElement('img');
    img.src = friend.avatarPath;
    img.alt = friend.displayName;
    img.width = 48;
    img.height = 48;
    img.className = 'rounded-circle flex-shrink-0 border';
    row.appendChild(img);

    // Info
    const info = document.createElement('div');
    info.className = 'flex-grow-1';
    const title = document.createElement('div');
    title.className = 'fw-semibold';
    title.textContent = friend.displayName || friend.email;
    info.appendChild(title);
    const meta = document.createElement('div');
    meta.className = 'text-muted small';
    meta.textContent = `${friend.email} | Điểm: ${friend.score}`;
    info.appendChild(meta);
    row.appendChild(info);

    // Status badge
    const badge = document.createElement('span');
    badge.className = `badge ${friend.online ? 'bg-success' : 'bg-secondary'}`;
    badge.textContent = friend.online ? 'Trực tuyến' : 'Ngoại tuyến';
    row.appendChild(badge);

    // Actions
    const actions = document.createElement('div');
    actions.className = 'flex-shrink-0 d-flex gap-2';
    actions.appendChild(createLink(`/friendship/user-detail/${friend.userId}`, 'Xem', 'btn btn-sm btn-outline-primary'));
    actions.appendChild(createButton('btn btn-sm btn-outline-danger remove-friend',
      'Xóa bạn',
      async () => await onRemoveFriend(friend.userId, li)
    ));
    row.appendChild(actions);

    li.appendChild(row);
    lists.friends.appendChild(li);
  }

  function createPendingRequestRow(req) {
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
    actions.appendChild(createButton('btn btn-sm btn-outline-success accept-request',
      'Chấp nhận',
      async () => await onAcceptRequest(req.friendshipId, li, req.requesterId)
    ));
    actions.appendChild(createButton('btn btn-sm btn-outline-danger decline-request',
      'Từ chối',
      async () => await onDeclineRequest(req.friendshipId, li)
    ));
    row.appendChild(actions);

    li.appendChild(row);
    lists.pending.appendChild(li);
  }

  function createSentRequestRow(req) {
    const li = document.createElement('li');
    li.className = 'list-group-item';
    li.dataset.friendshipId = req.friendshipId;

    const row = document.createElement('div');
    row.className = 'd-flex align-items-center gap-3 py-2';

    // Avatar
    const img = document.createElement('img');
    img.src = req.addresseeAvatarPath;
    img.alt = req.addresseeName;
    img.width = 48;
    img.height = 48;
    img.className = 'rounded-circle flex-shrink-0 border';
    row.appendChild(img);

    // Info
    const info = document.createElement('div');
    info.className = 'flex-grow-1';
    const title = document.createElement('div');
    title.className = 'fw-semibold';
    title.textContent = req.addresseeName;
    info.appendChild(title);
    const meta = document.createElement('div');
    meta.className = 'text-muted small';
    meta.textContent = `Đang chờ - ${formatDate(req.createdAt)}`;
    info.appendChild(meta);
    row.appendChild(info);

    // Actions
    const actions = document.createElement('div');
    actions.className = 'flex-shrink-0 d-flex gap-2';
    actions.appendChild(createLink(`/friendship/user-detail/${req.addresseeId}`, 'Xem', 'btn btn-sm btn-outline-primary'));
    actions.appendChild(createButton('btn btn-sm btn-outline-danger cancel-request',
      'Hủy',
      async () => await onCancelSentRequest(req.addresseeId, li)
    ));
    row.appendChild(actions);

    li.appendChild(row);
    lists.sent.appendChild(li);
  }

  // ============== EVENT HANDLERS ==============

  async function onAcceptRequest(friendshipId, row, requesterId) {
    const data = await apiCall('/friendship/api/accept', 'POST', { friendshipId });
    if (handleApiResponse(data, 'Đã chấp nhận lời mời kết bạn')) {
      removeEmptyPlaceholder(lists.pending);
      removeRowFromButton({ closest: () => row });
      showEmptyState(lists.pending, 'Bạn không có lời mời chưa xử lý');
      loadIndexData();
    }
  }

  async function onDeclineRequest(friendshipId, row) {
    const data = await apiCall('/friendship/api/decline', 'POST', { friendshipId });
    if (handleApiResponse(data, 'Đã từ chối lời mời kết bạn')) {
      removeRowFromButton({ closest: () => row });
      showEmptyState(lists.pending, 'Bạn không có lời mời chưa xử lý');
    }
  }

  async function onRemoveFriend(friendId, row) {
    if (!confirm('Bạn chắc chắn muốn xóa bạn này?')) return;
    const data = await apiCall('/friendship/api/remove', 'POST', { friendId });
    if (handleApiResponse(data, 'Đã xóa bạn')) {
      removeRowFromButton({ closest: () => row });
      showEmptyState(lists.friends, 'Bạn chưa có bạn bè');
    }
  }

  async function onCancelSentRequest(friendId, row) {
    if (!confirm('Bạn chắc chắn muốn hủy lời mời này?')) return;
    const data = await apiCall('/friendship/api/remove', 'POST', { friendId });
    if (handleApiResponse(data, 'Đã hủy lời mời kết bạn')) {
      removeRowFromButton({ closest: () => row });
      showEmptyState(lists.sent, 'Bạn không có lời mời đã gửi nào');
    }
  }

  // ============== FORM SUBMISSION ==============

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!ensureLogin()) return;

    const emailInput = document.getElementById('email');
    const email = emailInput?.value?.trim();

    if (!email) {
      showToast('Vui lòng nhập email', 'warning');
      return;
    }

    const data = await apiCall('/friendship/api/send-request', 'POST', { email });
    if (handleApiResponse(data, 'Đã gửi lời mời kết bạn')) {
      if (emailInput) emailInput.value = '';
      loadIndexData();
    }
  });

  // ============== INITIALIZATION ==============

  function formatDate(dateStr) {
    if (!dateStr) return '';
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString('vi-VN');
    } catch {
      return dateStr;
    }
  }

  // Load data on page load
  window.addEventListener('load', () => {
    if (ensureLogin()) {
      loadIndexData();
    }
  });

  // Expose for manual refresh
  window.friendshipIndexReload = loadIndexData;
})();
