(function () {
  const USERNAME_PATTERN = /^[A-Za-z0-9._]{6,20}$/;

  const form = document.getElementById("regForm");
  if (!form) return;

  const ui = window.CaroUi || {};
  const out = document.getElementById("out");
  const appPath = (window.CaroUrl && typeof window.CaroUrl.path === "function")
    ? window.CaroUrl.path
    : function (value) { return value; };

  const steps = Array.from(document.querySelectorAll("[data-register-step]"));
  const progress = Array.from(document.querySelectorAll("[data-register-progress]"));
  const storySteps = Array.from(document.querySelectorAll("[data-register-story-step]"));
  const backButton = document.getElementById("registerBackBtn");
  const passwordToggleButton = document.getElementById("toggleRegPasswordBtn");
  const usernameShuffleButton = document.getElementById("regUsernameShuffleBtn");
  const usernameNextButton = document.getElementById("regUsernameNextBtn");

  const emailInput = document.getElementById("email");
  const passwordInput = document.getElementById("password");
  const usernameInput = document.getElementById("username");
  const countryInput = document.getElementById("country");
  const genderInput = document.getElementById("gender");
  const birthMonthInput = document.getElementById("birthMonth");
  const birthDayInput = document.getElementById("birthDay");
  const birthYearInput = document.getElementById("birthYear");
  const usernameRules = {
    length: document.querySelector('[data-username-rule="length"]'),
    charset: document.querySelector('[data-username-rule="charset"]'),
    available: document.querySelector('[data-username-rule="available"]')
  };

  let stepIndex = 0;
  let availabilityToken = 0;
  let usernameAvailable = false;
  let usernameCheckTimer = 0;

  function setStatus(message, ok) {
    if (ui.setStatus) {
      ui.setStatus(out, message, ok);
    } else if (out) {
      out.textContent = String(message || "");
    }
  }

  function setRuleState(key, ok) {
    const rule = usernameRules[key];
    if (!rule) return;
    rule.classList.toggle("is-valid", !!ok);
  }

  function normalizeUsername(value) {
    return String(value || "").trim().replace(/^@+/, "");
  }

  function populateBirthDateOptions() {
    for (let month = 1; month <= 12; month += 1) {
      const option = document.createElement("option");
      option.value = String(month).padStart(2, "0");
      option.textContent = "Thang " + month;
      birthMonthInput?.appendChild(option);
    }
    for (let day = 1; day <= 31; day += 1) {
      const option = document.createElement("option");
      option.value = String(day).padStart(2, "0");
      option.textContent = "Ngay " + day;
      birthDayInput?.appendChild(option);
    }
    const currentYear = new Date().getFullYear();
    for (let year = currentYear - 6; year >= currentYear - 120; year -= 1) {
      const option = document.createElement("option");
      option.value = String(year);
      option.textContent = String(year);
      birthYearInput?.appendChild(option);
    }
  }

  function renderStep() {
    steps.forEach((step, index) => {
      step.classList.toggle("is-active", index === stepIndex);
    });
    progress.forEach((item, index) => {
      item.classList.toggle("is-active", index <= stepIndex);
    });
    storySteps.forEach((item, index) => {
      const active = index === stepIndex;
      item.classList.toggle("is-active", active);
      if (active) {
        item.setAttribute("aria-current", "step");
      } else {
        item.removeAttribute("aria-current");
      }
    });
    if (backButton) {
      backButton.disabled = stepIndex === 0;
      backButton.style.visibility = stepIndex === 0 ? "hidden" : "visible";
    }
    setStatus("", true);
  }

  function buildUsernameSuggestion() {
    const seed = String(emailInput?.value || usernameInput?.value || "player").trim().split("@")[0];
    const cleaned = seed.replace(/[^A-Za-z0-9._]+/g, "");
    let base = cleaned || "player";
    if (base.length < 6) {
      base = (base + "playerxx").slice(0, 10);
    }
    const suffix = String(100 + Math.floor(Math.random() * 900));
    return (base + suffix).slice(0, 20);
  }

  function syncUsernameRules() {
    const username = normalizeUsername(usernameInput?.value);
    setRuleState("length", username.length >= 6 && username.length <= 20);
    setRuleState("charset", USERNAME_PATTERN.test(username));
    setRuleState("available", usernameAvailable);
  }

  async function checkUsernameAvailability() {
    const currentToken = ++availabilityToken;
    const username = normalizeUsername(usernameInput?.value);
    usernameAvailable = false;
    syncUsernameRules();

    if (!USERNAME_PATTERN.test(username)) {
      return false;
    }

    try {
      const params = new URLSearchParams({ username: username });
      const res = await fetch(appPath("/account/username-availability?" + params.toString()), { cache: "no-store" });
      const data = await res.json().catch(() => ({}));
      if (currentToken !== availabilityToken) {
        return false;
      }
      usernameAvailable = !!(data && data.success);
      syncUsernameRules();
      return usernameAvailable;
    } catch (_) {
      if (currentToken === availabilityToken) {
        usernameAvailable = false;
        syncUsernameRules();
      }
      return false;
    }
  }

  function validateAccountStep() {
    const email = String(emailInput?.value || "").trim();
    const password = String(passwordInput?.value || "");
    if (!email || !email.includes("@")) {
      setStatus("Vui long nhap email hop le.", false);
      emailInput?.focus();
      return false;
    }
    if (!window.PasswordStrength || !window.PasswordStrength.allPassed(password)) {
      setStatus("Mat khau chua dat yeu cau bao mat.", false);
      passwordInput?.focus();
      return false;
    }
    return true;
  }

  async function validateUsernameStep() {
    const username = normalizeUsername(usernameInput?.value);
    if (!USERNAME_PATTERN.test(username)) {
      setStatus("Ten nguoi dung can 6-20 ky tu va chi dung chu cai, so, . hoac _.", false);
      usernameInput?.focus();
      syncUsernameRules();
      return false;
    }
    const available = await checkUsernameAvailability();
    if (!available) {
      setStatus("Ten nguoi dung nay da ton tai hoac khong hop le.", false);
      usernameInput?.focus();
      return false;
    }
    return true;
  }

  function buildBirthDate() {
    const month = String(birthMonthInput?.value || "").trim();
    const day = String(birthDayInput?.value || "").trim();
    const year = String(birthYearInput?.value || "").trim();
    if (!month || !day || !year) {
      return "";
    }
    const iso = year + "-" + month + "-" + day;
    const parsed = new Date(iso + "T00:00:00");
    if (Number.isNaN(parsed.getTime())) {
      return "";
    }
    const valid = parsed.getFullYear() === Number(year)
      && parsed.getMonth() + 1 === Number(month)
      && parsed.getDate() === Number(day);
    return valid ? iso : "";
  }

  function validateProfileStep() {
    const country = String(countryInput?.value || "").trim();
    const gender = String(genderInput?.value || "").trim();
    const birthDate = buildBirthDate();
    if (!country) {
      setStatus("Vui long chon quoc gia.", false);
      countryInput?.focus();
      return false;
    }
    if (!gender) {
      setStatus("Vui long chon gioi tinh.", false);
      genderInput?.focus();
      return false;
    }
    if (!birthDate) {
      setStatus("Vui long chon ngay sinh hop le.", false);
      birthMonthInput?.focus();
      return false;
    }
    return true;
  }

  document.querySelector('[data-register-next="0"]')?.addEventListener("click", () => {
    if (!validateAccountStep()) {
      return;
    }
    stepIndex = 1;
    if (!usernameInput?.value) {
      usernameInput.value = buildUsernameSuggestion();
    }
    renderStep();
    syncUsernameRules();
    void checkUsernameAvailability();
  });

  usernameNextButton?.addEventListener("click", async () => {
    const ok = await validateUsernameStep();
    if (!ok) {
      return;
    }
    stepIndex = 2;
    renderStep();
  });

  usernameInput?.addEventListener("input", () => {
    usernameAvailable = false;
    syncUsernameRules();
    window.clearTimeout(usernameCheckTimer);
    usernameCheckTimer = window.setTimeout(() => {
      void checkUsernameAvailability();
    }, 260);
  });

  usernameShuffleButton?.addEventListener("click", () => {
    usernameInput.value = buildUsernameSuggestion();
    usernameAvailable = false;
    syncUsernameRules();
    void checkUsernameAvailability();
  });

  passwordToggleButton?.addEventListener("click", () => {
    if (!passwordInput) return;
    const hidden = passwordInput.type === "password";
    passwordInput.type = hidden ? "text" : "password";
    passwordToggleButton.innerHTML = hidden
      ? '<i class="bi bi-eye-slash"></i>'
      : '<i class="bi bi-eye"></i>';
  });

  backButton?.addEventListener("click", () => {
    if (stepIndex <= 0) {
      return;
    }
    stepIndex -= 1;
    renderStep();
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!validateProfileStep()) {
      return;
    }
    const usernameOk = await validateUsernameStep();
    if (!usernameOk || !validateAccountStep()) {
      return;
    }

    const payload = {
      email: String(emailInput?.value || "").trim(),
      username: normalizeUsername(usernameInput?.value),
      displayName: normalizeUsername(usernameInput?.value),
      password: String(passwordInput?.value || ""),
      avatarPath: "",
      country: String(countryInput?.value || "").trim(),
      gender: String(genderInput?.value || "").trim(),
      birthDate: buildBirthDate()
    };

    try {
      const res = await fetch(appPath("/account/register"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      const data = await res.json().catch(() => ({}));
      const ok = !!(data && data.success);
      const message = ok
        ? "Da gui ma xac thuc email"
        : String(data?.error || "Dang ky that bai");
      setStatus(message, ok);
      if (!ok) {
        ui.toast?.(message, { type: "danger" });
        return;
      }
      localStorage.setItem("pendingVerifyEmail", payload.email);
      ui.toast?.(message, { type: "success" });
      window.setTimeout(() => {
        window.location.href = appPath("/account/verify-email-page");
      }, 650);
    } catch (error) {
      const message = String(error?.message || error || "Dang ky that bai");
      setStatus(message, false);
      ui.toast?.(message, { type: "danger" });
    }
  });

  populateBirthDateOptions();
  renderStep();
  syncUsernameRules();

  // Attach password strength indicator
  if (window.PasswordStrength) {
    const pwdStrengthContainer = document.getElementById("regPasswordStrength");
    window.PasswordStrength.attach(passwordInput, pwdStrengthContainer);
  }
})();
