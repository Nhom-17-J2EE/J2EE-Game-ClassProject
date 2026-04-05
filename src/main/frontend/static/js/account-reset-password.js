(function () {
  const sendForm = document.getElementById("sendForm");
  const resetForm = document.getElementById("resetForm");
  if (!sendForm || !resetForm) return;

  const ui = window.CaroUi || {};
  const appPath = (window.CaroUrl && typeof window.CaroUrl.path === "function")
    ? window.CaroUrl.path
    : function (value) { return value; };
  const out = document.getElementById("out");
  const email = document.getElementById("email");
  const code = document.getElementById("code");
  const newPassword = document.getElementById("newPassword");
  const confirmPassword = document.getElementById("confirmPassword");
  const verifyCodeBtn = document.getElementById("verifyCodeBtn");
  const verifyCodeStatus = document.getElementById("verifyCodeStatus");
  const resetBtn = document.getElementById("resetBtn");
  let isCodeVerified = false;

  function setStatus(message, ok) {
    if (ui.setStatus) {
      ui.setStatus(out, message, ok);
    } else if (out) {
      out.textContent = String(message || "");
    }
  }

  function report(data, successMessage) {
    if (ui.apiResult) {
      return ui.apiResult(data, { statusEl: out, successMessage });
    }
    const ok = !!(data && data.success);
    setStatus(ok ? successMessage : String(data?.error || data?.message || "Thao tac that bai"), ok);
    return ok;
  }

  function reportError(err) {
    const message = String(err?.message || err || "Loi mang");
    setStatus(message, false);
    ui.toast?.(message, { type: "danger" });
  }

  function setVerifyState(verified, label, tone) {
    isCodeVerified = !!verified;
    if (verifyCodeStatus) {
      verifyCodeStatus.textContent = label || (verified ? "Ma hop le" : "Chua xac thuc ma");
      verifyCodeStatus.classList.remove("text-muted", "text-success", "text-danger");
      const resolvedTone = tone || (verified ? "success" : "muted");
      verifyCodeStatus.classList.add(
        resolvedTone === "danger" ? "text-danger" : (resolvedTone === "success" ? "text-success" : "text-muted")
      );
    }
    if (resetBtn) {
      resetBtn.disabled = !isCodeVerified;
    }
  }

  function invalidateVerifiedCode() {
    if (!isCodeVerified) return;
    setVerifyState(false, "Ma da thay doi, vui long xac thuc lai", "danger");
  }

  async function postJson(url, payload) {
    const res = await fetch(appPath(url), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload || {})
    });
    return res.json();
  }

  sendForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const emailValue = (email?.value || "").trim();
    if (!emailValue) {
      setStatus("Email la bat buoc", false);
      ui.toast?.("Email la bat buoc", { type: "danger" });
      return;
    }
    const submitBtn = sendForm.querySelector('button[type="submit"]');
    if (submitBtn) submitBtn.disabled = true;
    try {
      const data = await postJson("/account/send-reset-code", { email: emailValue });
      report(data, "Neu email ton tai, ma reset da duoc gui");
      setVerifyState(false, "Chua xac thuc ma", "muted");
      code?.focus();
    } catch (err) {
      reportError(err);
    } finally {
      if (submitBtn) submitBtn.disabled = false;
    }
  });

  email?.addEventListener("input", invalidateVerifiedCode);
  code?.addEventListener("input", invalidateVerifiedCode);

  verifyCodeBtn?.addEventListener("click", async () => {
    const emailValue = (email?.value || "").trim();
    const codeValue = (code?.value || "").trim();
    if (!emailValue || !codeValue) {
      setVerifyState(false, "Nhap email va ma xac thuc", "danger");
      setStatus("Nhap email va ma xac thuc de tiep tuc", false);
      return;
    }
    try {
      verifyCodeBtn.disabled = true;
      const data = await postJson("/account/verify-reset-code", { email: emailValue, code: codeValue });
      const ok = report(data, "Ma reset hop le");
      if (ok) {
        setVerifyState(true, "Ma hop le", "success");
      } else {
        setVerifyState(false, String(data?.error || "Ma khong hop le"), "danger");
      }
    } catch (err) {
      reportError(err);
      setVerifyState(false, "Khong the xac thuc ma", "danger");
    } finally {
      verifyCodeBtn.disabled = false;
    }
  });

  // Attach password strength indicator
  if (window.PasswordStrength && newPassword) {
    const pwdStrengthContainer = document.getElementById("resetPasswordStrength");
    window.PasswordStrength.attach(newPassword, pwdStrengthContainer);
  }

  resetForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (!isCodeVerified) {
      setStatus("Vui long xac thuc ma truoc khi dat lai mat khau", false);
      ui.toast?.("Vui long xac thuc ma truoc khi dat lai mat khau", { type: "warning" });
      return;
    }
    if (window.PasswordStrength && !window.PasswordStrength.allPassed(newPassword?.value || "")) {
      setStatus("Mat khau moi chua dat yeu cau bao mat", false);
      ui.toast?.("Mat khau moi chua dat yeu cau bao mat", { type: "danger" });
      return;
    }

    const emailValue = (email?.value || "").trim();
    const codeValue = (code?.value || "").trim();
    try {
      if (resetBtn) resetBtn.disabled = true;
      const data = await postJson("/account/reset-password", {
        email: emailValue,
        code: codeValue,
        newPassword: newPassword?.value || "",
        confirmPassword: confirmPassword?.value || ""
      });
      const ok = report(data, "Dat lai mat khau thanh cong");
      if (ok) {
        setVerifyState(false, "Dat lai mat khau thanh cong", "success");
        if (code) code.value = "";
        if (newPassword) newPassword.value = "";
        if (confirmPassword) confirmPassword.value = "";
      } else if (resetBtn) {
        resetBtn.disabled = false;
      }
    } catch (err) {
      reportError(err);
      if (resetBtn) resetBtn.disabled = false;
    } finally {
      if (resetBtn && !isCodeVerified) {
        resetBtn.disabled = true;
      }
    }
  });
})();
