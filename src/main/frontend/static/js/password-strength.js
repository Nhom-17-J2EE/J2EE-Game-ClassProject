/**
 * Shared password-strength module.
 *
 * Usage:
 *   PasswordStrength.attach(passwordInput, containerEl);
 *
 * `containerEl` should be an empty <div> that will be populated with the
 * rule checklist and the strength meter bar.
 *
 * The module also exposes:
 *   PasswordStrength.validate(password)   -> { rules, score, label, allPassed }
 *   PasswordStrength.allPassed(password)  -> boolean
 */
window.PasswordStrength = (function () {
  "use strict";

  var MIN_LENGTH = 8;

  /* ── rule definitions ─────────────────────────────────────────── */

  var RULES = [
    { key: "length",    label: "It nhat " + MIN_LENGTH + " ky tu",          test: function (p) { return p.length >= MIN_LENGTH; } },
    { key: "uppercase", label: "Co it nhat 1 chu in hoa (A-Z)",             test: function (p) { return /[A-Z]/.test(p); } },
    { key: "lowercase", label: "Co it nhat 1 chu thuong (a-z)",             test: function (p) { return /[a-z]/.test(p); } },
    { key: "digit",     label: "Co it nhat 1 chu so (0-9)",                 test: function (p) { return /[0-9]/.test(p); } },
    { key: "special",   label: "Co it nhat 1 ky tu dac biet (!@#$...)",     test: function (p) { return /[^A-Za-z0-9]/.test(p); } }
  ];

  /* ── strength labels ──────────────────────────────────────────── */

  var STRENGTH_LEVELS = [
    { min: 0, label: "",        cls: "" },
    { min: 1, label: "Yeu",     cls: "pwd-weak" },
    { min: 2, label: "Trung binh", cls: "pwd-fair" },
    { min: 4, label: "Manh",    cls: "pwd-good" },
    { min: 5, label: "Rat manh", cls: "pwd-strong" }
  ];

  function strengthLevel(score) {
    for (var i = STRENGTH_LEVELS.length - 1; i >= 0; i--) {
      if (score >= STRENGTH_LEVELS[i].min) return STRENGTH_LEVELS[i];
    }
    return STRENGTH_LEVELS[0];
  }

  /* ── core validate ────────────────────────────────────────────── */

  function validate(password) {
    var p = String(password || "");
    var results = [];
    var score = 0;
    for (var i = 0; i < RULES.length; i++) {
      var passed = p.length > 0 && RULES[i].test(p);
      if (passed) score++;
      results.push({ key: RULES[i].key, label: RULES[i].label, passed: passed });
    }
    var level = strengthLevel(score);
    return { rules: results, score: score, label: level.label, cls: level.cls, allPassed: score === RULES.length };
  }

  function allPassed(password) {
    return validate(password).allPassed;
  }

  /* ── DOM builder ──────────────────────────────────────────────── */

  function buildUI(container) {
    container.innerHTML = "";

    // rules list
    var rulesWrap = document.createElement("div");
    rulesWrap.className = "pwd-rules";
    var ruleEls = {};
    for (var i = 0; i < RULES.length; i++) {
      var rule = document.createElement("div");
      rule.className = "pwd-rule";
      rule.setAttribute("data-pwd-rule", RULES[i].key);
      rule.innerHTML = '<i class="bi bi-check2"></i> <span>' + RULES[i].label + "</span>";
      rulesWrap.appendChild(rule);
      ruleEls[RULES[i].key] = rule;
    }
    container.appendChild(rulesWrap);

    // meter
    var meterWrap = document.createElement("div");
    meterWrap.className = "pwd-meter";
    var meterTrack = document.createElement("div");
    meterTrack.className = "pwd-meter__track";
    var meterBar = document.createElement("div");
    meterBar.className = "pwd-meter__bar";
    meterTrack.appendChild(meterBar);
    meterWrap.appendChild(meterTrack);
    var meterLabel = document.createElement("span");
    meterLabel.className = "pwd-meter__label";
    meterWrap.appendChild(meterLabel);
    container.appendChild(meterWrap);

    return { ruleEls: ruleEls, meterBar: meterBar, meterLabel: meterLabel, meterWrap: meterWrap };
  }

  /* ── attach to input ──────────────────────────────────────────── */

  function attach(inputEl, containerEl) {
    if (!inputEl || !containerEl) return;

    var ui = buildUI(containerEl);

    function sync() {
      var result = validate(inputEl.value);
      for (var i = 0; i < result.rules.length; i++) {
        var r = result.rules[i];
        var el = ui.ruleEls[r.key];
        if (el) {
          el.classList.toggle("is-valid", r.passed);
        }
      }
      var pct = (result.score / RULES.length) * 100;
      ui.meterBar.style.width = pct + "%";

      // remove all level classes
      ui.meterWrap.classList.remove("pwd-weak", "pwd-fair", "pwd-good", "pwd-strong");
      if (result.cls) {
        ui.meterWrap.classList.add(result.cls);
      }
      ui.meterLabel.textContent = result.label;
    }

    inputEl.addEventListener("input", sync);
    inputEl.addEventListener("focus", sync);

    // initial sync
    sync();
  }

  /* ── public API ───────────────────────────────────────────────── */

  return {
    MIN_LENGTH: MIN_LENGTH,
    RULES: RULES,
    validate: validate,
    allPassed: allPassed,
    attach: attach
  };
})();
