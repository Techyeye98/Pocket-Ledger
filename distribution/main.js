/* Pocket Ledger — marketing site
 * Motion system: native CSS scroll-driven animation when available, a small
 * IntersectionObserver fallback otherwise, and rAF-driven scroll scribing for
 * the pinned architecture section. Zero runtime dependencies. */
(function () {
  "use strict";

  /* ---------------------------------------------------------------- helpers */
  var $ = function (s, c) { return (c || document).querySelector(s); };
  var $$ = function (s, c) { return Array.prototype.slice.call((c || document).querySelectorAll(s)); };
  var clamp = function (v, a, b) { return Math.min(b, Math.max(a, v)); };
  var lerp = function (a, b, t) { return a + (b - a) * t; };
  var fmtINR = function (n) {
    return "₹" + n.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };
  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var viewTimeline = (function () {
    try { return CSS.supports("animation-timeline", "view()"); } catch (e) { return false; }
  })();

  /* soften in-page anchors for the fixed header */
  (function anchors() {
    var sheet = document.createElement("style");
    sheet.textContent = "main [id], footer [id] { scroll-margin-top: 88px; }";
    document.head.appendChild(sheet);
  })();

  /* ------------------------------------------------------------ cinematic veil */
  (function veil() {
    var veilEl = $("#veil");
    if (!veilEl) return;
    if (reduced) {
      veilEl.style.display = "none";
      document.documentElement.classList.add("go");
      var m = $("main"); if (m) m.classList.add("live");
      return;
    }
    var done = false;
    function dismiss() {
      if (done) return;
      done = true;
      veilEl.classList.add("is-done");
      veilEl.setAttribute("aria-hidden", "true");
      document.documentElement.classList.add("go");
      var m = $("main"); if (m) m.classList.add("live");
      setTimeout(function () { veilEl.style.display = "none"; }, 900);
    }
    var skip = $("#veilSkip");
    if (skip) skip.addEventListener("click", dismiss);
    if (document.readyState !== "loading") {
      setTimeout(dismiss, 1500);
    } else {
      window.addEventListener("load", function () { setTimeout(dismiss, 1500); });
    }
  })();

  /* ---------------------------------------------------------- theme toggle */
  (function theme() {
    var root = document.documentElement;
    var btn = $("#themeToggle");
    var stored = null;
    try { stored = localStorage.getItem("pl-theme"); } catch (e) {}
    var theme = stored || (window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark");
    function apply(t) {
      root.setAttribute("data-theme", t);
      var sun = $(".sun", btn), moon = $(".moon", btn);
      if (sun) sun.style.display = t === "dark" ? "" : "none";
      if (moon) moon.style.display = t === "light" ? "" : "none";
      try { localStorage.setItem("pl-theme", t); } catch (e) {}
    }
    apply(theme);
    if (btn) btn.addEventListener("click", function () {
      apply(root.getAttribute("data-theme") === "dark" ? "light" : "dark");
    });
  })();

  /* ------------------------------------------------- nav scroll + progress */
  (function nav() {
    var navEl = $("#nav");
    var bar = $("#navProgress");
    var scrollHint = $(".scroll-hint");
    function onScroll() {
      var y = window.scrollY || window.pageYOffset;
      if (navEl) navEl.classList.toggle("scrolled", y > 40);
      var max = document.documentElement.scrollHeight - window.innerHeight;
      if (bar) bar.style.transform = "scaleX(" + clamp(max > 0 ? y / max : 0, 0, 1) + ")";
      if (scrollHint) scrollHint.style.opacity = String(clamp(1 - y / 140, 0, 1));
    }
    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();

    var burger = $("#burger");
    var menu = $("#mobileMenu");
    if (burger && menu) {
      function closeMenu() {
        burger.setAttribute("aria-expanded", "false");
        menu.classList.remove("open");
        document.body.style.overflow = "";
      }
      burger.addEventListener("click", function () {
        var open = burger.getAttribute("aria-expanded") === "true";
        burger.setAttribute("aria-expanded", String(!open));
        menu.classList.toggle("open", !open);
        document.body.style.overflow = open ? "" : "hidden";
      });
      $$("a", menu).forEach(function (a) { a.addEventListener("click", closeMenu); });
    }
  })();

  /* ------------------------------------------------ reveal-on-scroll (.rv) */
  (function reveals() {
    var main = $("main");
    if (!main) return;
    if (viewTimeline) return; /* native CSS scroll-driven animation handles it */
    var els = $$(".rv", main);
    if (!els.length || !("IntersectionObserver" in window)) {
      els.forEach(function (el) { el.classList.add("is-in"); });
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) {
          en.target.classList.add("is-in");
          io.unobserve(en.target);
        }
      });
    }, { threshold: 0.12, rootMargin: "0px 0px -8% 0px" });
    els.forEach(function (el) { io.observe(el); });
  })();

  /* ----------------------------------------------------- hero stat counters */
  (function counters() {
    var proof = $(".hero-proof");
    if (!proof || reduced) return;
    var nums = $$(".pt-value .w[data-count]", proof);
    var started = false;
    function run() {
      if (started) return;
      started = true;
      nums.forEach(function (el) {
        var target = parseFloat(el.getAttribute("data-count")) || 0;
        var t0 = null, dur = 1300;
        function tick(ts) {
          if (!t0) t0 = ts;
          var p = clamp((ts - t0) / dur, 0, 1);
          var eased = 1 - Math.pow(1 - p, 3);
          el.textContent = Math.round(target * eased);
          if (p < 1) requestAnimationFrame(tick);
          else el.textContent = String(target);
        }
        requestAnimationFrame(tick);
      });
    }
    if ("IntersectionObserver" in window) {
      var io = new IntersectionObserver(function (entries) {
        entries.forEach(function (en) { if (en.isIntersecting) { run(); io.disconnect(); } });
      }, { threshold: 0.5 });
      io.observe(proof);
    } else if (proof.getBoundingClientRect().top < window.innerHeight) {
      run();
    }
  })();

  /* ------------------------------------------------- live hero demo panel */
  (function demo() {
    var card = $("#demoCard"), rowsEl = $("#demoRows"), amtEl = $("#demoAmt"),
        input = $("#demoInput"), logBtn = $("#demoLog"), avgEl = $(".demo-total .avg"),
        segs = $$(".donut .seg"), legendEl = $(".donut-legend");
    if (!card) return;

    /* Exactly three categories — Petrol, Groceries, Education. No period adds a
       fourth. Every displayed number comes from `entries`: the total is their sum,
       and the donut/legend percentages are derived from the same amounts, so the
       bell, the ring and the rows can never disagree. */
    var PERIODS = {
      "Today": {
        entries: [
          { ic: "◆", name: "Petrol", sub: "Jupiter · 12-09-26", amt: 411.75 },
          { ic: "✧", name: "Groceries", sub: "Amul, chaas · 13-09-26", amt: 89 },
          { ic: "⌑", name: "Education", sub: "Course fee · 11-09-26", amt: 99 }
        ]
      },
      "Week": {
        entries: [
          { ic: "◆", name: "Petrol", sub: "5 fills · wks 37-38", amt: 411.75 },
          { ic: "✧", name: "Groceries", sub: "3 shops · weekly", amt: 203 },
          { ic: "⌑", name: "Education", sub: "Course fee · once", amt: 99 }
        ]
      },
      "Month": {
        entries: [
          { ic: "◆", name: "Petrol", sub: "Jupiter + HP + local", amt: 1211.5 },
          { ic: "✧", name: "Groceries", sub: "Amul + kirana", amt: 901.6 },
          { ic: "⌑", name: "Education", sub: "Two courses", amt: 299 }
        ]
      },
      "Year": {
        entries: [
          { ic: "◆", name: "Petrol", sub: "392 fills · 12 mo", amt: 8210 },
          { ic: "✧", name: "Groceries", sub: "Household · year", amt: 8294 },
          { ic: "⌑", name: "Education", sub: "Certificates · 4", amt: 2400 }
        ]
      }
    };

    var current = PERIODS["Today"];
    var started = false;

    function renderRows() {
      rowsEl.innerHTML = "";
      current.entries.forEach(function (r) {
        var row = document.createElement("div");
        row.className = "demo-row";
        row.innerHTML =
          '<span class="dr-ic">' + r.ic + "</span>" +
          '<span><span class="dr-name">' + r.name + "</span><br>" +
          '<span class="dr-sub">' + r.sub + "</span></span>" +
          '<span class="dr-amt">' + fmtINR(r.amt) + "</span>";
        rowsEl.appendChild(row);
      });
      /* only keep the top three rows visible; older ones scroll off the card */
      return;
    }

    function sumAmt(list) {
      return list.reduce(function (t, v) { return t + v; }, 0);
    }

    /* floor each exact share, then hand the leftover points to the largest
       remainders — integer percentages that always total exactly 100. */
    function pctMix(amounts) {
      var tot = sumAmt(amounts) || 1;
      var raw = amounts.map(function (a) { return (a / tot) * 100; });
      var out = raw.map(Math.floor);
      var order = raw
        .map(function (v, i) { return { i: i, frac: v - Math.floor(v) }; })
        .sort(function (a, b) { return b.frac - a.frac; });
      var left = 100 - sumAmt(out);
      for (var k = 0; k < left; k++) out[order[k % order.length].i] += 1;
      return out;
    }

    function renderMix() {
      var pct = pctMix(current.entries.map(function (e) { return e.amt; }));
      var acc = 0;
      segs.forEach(function (el, i) {
        var v = i < pct.length ? pct[i] : 0;
        if (v > 0) {
          el.style.display = "";
          el.style.strokeDasharray = v + " 100";
          el.style.strokeDashoffset = String(-acc);
          acc += v;
        } else {
          /* a zero-length dash still paints a round-cap dot — hide the ring instead */
          el.style.display = "none";
        }
      });
      if (legendEl) {
        legendEl.innerHTML = current.entries.map(function (e, i) {
          return '<div class="legend-row"><i></i>' + e.name +
            '<span>' + fmtINR(e.amt) + ' · ' + pct[i] + '%</span></div>';
        }).join("");
      }
    }

    function renderHead() {
      if (!amtEl) return;
      var total = sumAmt(current.entries.map(function (e) { return e.amt; }));
      amtEl.textContent = fmtINR(total);
      if (avgEl) {
        avgEl.innerHTML = "<b>avg</b> " + fmtINR(total / current.entries.length) +
          " · <b>" + current.entries.length + "</b> entries";
      }
    }

    function enter() {
      if (started) return;
      started = true;
      renderMix();
    }

    /* tabs */
    $$(".demo-tabs button").forEach(function (btn) {
      btn.addEventListener("click", function () {
        $$(".demo-tabs button").forEach(function (b) { b.setAttribute("aria-selected", "false"); });
        btn.setAttribute("aria-selected", "true");
        current = PERIODS[btn.textContent.trim()] || current;
        renderRows();
        renderHead();
        renderMix();
      });
    });

    /* quick-log in the demo */
    function logDemo() {
      var raw = (input.value || "").replace(/[^\d.]/g, "");
      var amt = parseFloat(raw);
      if (!(amt > 0)) {
        if (input) { input.value = ""; input.focus(); }
        return;
      }
      current.entries = current.entries.slice(0, 2).concat([
        { ic: "✧", name: "Quick log", sub: "your sheet · " + new Date().toLocaleDateString("en-IN", { day: "2-digit", month: "2-digit", year: "2-digit" }), amt: amt }
      ]);
      renderRows();
      renderHead();
      renderMix();
      if (input) input.value = "";
    }
    if (logBtn) logBtn.addEventListener("click", logDemo);
    if (input) input.addEventListener("keydown", function (e) { if (e.key === "Enter") logDemo(); });

    if ("IntersectionObserver" in window) {
      var io = new IntersectionObserver(function (entries) {
        entries.forEach(function (en) { if (en.isIntersecting) { enter(); io.disconnect(); } });
      }, { threshold: 0.4 });
      io.observe(card);
    } else {
      enter();
    }
    renderRows();
    renderHead();
  })();

  /* --------------------------------------------------- quick-log tile demo */
  (function qlog() {
    var amtEl = $("#qlogAmt"), out = $("#qlogOut");
    var keys = $("#qlogKeys"), go = $("#qlogGo");
    if (!amtEl || !go) return;
    var value = 0;
    function render() { amtEl.textContent = value.toLocaleString("en-IN"); }
    if (keys) $$("button", keys).forEach(function (b) {
      b.addEventListener("click", function () {
        value = parseInt(b.getAttribute("data-v"), 10) || 0;
        render();
      });
    });
    go.addEventListener("click", function () {
      if (!(value > 0)) return;
      var row = document.createElement("div");
      row.className = "demo-row";
      row.innerHTML =
        '<span class="dr-ic">✧</span><span><span class="dr-name">Quick log</span><br>' +
        '<span class="dr-sub">pocket_ledger.db · 0.02s</span></span>' +
        '<span class="dr-amt">' + fmtINR(value) + "</span>";
      out.innerHTML = "";
      out.appendChild(row);
      value = 0;
      render();
      if (go.classList) { go.classList.add("pulse"); setTimeout(function () { go.classList.remove("pulse"); }, 350); }
    });
  })();

  /* ----------------------------------------------------- tags filter demo */
  (function tagsFilter() {
    var group = $("#tagFilters"), list = $("#tagsList");
    if (!group || !list) return;
    var rows = $$(".tl-row", list);
    var active = new Set();
    $$(".chip", group).forEach(function (chip) {
      chip.addEventListener("click", function () {
        var f = chip.getAttribute("data-filter");
        if (f === "all") active.clear();
        else {
          if (active.has(f)) active.delete(f);
          else active.add(f);
        }
        $$(".chip", group).forEach(function (c) {
          c.setAttribute("aria-pressed", String(c.getAttribute("data-filter") === "all" ? active.size === 0 : active.has(c.getAttribute("data-filter"))));
        });
        rows.forEach(function (row) {
          var match = active.size === 0 || Array.from(active).some(function (f) {
            return (row.getAttribute("data-cats") || "").indexOf(f) !== -1;
          });
          row.classList.toggle("hidden", !match);
        });
      });
    });
  })();

  /* --------------------------------------------------- pipeline scroll scrub */
  (function pipeline() {
    var section = $("#architecture");
    var stage = $(".pl-stage");
    var packet = $("#plPacket"), rail = $("#plRailFlow"), nodes = $$(".pl-node"), tabs = $$(".pl-tab");
    if (!section || !packet) return;
    var cur = 0, pinned = true;
    var THRESH = [0.03, 0.36, 0.70, 0.98];
    var mm = window.matchMedia("(min-width: 901px)");
    pinned = mm.matches;
    mm.addListener ? mm.addListener(function (m) { pinned = m.matches; onScroll(); }) : 0;

    function apply(p) {
      cur = p;
      if (packet.style.left !== undefined) {
        packet.style.left = (6 + 84 * p) + "%";
        packet.style.top = "50%";
        packet.style.transform = "translate(-50%,-50%)";
      }
      if (rail) rail.setAttribute("x2", String(40 + 920 * p));
      var actv = -1;
      THRESH.forEach(function (t, i) { if (p >= t) actv = i; });
      nodes.forEach(function (n, i) { n.classList.toggle("active", i === actv); });
      tabs.forEach(function (t, i) { t.classList.toggle("active", i === actv); });
    }

    function compute() {
      var rect = section.getBoundingClientRect();
      var vh = window.innerHeight;
      if (pinned) {
        var total = section.offsetHeight - vh;
        if (total <= 0) return 0;
        return clamp(-rect.top / total, 0, 1);
      }
      /* unpinned mobile: scrub by how far the pl-stage crosses the viewport */
      if (!stage) return 0;
      var s = stage.getBoundingClientRect();
      var span = s.height + vh;
      if (span <= 0) return 0;
      var center = s.top + s.height / 2;
      return clamp((vh + s.height / 2 - center) / span, 0, 1);
    }
    function onScroll() {
      apply(compute());
    }
    window.addEventListener("resize", onScroll, { passive: true });
    window.addEventListener("scroll", onScroll, { passive: true });

    /* clicking a tab eases the packet to that stage */
    tabs.forEach(function (tab, i) {
      tab.addEventListener("click", function () {
        if (reduced) { apply(THRESH[i] + 0.01); return; }
        var target = THRESH[i] + 0.01, from = cur, t0 = 0, dur = 700;
        function tick(ts) {
          if (!t0) t0 = ts;
          var p = clamp((ts - t0) / dur, 0, 1);
          var eased = p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
          apply(lerp(from, target, eased));
          if (p < 1) requestAnimationFrame(tick);
        }
        requestAnimationFrame(tick);
      });
    });

    apply(compute());
  })();

  /* -------------------------------------------------------- magnetic CTAs */
  (function magnetic() {
    if (reduced || !window.matchMedia("(pointer: fine)").matches) return;
    $$(".magnetic").forEach(function (el) {
      var rx = 0, ry = 0, active = false, raf = 0;
      function loop() {
        if (!active) return;
        el.style.setProperty("--mx", rx + "px");
        el.style.setProperty("--my", ry + "px");
        raf = requestAnimationFrame(loop);
      }
      el.addEventListener("pointermove", function (e) {
        var r = el.getBoundingClientRect();
        rx = (e.clientX - (r.left + r.width / 2)) * 0.22;
        ry = (e.clientY - (r.top + r.height / 2)) * 0.22;
        if (!active) { active = true; loop(); }
      });
      el.addEventListener("pointerleave", function () {
        active = false;
        cancelAnimationFrame(raf);
        el.style.setProperty("--mx", "0px");
        el.style.setProperty("--my", "0px");
      });
    });
  })();

  /* -------------------------------------------------- hero tilt + parallax */
  (function tilt() {
    if (reduced) return;
    var stage = $("#heroDemo"), card = $("#demoCard");
    if (stage && card && window.matchMedia("(pointer: fine)").matches) {
      stage.addEventListener("pointermove", function (e) {
        var r = stage.getBoundingClientRect();
        var px = (e.clientX - r.left) / r.width - 0.5;
        var py = (e.clientY - r.top) / r.height - 0.5;
        card.style.transition = "transform 90ms ease-out";
        card.style.transform =
          "perspective(1100px) rotateY(" + (px * 9) + "deg) rotateX(" + (-py * 9) + "deg) translateY(-2px)";
      });
      stage.addEventListener("pointerleave", function () {
        card.style.transition = "transform 700ms var(--ease-q)";
        card.style.transform = "perspective(1100px) rotateY(0deg) rotateX(0deg)";
      });
    }

    var layers = $$("[data-parallax]", document);
    if (!layers.length || !window.matchMedia("(pointer: fine)").matches) return;
    var hero = $(".hero");
    var mapped = layers.map(function (el) {
      return { el: el, f: parseFloat(el.getAttribute("data-parallax")) || 0, base: 0, tx: 0, ty: 0 };
    });
    hero.addEventListener("pointermove", function (e) {
      var r = hero.getBoundingClientRect();
      var nx = (e.clientX - r.left) / r.width - 0.5;
      var ny = (e.clientY - r.top) / r.height - 0.5;
      mapped.forEach(function (m) {
        m.tx = nx * m.f * 60;
        m.ty = ny * m.f * 60;
      });
    });
    (function parallaxLoop() {
      mapped.forEach(function (m) {
        m.el.style.transform = m.base ? "translateY(" + (m.base + m.ty) + "px) translateX(" + m.tx + "px)" :
          "translate(" + m.tx + "px, " + m.ty + "px)";
      });
      requestAnimationFrame(parallaxLoop);
    })();
  })();

  /* ------------------------------------ honest Android APK reachability */
  (function apk() {
    var state = $("#dlState"), fallback = $("#dlFallback"), btn = $("#dlBtn");
    if (!state) return;
    if (location.protocol === "file:") {
      state.textContent = "status unknown · previewing from disk";
      state.className = "dl-state warn";
      return;
    }
    var url = btn ? btn.getAttribute("href") : "downloads/Pocket-Ledger-Android-V2.apk";
    var ctrl = new AbortController();
    var timer = setTimeout(function () { ctrl.abort(); }, 8000);
    fetch(url, { method: "HEAD", cache: "no-store", signal: ctrl.signal })
      .then(function (res) {
        clearTimeout(timer);
        if (!res.ok) throw new Error("HTTP " + res.status);
        state.textContent = "over the air ✓ " + res.status;
        state.className = "dl-state ok";
      })
      .catch(function (err) {
        clearTimeout(timer);
        var note = (err && err.name === "AbortError") ? "check timed out" : ("reachability failed");
        state.textContent = note;
        state.className = "dl-state err";
        if (fallback) fallback.hidden = false;
      });
  })();
})();