(function () {
  const THEME_KEY = "theme";
  const THEME_MODE_KEY = "caroThemeMode.v1";
  const MUSIC_KEY = "music";
  const TOAST_ENABLED_KEY = "caroToastEnabled.v1";
  const SIDEBAR_DESKTOP_HIDDEN_KEY = "caroSidebarDesktopHidden.v2";
  const SIDEBAR_MOBILE_AUTO_CLOSE_KEY = "caroSidebarMobileAutoClose.v1";
  const FRIEND_LIST_SHOW_OFFLINE_KEY = "caroFriendListShowOffline.v1";
  const FRIEND_LIST_AUTO_REFRESH_KEY = "caroFriendListAutoRefresh.v1";
  const FRIEND_LIST_REFRESH_MS_KEY = "caroFriendListRefreshMs.v1";
  const FRIEND_LIST_ALLOWED_REFRESH_VALUES = [5000, 10000, 15000, 20000, 30000, 60000];
  const PREFERENCES_CHANGED_EVENT = "caro:preferences-changed";
  const USER_CHANGE_EVENT = "caro:user-changed";
  const DEFAULT_AVATAR_PATH = "/uploads/avatars/default-avatar.jpg";
  const USERNAME_PATTERN = /^[A-Za-z0-9._]{6,20}$/;

  function appPath(url) {
    if (window.CaroUrl && typeof window.CaroUrl.path === "function") {
      return window.CaroUrl.path(url);
    }
    return url;
  }

  function readStorage(key, fallbackValue) {
    try {
      const value = window.localStorage.getItem(key);
      return value == null ? fallbackValue : value;
    } catch (_) {
      return fallbackValue;
    }
  }

  function writeStorage(key, value) {
    try {
      window.localStorage.setItem(key, value);
    } catch (_) {
    }
  }

  function readBooleanPref(key, fallbackValue) {
    const raw = readStorage(key, null);
    if (raw == null) {
      return fallbackValue;
    }
    return raw === "1" || raw === "true";
  }

  function writeBooleanPref(key, value) {
    writeStorage(key, value ? "1" : "0");
  }

  function readFriendListRefreshMs() {
    const raw = Number.parseInt(String(readStorage(FRIEND_LIST_REFRESH_MS_KEY, "5000")), 10);
    if (Number.isFinite(raw) && FRIEND_LIST_ALLOWED_REFRESH_VALUES.includes(raw)) {
      return String(raw);
    }
    return "5000";
  }

  function normalizeThemeMode(mode) {
    const value = String(mode || "").trim().toLowerCase();
    if (value === "dark" || value === "light" || value === "system") {
      return value;
    }
    return "light";
  }

  function getThemeMode() {
    if (window.CaroTheme && typeof window.CaroTheme.getMode === "function") {
      return normalizeThemeMode(window.CaroTheme.getMode());
    }
    const fromModeKey = normalizeThemeMode(readStorage(THEME_MODE_KEY, "light"));
    if (fromModeKey !== "light" || String(readStorage(THEME_MODE_KEY, "") || "").trim().toLowerCase() === "light") {
      return fromModeKey;
    }
    const legacy = normalizeThemeMode(readStorage(THEME_KEY, "light"));
    return legacy === "dark" || legacy === "light" ? legacy : "light";
  }

  function applyTheme(mode) {
    const normalizedMode = normalizeThemeMode(mode);
    if (window.CaroTheme && typeof window.CaroTheme.setMode === "function") {
      const result = window.CaroTheme.setMode(normalizedMode);
      return normalizeThemeMode(result && result.mode ? result.mode : normalizedMode);
    }
    const effectiveTheme = normalizedMode === "dark"
      ? "dark"
      : (normalizedMode === "system" && window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
    const root = document.documentElement;
    if (root) {
      root.classList.toggle("dark-mode", effectiveTheme === "dark");
      root.classList.toggle("light-mode", effectiveTheme === "light");
      root.setAttribute("data-theme", effectiveTheme);
      root.setAttribute("data-theme-mode", normalizedMode);
    }
    document.body.classList.toggle("dark-mode", effectiveTheme === "dark");
    document.body.classList.toggle("light-mode", effectiveTheme === "light");
    document.body.dataset.theme = effectiveTheme;
    document.body.dataset.themeMode = normalizedMode;
    writeStorage(THEME_MODE_KEY, normalizedMode);
    writeStorage(THEME_KEY, effectiveTheme);
    return normalizedMode;
  }

  function setStatus(target, message, isSuccess) {
    if (!target) {
      return;
    }
    if (window.CaroUi && typeof window.CaroUi.setStatus === "function") {
      window.CaroUi.setStatus(target, message, isSuccess);
      return;
    }
    target.textContent = String(message || "");
  }

  function notify(message, type) {
    if (window.CaroUi && typeof window.CaroUi.toast === "function") {
      window.CaroUi.toast(message, { type: type || "info" });
    }
  }

  function emitPreferencesChanged(preferences) {
    const detail = preferences && typeof preferences === "object" ? preferences : {};
    try {
      window.dispatchEvent(new CustomEvent(PREFERENCES_CHANGED_EVENT, { detail }));
    } catch (_) {
    }
    try {
      document.dispatchEvent(new CustomEvent(PREFERENCES_CHANGED_EVENT, { detail }));
    } catch (_) {
    }
  }

  document.addEventListener("DOMContentLoaded", () => {
    const root = document.getElementById("settingsRoot");
    if (!root) {
      return;
    }

    const isAuthenticated = String(root.dataset.authenticated || "").toLowerCase() === "true";
    const authUserId = String(root.dataset.authUserId || "").trim();

    const accountForm = document.getElementById("accountSettingsForm");
    const passwordForm = document.getElementById("changePasswordSettingsForm");
    const accountStatus = document.getElementById("accountSettingsStatus");
    const passwordStatus = document.getElementById("passwordSettingsStatus");
    const preferencesStatus = document.getElementById("preferencesStatus");
    const securityStatus = document.getElementById("securitySettingsStatus");
    const socialLinkStatus = document.getElementById("socialLinkStatus");

    const usernameInput = document.getElementById("settingsUsername");
    const usernameHint = document.getElementById("settingsUsernameHint");
    const displayNameInput = document.getElementById("settingsDisplayName");
    const emailInput = document.getElementById("settingsEmail");
    const countryInput = document.getElementById("settingsCountry");
    const genderInput = document.getElementById("settingsGender");
    const birthDateInput = document.getElementById("settingsBirthDate");
    const avatarPathInput = document.getElementById("settingsAvatarPath");
    const avatarPreview = document.getElementById("settingsAvatarPreview");
    const avatarFileInput = document.getElementById("settingsAvatarFile");
    const uploadAvatarBtn = document.getElementById("settingsUploadAvatarBtn");
    const unlinkFacebookBtn = document.getElementById("unlinkFacebookBtn");
    const unlinkGoogleBtn = document.getElementById("unlinkGoogleBtn");

    const currentPasswordInput = document.getElementById("settingsCurrentPassword");
    const newPasswordInput = document.getElementById("settingsNewPassword");
    const confirmPasswordInput = document.getElementById("settingsConfirmPassword");

    // Attach password strength indicator to new-password field
    if (window.PasswordStrength && newPasswordInput) {
      const pwdStrengthContainer = document.getElementById("settingsPasswordStrength");
      window.PasswordStrength.attach(newPasswordInput, pwdStrengthContainer);
    }

    const themeSelect = document.getElementById("themeModeSelect");
    const languageSelect = document.getElementById("languageModeSelect");
    const desktopSidebarVisibleByDefault = document.getElementById("desktopSidebarVisibleByDefault");
    const mobileSidebarAutoClose = document.getElementById("mobileSidebarAutoClose");
    const homeMusicEnabled = document.getElementById("homeMusicEnabled");
    const toastNotificationsEnabled = document.getElementById("toastNotificationsEnabled");
    const showOfflineFriendsInSidebar = document.getElementById("showOfflineFriendsInSidebar");
    const autoRefreshFriendList = document.getElementById("autoRefreshFriendList");
    const friendListRefreshMsSelect = document.getElementById("friendListRefreshMsSelect");
    const savePreferencesBtn = document.getElementById("savePreferencesBtn");
    const resetPreferencesBtn = document.getElementById("resetPreferencesBtn");
    const exportPreferencesBtn = document.getElementById("exportPreferencesBtn");
    const importPreferencesBtn = document.getElementById("importPreferencesBtn");
    const importPreferencesFileInput = document.getElementById("importPreferencesFileInput");

    const activateAdminCodeInput = document.getElementById("settingsAdminCode");
    const activateAdminFromSettingsBtn = document.getElementById("activateAdminFromSettingsBtn");

    const i18n = window.CaroI18n;

    const normalizeAvatarPath = (value) => {
      const raw = String(value || "").trim();
      return raw || DEFAULT_AVATAR_PATH;
    };

    let previewObjectUrl = null;
    let usernameCheckTimer = null;

    const releasePreviewObjectUrl = () => {
      if (previewObjectUrl) {
        window.URL.revokeObjectURL(previewObjectUrl);
        previewObjectUrl = null;
      }
    };

    const updateAvatarPreview = (value) => {
      if (!avatarPreview) {
        return;
      }
      releasePreviewObjectUrl();
      avatarPreview.src = appPath(normalizeAvatarPath(value));
    };

    const updateAvatarPreviewFromFile = (file) => {
      if (!avatarPreview || !file) {
        return;
      }
      releasePreviewObjectUrl();
      previewObjectUrl = window.URL.createObjectURL(file);
      avatarPreview.src = previewObjectUrl;
    };

    const setUsernameHint = (message, ok) => {
      if (!usernameHint) {
        return;
      }
      usernameHint.textContent = String(message || "");
      usernameHint.classList.toggle("text-success", !!ok);
      usernameHint.classList.toggle("text-danger", ok === false);
    };

    const normalizeUsername = (value) => String(value || "").trim().replace(/^@+/, "");

    const validateUsername = async (silent) => {
      const candidate = normalizeUsername(usernameInput?.value || "");
      if (!candidate) {
        setUsernameHint("Ten nguoi dung la bat buoc.", false);
        return false;
      }
      if (!USERNAME_PATTERN.test(candidate)) {
        setUsernameHint("Ten nguoi dung can 6-20 ky tu va chi dung chu cai, so, . hoac _.", false);
        return false;
      }
      if (!silent) {
        setUsernameHint("Dang kiem tra ten nguoi dung...", null);
      }
      try {
        const query = new URLSearchParams({ username: candidate });
        if (authUserId) {
          query.set("userId", authUserId);
        }
        const res = await fetch(appPath("/account/username-availability?" + query.toString()), { cache: "no-store" });
        const data = await res.json().catch(() => ({}));
        const ok = !!(data && data.success);
        if (ok) {
          setUsernameHint("Ten nguoi dung hop le va co san.", true);
          return true;
        }
        setUsernameHint(String(data?.error || "Ten nguoi dung khong hop le"), false);
        return false;
      } catch (_) {
        if (!silent) {
          setUsernameHint("Khong the kiem tra ten nguoi dung luc nay.", false);
        }
        return false;
      }
    };

    const buildPreferencesPayload = () => {
      const refreshValue = Number.parseInt(String(friendListRefreshMsSelect?.value || "5000"), 10);
      const selectedThemeMode = normalizeThemeMode(themeSelect?.value || getThemeMode());
      return {
        version: 1,
        savedAt: new Date().toISOString(),
        themeMode: selectedThemeMode,
        theme: selectedThemeMode,
        language: languageSelect && languageSelect.value === "en" ? "en" : "vi",
        sidebarDesktopVisibleByDefault: !!(desktopSidebarVisibleByDefault && desktopSidebarVisibleByDefault.checked),
        sidebarMobileAutoClose: !!(mobileSidebarAutoClose && mobileSidebarAutoClose.checked),
        homeMusicEnabled: !!(homeMusicEnabled && homeMusicEnabled.checked),
        toastNotificationsEnabled: !!(toastNotificationsEnabled && toastNotificationsEnabled.checked),
        showOfflineFriendsInSidebar: !!(showOfflineFriendsInSidebar && showOfflineFriendsInSidebar.checked),
        autoRefreshFriendList: !!(autoRefreshFriendList && autoRefreshFriendList.checked),
        friendListRefreshMs:
          Number.isFinite(refreshValue) && FRIEND_LIST_ALLOWED_REFRESH_VALUES.includes(refreshValue)
            ? refreshValue
            : 5000
      };
    };

    const toServerPreferencesPayload = (source) => {
      const data = source || {};
      return {
        themeMode: normalizeThemeMode(data.themeMode || data.theme || "system"),
        language: data.language === "en" ? "en" : "vi",
        sidebarDesktopVisibleByDefault: !!data.sidebarDesktopVisibleByDefault,
        sidebarMobileAutoClose: data.sidebarMobileAutoClose !== false,
        homeMusicEnabled: data.homeMusicEnabled !== false,
        toastNotificationsEnabled: data.toastNotificationsEnabled !== false,
        showOfflineFriendsInSidebar: data.showOfflineFriendsInSidebar !== false,
        autoRefreshFriendList: data.autoRefreshFriendList !== false,
        friendListRefreshMs: FRIEND_LIST_ALLOWED_REFRESH_VALUES.includes(Number(data.friendListRefreshMs))
          ? Number(data.friendListRefreshMs)
          : 5000
      };
    };

    const applyPreferencesPayload = (prefs, persistChanges) => {
      const data = prefs || {};
      const themeMode = normalizeThemeMode(data.themeMode || data.theme || getThemeMode());
      const appliedThemeMode = applyTheme(themeMode);
      if (themeSelect) {
        themeSelect.value = appliedThemeMode;
      }

      const language = data.language === "en" ? "en" : "vi";
      if (languageSelect) {
        languageSelect.value = language;
      }
      if (i18n && typeof i18n.setLanguage === "function") {
        try {
          i18n.setLanguage(language);
        } catch (_) {
        }
      }

      if (desktopSidebarVisibleByDefault) {
        desktopSidebarVisibleByDefault.checked = !!data.sidebarDesktopVisibleByDefault;
      }
      if (mobileSidebarAutoClose) {
        mobileSidebarAutoClose.checked = data.sidebarMobileAutoClose !== false;
      }
      if (homeMusicEnabled) {
        homeMusicEnabled.checked = data.homeMusicEnabled !== false;
      }
      if (toastNotificationsEnabled) {
        toastNotificationsEnabled.checked = data.toastNotificationsEnabled !== false;
      }
      if (showOfflineFriendsInSidebar) {
        showOfflineFriendsInSidebar.checked = data.showOfflineFriendsInSidebar !== false;
      }
      if (autoRefreshFriendList) {
        autoRefreshFriendList.checked = data.autoRefreshFriendList !== false;
      }
      if (friendListRefreshMsSelect) {
        const refreshMs = Number.parseInt(String(data.friendListRefreshMs || 5000), 10);
        friendListRefreshMsSelect.value =
          Number.isFinite(refreshMs) && FRIEND_LIST_ALLOWED_REFRESH_VALUES.includes(refreshMs)
            ? String(refreshMs)
            : "5000";
      }
      syncFriendRefreshSelectState();

      if (!persistChanges) {
        return;
      }
      const normalized = buildPreferencesPayload();
      writeBooleanPref(SIDEBAR_DESKTOP_HIDDEN_KEY, !normalized.sidebarDesktopVisibleByDefault);
      writeBooleanPref(SIDEBAR_MOBILE_AUTO_CLOSE_KEY, normalized.sidebarMobileAutoClose);
      writeStorage(MUSIC_KEY, normalized.homeMusicEnabled ? "on" : "off");
      writeBooleanPref(TOAST_ENABLED_KEY, normalized.toastNotificationsEnabled);
      writeBooleanPref(FRIEND_LIST_SHOW_OFFLINE_KEY, normalized.showOfflineFriendsInSidebar);
      writeBooleanPref(FRIEND_LIST_AUTO_REFRESH_KEY, normalized.autoRefreshFriendList);
      writeStorage(FRIEND_LIST_REFRESH_MS_KEY, String(normalized.friendListRefreshMs));
      emitPreferencesChanged(normalized);
    };

    const syncFriendRefreshSelectState = () => {
      if (!friendListRefreshMsSelect || !autoRefreshFriendList) {
        return;
      }
      friendListRefreshMsSelect.disabled = !autoRefreshFriendList.checked;
    };

    const currentTheme = getThemeMode();
    if (themeSelect) {
      themeSelect.value = currentTheme;
    }
    applyTheme(currentTheme);

    if (languageSelect) {
      languageSelect.value = (i18n && typeof i18n.getLanguage === "function") ? (i18n.getLanguage() || "vi") : "vi";
    }
    if (i18n && typeof i18n.onChange === "function") {
      i18n.onChange((lang) => {
        if (languageSelect) {
          languageSelect.value = String(lang || "vi");
        }
      });
    }

    if (desktopSidebarVisibleByDefault) {
      desktopSidebarVisibleByDefault.checked = !readBooleanPref(SIDEBAR_DESKTOP_HIDDEN_KEY, true);
    }
    if (mobileSidebarAutoClose) {
      mobileSidebarAutoClose.checked = readBooleanPref(SIDEBAR_MOBILE_AUTO_CLOSE_KEY, true);
    }
    if (homeMusicEnabled) {
      homeMusicEnabled.checked = readStorage(MUSIC_KEY, "on") !== "off";
    }
    if (toastNotificationsEnabled) {
      toastNotificationsEnabled.checked = readBooleanPref(TOAST_ENABLED_KEY, true);
    }
    if (showOfflineFriendsInSidebar) {
      showOfflineFriendsInSidebar.checked = readBooleanPref(FRIEND_LIST_SHOW_OFFLINE_KEY, true);
    }
    if (autoRefreshFriendList) {
      autoRefreshFriendList.checked = readBooleanPref(FRIEND_LIST_AUTO_REFRESH_KEY, true);
    }
    if (friendListRefreshMsSelect) {
      friendListRefreshMsSelect.value = readFriendListRefreshMs();
    }
    syncFriendRefreshSelectState();

    themeSelect?.addEventListener("change", () => {
      const appliedMode = applyTheme(themeSelect.value);
      if (themeSelect) {
        themeSelect.value = appliedMode;
      }
    });
    window.CaroTheme?.onChange?.((event) => {
      if (!themeSelect) {
        return;
      }
      themeSelect.value = normalizeThemeMode(event && event.mode ? event.mode : getThemeMode());
    });

    languageSelect?.addEventListener("change", () => {
      if (!i18n || typeof i18n.setLanguage !== "function") {
        return;
      }
      try {
        i18n.setLanguage(languageSelect.value === "en" ? "en" : "vi");
      } catch (_) {
      }
    });

    autoRefreshFriendList?.addEventListener("change", syncFriendRefreshSelectState);

    const persistPreferencesToServer = async (payload) => {
      const response = await fetch(appPath("/account/preferences"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(toServerPreferencesPayload(payload))
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok || !data.success) {
        throw new Error(String(data?.error || "Khong the luu tuy chon vao tai khoan"));
      }
      return toServerPreferencesPayload(data.data || payload);
    };

    const loadPreferencesFromServer = async () => {
      if (!isAuthenticated || !authUserId) {
        return;
      }
      try {
        const response = await fetch(appPath("/account/preferences"), { cache: "no-store" });
        const data = await response.json().catch(() => ({}));
        if (!response.ok || !data.success || !data.data) {
          return;
        }
        applyPreferencesPayload(data.data, true);
      } catch (_) {
      }
    };

    const persistPreferences = async (payload, successMessage) => {
      applyPreferencesPayload(payload, true);
      if (isAuthenticated && authUserId) {
        const stored = await persistPreferencesToServer(payload);
        applyPreferencesPayload(stored, true);
        setStatus(preferencesStatus, successMessage + " (da dong bo tai khoan).", true);
      } else {
        setStatus(preferencesStatus, successMessage, true);
      }
      if (toastNotificationsEnabled && toastNotificationsEnabled.checked) {
        notify("Da luu cai dat", "success");
      }
    };

    const savePreferences = async () => {
      try {
        await persistPreferences(
          buildPreferencesPayload(),
          "Da luu tuy chon. Sidebar va noi dung da duoc dong bo ngay."
        );
      } catch (error) {
        const message = String(error?.message || error || "Khong the luu cai dat");
        setStatus(preferencesStatus, message, false);
        notify(message, "danger");
      }
    };

    savePreferencesBtn?.addEventListener("click", savePreferences);

    resetPreferencesBtn?.addEventListener("click", async () => {
      try {
        await persistPreferences({
          themeMode: "system",
          language: "vi",
          sidebarDesktopVisibleByDefault: false,
          sidebarMobileAutoClose: true,
          homeMusicEnabled: true,
          toastNotificationsEnabled: true,
          showOfflineFriendsInSidebar: true,
          autoRefreshFriendList: true,
          friendListRefreshMs: 5000
        }, "Da dua tat ca tuy chon ve mac dinh.");
      } catch (error) {
        const message = String(error?.message || error || "Khong the reset cai dat");
        setStatus(preferencesStatus, message, false);
        notify(message, "danger");
      }
    });

    exportPreferencesBtn?.addEventListener("click", () => {
      try {
        const payload = buildPreferencesPayload();
        const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = "caro-settings.json";
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
        setStatus(preferencesStatus, "Da xuat tep cai dat JSON.", true);
      } catch (error) {
        setStatus(preferencesStatus, "Khong the xuat cai dat.", false);
      }
    });

    importPreferencesBtn?.addEventListener("click", () => {
      importPreferencesFileInput?.click?.();
    });

    importPreferencesFileInput?.addEventListener("change", () => {
      const file = importPreferencesFileInput.files && importPreferencesFileInput.files[0];
      if (!file) {
        return;
      }
      const reader = new FileReader();
      reader.onload = async () => {
        try {
          const parsed = JSON.parse(String(reader.result || "{}"));
          await persistPreferences(parsed, "Da import cai dat thanh cong.");
        } catch (error) {
          const message = error instanceof SyntaxError
            ? "File cai dat khong hop le."
            : String(error?.message || error || "Khong the import cai dat");
          setStatus(preferencesStatus, message, false);
        } finally {
          importPreferencesFileInput.value = "";
        }
      };
      reader.onerror = () => {
        setStatus(preferencesStatus, "Khong doc duoc tep cai dat.", false);
        importPreferencesFileInput.value = "";
      };
      reader.readAsText(file);
    });

    loadPreferencesFromServer();
    if (usernameInput?.value) {
      setUsernameHint("Ten nguoi dung can 6-20 ky tu, chi dung chu cai, so, . va _.", null);
      void validateUsername(true);
    }
    updateAvatarPreview(avatarPathInput?.value || DEFAULT_AVATAR_PATH);
    avatarPathInput?.addEventListener("input", () => {
      updateAvatarPreview(avatarPathInput.value);
    });
    avatarFileInput?.addEventListener("change", () => {
      const file = avatarFileInput.files && avatarFileInput.files[0];
      if (!file) {
        updateAvatarPreview(avatarPathInput?.value || DEFAULT_AVATAR_PATH);
        return;
      }
      updateAvatarPreviewFromFile(file);
    });
    usernameInput?.addEventListener("input", () => {
      if (usernameCheckTimer) {
        clearTimeout(usernameCheckTimer);
      }
      setUsernameHint("Ten nguoi dung can 6-20 ky tu, chi dung chu cai, so, . va _.", null);
      usernameCheckTimer = window.setTimeout(() => {
        void validateUsername(true);
      }, 260);
    });
    usernameInput?.addEventListener("blur", () => {
      void validateUsername(false);
    });
    window.addEventListener(USER_CHANGE_EVENT, (event) => {
      const user = event?.detail?.user;
      if (user && usernameInput && document.activeElement !== usernameInput) {
        usernameInput.value = user.username || user.displayName || "";
      }
      if (user && displayNameInput && document.activeElement !== displayNameInput) {
        displayNameInput.value = user.displayName || usernameInput?.value || "Nguoi choi";
      }
      if (user && emailInput && document.activeElement !== emailInput) {
        emailInput.value = user.email || "";
      }
      if (user && countryInput && document.activeElement !== countryInput) {
        countryInput.value = user.country || "";
      }
      if (user && genderInput && document.activeElement !== genderInput) {
        genderInput.value = user.gender || "";
      }
      if (user && birthDateInput && document.activeElement !== birthDateInput) {
        birthDateInput.value = user.birthDate || "";
      }
      if (user && user.avatarPath && avatarPathInput && document.activeElement !== avatarPathInput) {
        avatarPathInput.value = user.avatarPath;
      }
      updateAvatarPreview(user?.avatarPath || avatarPathInput?.value || DEFAULT_AVATAR_PATH);
    });
    window.addEventListener("beforeunload", releasePreviewObjectUrl);

    uploadAvatarBtn?.addEventListener("click", async () => {
      if (!isAuthenticated || !authUserId) {
        setStatus(accountStatus, "Ban can dang nhap de tai avatar.", false);
        return;
      }
      const file = avatarFileInput?.files && avatarFileInput.files[0];
      if (!file) {
        setStatus(accountStatus, "Vui long chon tep avatar.", false);
        return;
      }

      uploadAvatarBtn.disabled = true;
      try {
        const body = new FormData();
        body.append("avatar", file);
        const res = await fetch(appPath("/account/avatar"), {
          method: "POST",
          body
        });
        const data = await res.json().catch(() => ({}));
        const ok = !!(data && data.success);
        const updated = data?.data || {};
        const message = ok
          ? String(updated.message || "Tai avatar thanh cong")
          : String(data?.error || "Khong the tai avatar");
        setStatus(accountStatus, message, ok);
        notify(message, ok ? "success" : "danger");
        if (!ok) {
          updateAvatarPreview(avatarPathInput?.value || DEFAULT_AVATAR_PATH);
          return;
        }

        const current = window.CaroUser?.get?.() || { userId: authUserId };
        const resolvedAvatarPath = updated.avatarPath || current.avatarPath || DEFAULT_AVATAR_PATH;
        if (avatarPathInput) {
          avatarPathInput.value = resolvedAvatarPath;
        }
        if (avatarFileInput) {
          avatarFileInput.value = "";
        }
        updateAvatarPreview(resolvedAvatarPath);
        window.CaroUser?.set?.({
          userId: updated.userId || current.userId || authUserId,
          username: updated.username || current.username || usernameInput?.value || updated.displayName || "Nguoi choi",
          displayName: updated.displayName || current.displayName || "Nguoi choi",
          email: updated.email || current.email || "",
          role: updated.role || current.role || "User",
          avatarPath: resolvedAvatarPath,
          country: updated.country || current.country || countryInput?.value || "",
          gender: updated.gender || current.gender || genderInput?.value || "",
          birthDate: updated.birthDate || current.birthDate || birthDateInput?.value || "",
          onboardingCompleted: updated.onboardingCompleted === true || current.onboardingCompleted === true
        });
      } catch (error) {
        const message = String(error?.message || error || "Khong the tai avatar");
        setStatus(accountStatus, message, false);
        notify(message, "danger");
        updateAvatarPreview(avatarPathInput?.value || DEFAULT_AVATAR_PATH);
      } finally {
        uploadAvatarBtn.disabled = false;
      }
    });

    accountForm?.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (!isAuthenticated || !authUserId) {
        setStatus(accountStatus, "Ban can dang nhap de cap nhat tai khoan.", false);
        return;
      }
      const usernameOk = await validateUsername(false);
      if (!usernameOk) {
        setStatus(accountStatus, "Vui long nhap ten nguoi dung hop le.", false);
        return;
      }

      const payload = {
        userId: authUserId,
        username: normalizeUsername(usernameInput?.value || ""),
        displayName: String(displayNameInput?.value || "").trim(),
        email: String(emailInput?.value || "").trim(),
        avatarPath: String(avatarPathInput?.value || "").trim(),
        country: String(countryInput?.value || "").trim(),
        gender: String(genderInput?.value || "").trim(),
        birthDate: String(birthDateInput?.value || "").trim()
      };

      try {
        const res = await fetch(appPath("/account/update-profile"), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload)
        });
        const data = await res.json();
        const ok = !!(data && data.success);
        const message = ok
          ? String(data?.data?.message || "Da cap nhat ho so")
          : String(data?.error || "Khong the cap nhat ho so");
        setStatus(accountStatus, message, ok);
        notify(message, ok ? "success" : "danger");
        if (!ok) {
          return;
        }

        const current = window.CaroUser?.get?.() || { userId: authUserId };
        const updated = data.data || {};
        window.CaroUser?.set?.({
          userId: updated.userId || current.userId || authUserId,
          username: updated.username || payload.username || current.username || payload.displayName || "Nguoi choi",
          displayName: updated.displayName || payload.displayName || current.displayName || "Nguoi choi",
          email: updated.email || payload.email || current.email || "",
          role: updated.role || current.role || "User",
          avatarPath: updated.avatarPath || payload.avatarPath || current.avatarPath || DEFAULT_AVATAR_PATH,
          country: updated.country || payload.country || current.country || "",
          gender: updated.gender || payload.gender || current.gender || "",
          birthDate: updated.birthDate || payload.birthDate || current.birthDate || "",
          onboardingCompleted: updated.onboardingCompleted === true
        });
        updateAvatarPreview(updated.avatarPath || payload.avatarPath || current.avatarPath || DEFAULT_AVATAR_PATH);
      } catch (error) {
        const message = String(error?.message || error || "Khong the cap nhat ho so");
        setStatus(accountStatus, message, false);
        notify(message, "danger");
      }
    });

    passwordForm?.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (!isAuthenticated || !authUserId) {
        setStatus(passwordStatus, "Ban can dang nhap de doi mat khau.", false);
        return;
      }

      const currentPassword = String(currentPasswordInput?.value || "");
      const newPassword = String(newPasswordInput?.value || "");
      const confirmPassword = String(confirmPasswordInput?.value || "");

      if (!currentPassword || !newPassword) {
        setStatus(passwordStatus, "Vui long nhap day du mat khau.", false);
        return;
      }
      if (window.PasswordStrength && !window.PasswordStrength.allPassed(newPassword)) {
        setStatus(passwordStatus, "Mat khau moi chua dat yeu cau bao mat.", false);
        return;
      }
      if (newPassword !== confirmPassword) {
        setStatus(passwordStatus, "Nhap lai mat khau moi khong khop.", false);
        return;
      }

      try {
        const res = await fetch(appPath("/account/change-password"), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            userId: authUserId,
            currentPassword: currentPassword,
            newPassword: newPassword
          })
        });
        const data = await res.json();
        const ok = !!(data && data.success);
        const message = ok
          ? String(data?.data?.message || "Da doi mat khau")
          : String(data?.error || "Khong the doi mat khau");
        setStatus(passwordStatus, message, ok);
        notify(message, ok ? "success" : "danger");
        if (ok) {
          if (currentPasswordInput) currentPasswordInput.value = "";
          if (newPasswordInput) newPasswordInput.value = "";
          if (confirmPasswordInput) confirmPasswordInput.value = "";
        }
      } catch (error) {
        const message = String(error?.message || error || "Khong the doi mat khau");
        setStatus(passwordStatus, message, false);
        notify(message, "danger");
      }
    });

    activateAdminFromSettingsBtn?.addEventListener("click", async () => {
      if (!isAuthenticated || !authUserId) {
        setStatus(securityStatus, "Ban can dang nhap de kich hoat quyen quan tri.", false);
        return;
      }
      const activationCode = String(activateAdminCodeInput?.value || "").trim();
      if (!activationCode) {
        setStatus(securityStatus, "Vui long nhap ma kich hoat quan tri.", false);
        return;
      }

      activateAdminFromSettingsBtn.disabled = true;
      try {
        const response = await fetch(appPath("/account/activate-admin"), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ userId: authUserId, activationCode: activationCode })
        });
        const payload = await response.json();
        const success = !!(payload && payload.success);
        const message = success
          ? String(payload?.data?.message || "Da kich hoat quyen quan tri")
          : String(payload?.error || "Khong the kich hoat quyen quan tri");

        setStatus(securityStatus, message, success);
        notify(message, success ? "success" : "danger");

        if (!success) {
          return;
        }

        const current = window.CaroUser?.get?.() || { userId: authUserId };
        window.CaroUser?.set?.({
          userId: current.userId || authUserId,
          username: payload?.data?.username || current.username || usernameInput?.value || current.displayName || "Nguoi choi",
          displayName: payload?.data?.displayName || current.displayName || "Nguoi choi",
          email: payload?.data?.email || current.email || "",
          role: "Admin",
          avatarPath: payload?.data?.avatarPath || current.avatarPath || "/uploads/avatars/default-avatar.jpg",
          country: payload?.data?.country || current.country || "",
          gender: payload?.data?.gender || current.gender || "",
          birthDate: payload?.data?.birthDate || current.birthDate || "",
          onboardingCompleted: payload?.data?.onboardingCompleted === true || current.onboardingCompleted === true
        });

        if (activateAdminCodeInput) {
          activateAdminCodeInput.value = "";
        }
      } catch (error) {
        const message = String(error?.message || error || "Khong the kich hoat quyen quan tri");
        setStatus(securityStatus, message, false);
        notify(message, "danger");
      } finally {
        activateAdminFromSettingsBtn.disabled = false;
      }
    });

    const bindUnlinkSocialProvider = (button, provider, providerLabel) => {
      button?.addEventListener("click", async () => {
        if (!isAuthenticated || !authUserId) {
          setStatus(socialLinkStatus, "Ban can dang nhap de huy lien ket " + providerLabel + ".", false);
          return;
        }

        button.disabled = true;
        try {
          const response = await fetch(appPath("/account/social/" + provider + "/unlink"), {
            method: "POST"
          });
          const payload = await response.json().catch(() => ({}));
          const success = !!(payload && payload.success);
          const message = success
            ? String(payload?.data?.message || ("Da huy lien ket " + providerLabel))
            : String(payload?.error || ("Khong the huy lien ket " + providerLabel));

          setStatus(socialLinkStatus, message, success);
          notify(message, success ? "success" : "danger");

          if (success) {
            window.setTimeout(() => window.location.reload(), 300);
          }
        } catch (error) {
          const message = String(error?.message || error || ("Khong the huy lien ket " + providerLabel));
          setStatus(socialLinkStatus, message, false);
          notify(message, "danger");
        } finally {
          button.disabled = false;
        }
      });
    };

    bindUnlinkSocialProvider(unlinkFacebookBtn, "facebook", "Facebook");
    bindUnlinkSocialProvider(unlinkGoogleBtn, "google", "Google");
  });
})();
