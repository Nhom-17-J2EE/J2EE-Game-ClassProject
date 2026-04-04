(function() {
  'use strict';

  const root = document.getElementById('friendshipSearchRoot');
  if (!root) return;

  const out = document.getElementById('friendshipSearchOut');
  const uid = root.dataset.currentUserId || window.CaroUser?.get?.()?.userId || '';
  const ui = window.CaroUi || {};

  if (!uid) return;

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

  // ============== SEND REQUEST ACTION ==============

  document.querySelectorAll('.quick-add[data-user-id]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const friendId = btn.dataset.userId;
      if (!friendId) return;

      const originalText = btn.textContent;
      btn.disabled = true;
      btn.textContent = 'Đang gửi...';

      try {
        const data = await apiCall('/friendship/api/send-request-by-id', 'POST', { addresseeId: friendId });
        
        if (data?.success) {
          setStatus('Đã gửi lời mời kết bạn', true);
          showToast('Đã gửi lời mời kết bạn', 'success');
          btn.textContent = 'Đã gửi';
          btn.classList.remove('btn-primary');
          btn.classList.add('btn-success');
        } else {
          setStatus(data?.error || 'Gửi lời mời thất bại', false);
          showToast(data?.error || 'Gửi lời mời thất bại', 'danger');
          btn.textContent = originalText;
          btn.disabled = false;
        }
      } catch (error) {
        const message = String(error?.message || error || 'Lỗi yêu cầu');
        setStatus(message, false);
        showToast(message, 'danger');
        btn.textContent = originalText;
        btn.disabled = false;
      }
    });
  });
})();
