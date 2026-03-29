(function() {
  'use strict';

  const root = document.getElementById('friendshipUserDetailRoot');
  if (!root) return;

  const actionBtn = document.getElementById('friendRelationBtn');
  const out = document.getElementById('friendshipUserDetailOut');
  const ui = window.CaroUi || {};

  if (!actionBtn) return;

  const targetUserId = root.dataset.targetUserId || root.dataset.userId || '';
  const currentUserId = root.dataset.currentUserId || window.CaroUser?.get?.()?.userId || '';

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

  function toBool(value) {
    return String(value).toLowerCase() === 'true';
  }

  // ============== UPDATE UI ==============

  function updateActionLabel(status) {
    // status can be: "friends", "pending_sent", "pending_received", "none"
    
    if (status === 'friends') {
      actionBtn.textContent = 'Xóa bạn';
      actionBtn.className = 'btn btn-outline-danger';
      return;
    }

    if (status === 'pending_sent') {
      actionBtn.textContent = 'Lời mời đã gửi';
      actionBtn.className = 'btn btn-outline-warning';
      actionBtn.disabled = true;
      return;
    }

    if (status === 'pending_received') {
      actionBtn.textContent = 'Xem yêu cầu';
      actionBtn.className = 'btn btn-outline-info';
      return;
    }

    // status === 'none'
    actionBtn.textContent = 'Gửi lời mời kết bạn';
    actionBtn.className = 'btn btn-primary';
    actionBtn.disabled = false;
  }

  // ============== LOAD RELATIONSHIP STATUS ==============

  async function loadRelationshipStatus() {
    if (!targetUserId || !currentUserId) {
      setStatus('Thiếu thông tin tài khoản', false);
      return;
    }

    const data = await apiCall(`/friendship/api/user-detail/${targetUserId}`);
    if (!data?.success) {
      setStatus(data?.error || 'Không thể tải thông tin', false);
      return;
    }

    const status = data.relationshipStatus || 'none';
    updateActionLabel(status);
    root.dataset.relationshipStatus = status;
  }

  // ============== EVENT HANDLERS ==============

  actionBtn.addEventListener('click', async () => {
    if (!currentUserId || !targetUserId) {
      setStatus('Thiếu thông tin tài khoản', false);
      showToast('Thiếu thông tin tài khoản', 'warning');
      return;
    }

    const status = root.dataset.relationshipStatus || 'none';

    // If pending_received, go to notifications
    if (status === 'pending_received') {
      window.location.href = (window.CaroUrl?.path?.('/friendship/notifications') || '/friendship/notifications');
      return;
    }

    // If already requested (pending_sent), do nothing
    if (status === 'pending_sent') {
      return;
    }

    actionBtn.disabled = true;

    try {
      let data;
      if (status === 'friends') {
        // Remove friendship
        data = await apiCall('/friendship/api/remove', 'POST', { friendId: targetUserId });
        if (handleApiResponse(data, 'Đã xóa bạn')) {
          updateActionLabel('none');
          root.dataset.relationshipStatus = 'none';
        }
      } else {
        // Send request
        data = await apiCall('/friendship/api/send-request-by-id', 'POST', { addresseeId: targetUserId });
        if (handleApiResponse(data, 'Đã gửi lời mời kết bạn')) {
          updateActionLabel('pending_sent');
          root.dataset.relationshipStatus = 'pending_sent';
        }
      }
    } catch (error) {
      console.error('Action failed:', error);
      setStatus(String(error.message || error), false);
    } finally {
      actionBtn.disabled = false;
    }
  });

  // ============== INITIALIZATION ==============

  window.addEventListener('load', () => {
    loadRelationshipStatus();
  });

  // Expose for manual reload
  window.friendshipUserDetailReload = loadRelationshipStatus;
})();
