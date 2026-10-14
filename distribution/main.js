(() => {
  const root = document.documentElement;
  const themeButton = document.getElementById('theme-toggle');
  const menuButton = document.getElementById('menu-toggle');
  const mobileNav = document.getElementById('mobile-nav');
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const finePointer = window.matchMedia('(hover: hover) and (pointer: fine)');

  const hero = document.querySelector('.hero');
  const heroTraces = document.querySelector('.hero-traces');
  const heroBeats = document.querySelector('.hero-beats');
  const heroGlow = document.querySelector('.hero-glow');
  let pointerTarget = { x: 0, y: 0 };
  let pointerCurrent = { x: 0, y: 0 };
  let pointerFrame = 0;
  let heroVisible = true;
  function syncTravel() {
    if (reducedMotion.matches || document.hidden || !heroVisible) heroTraces.pauseAnimations();
    else heroTraces.unpauseAnimations();
  }
  const resetHeroDepth = () => {
    pointerTarget = { x: 0, y: 0 };
    if (!pointerFrame) pointerFrame = requestAnimationFrame(animateHeroDepth);
  };
  function animateHeroDepth() {
    pointerFrame = 0;
    if (reducedMotion.matches || document.hidden || !heroVisible) {
      pointerCurrent = { x: 0, y: 0 };
    } else {
      pointerCurrent.x += (pointerTarget.x - pointerCurrent.x) * .12;
      pointerCurrent.y += (pointerTarget.y - pointerCurrent.y) * .12;
    }
    const { x, y } = pointerCurrent;
    heroTraces.style.transform = `translate3d(${(x * 16).toFixed(2)}px, ${(y * 12).toFixed(2)}px, 0)`;
    heroBeats.style.transform = `translate3d(${(x * -9).toFixed(2)}px, ${(y * -5).toFixed(2)}px, 0)`;
    heroGlow.style.transform = `translate3d(${(x * 26).toFixed(2)}px, ${(y * 18).toFixed(2)}px, 0)`;
    if (Math.abs(pointerTarget.x - x) > .003 || Math.abs(pointerTarget.y - y) > .003) {
      pointerFrame = requestAnimationFrame(animateHeroDepth);
    }
  }
  hero.addEventListener('pointermove', event => {
    if (!finePointer.matches || reducedMotion.matches || !heroVisible || document.hidden) return;
    const rect = hero.getBoundingClientRect();
    pointerTarget = {
      x: Math.max(-1, Math.min(1, (event.clientX - rect.left) / rect.width * 2 - 1)),
      y: Math.max(-1, Math.min(1, (event.clientY - rect.top) / rect.height * 2 - 1))
    };
    if (!pointerFrame) pointerFrame = requestAnimationFrame(animateHeroDepth);
  }, { passive: true });
  hero.addEventListener('pointerleave', resetHeroDepth);
  reducedMotion.addEventListener('change', () => { resetHeroDepth(); syncTravel(); });
  finePointer.addEventListener('change', resetHeroDepth);
  document.addEventListener('visibilitychange', () => { resetHeroDepth(); syncTravel(); });
  if ('IntersectionObserver' in window) {
    new IntersectionObserver(([entry]) => {
      heroVisible = entry.isIntersecting;
      if (!heroVisible) resetHeroDepth();
      syncTravel();
    }).observe(hero);
  }
  syncTravel();

  const storedTheme = (() => {
    try { return localStorage.getItem('pl-theme'); } catch { return null; }
  })();
  root.dataset.theme = storedTheme === 'light' ? 'light' : 'dark';
  const updateThemeLabel = () => {
    const next = root.dataset.theme === 'dark' ? 'light' : 'dark';
    themeButton.setAttribute('aria-label', `Switch to ${next} theme`);
    themeButton.title = `Switch to ${next} theme`;
    document.querySelector('meta[name="theme-color"]').content = root.dataset.theme === 'dark' ? '#050d0b' : '#f2f8f4';
  };
  updateThemeLabel();
  themeButton.addEventListener('click', () => {
    root.dataset.theme = root.dataset.theme === 'dark' ? 'light' : 'dark';
    try { localStorage.setItem('pl-theme', root.dataset.theme); } catch {}
    updateThemeLabel();
  });

  function closeMenu(returnFocus = false) {
    menuButton.setAttribute('aria-expanded', 'false');
    menuButton.setAttribute('aria-label', 'Open navigation');
    mobileNav.hidden = true;
    if (returnFocus) menuButton.focus();
  }
  menuButton.addEventListener('click', () => {
    const open = menuButton.getAttribute('aria-expanded') !== 'true';
    menuButton.setAttribute('aria-expanded', String(open));
    menuButton.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
    mobileNav.hidden = !open;
    if (open) mobileNav.querySelector('a').focus();
  });
  mobileNav.querySelectorAll('a').forEach(link => link.addEventListener('click', () => closeMenu()));
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && menuButton.getAttribute('aria-expanded') === 'true') closeMenu(true);
  });
  window.matchMedia('(min-width: 801px)').addEventListener('change', event => {
    if (event.matches) closeMenu();
  });

  const entries = [
    { category: 'Petrol', amount: 411.75 },
    { category: 'Groceries', amount: 89 },
    { category: 'Education', amount: 99 },
    { category: 'Groceries', amount: 114 },
    { category: 'Petrol', amount: 300 }
  ];
  const categoryOrder = [0, 4, 1, 3, 2];
  const visualField = document.getElementById('visual-field');
  const bars = document.getElementById('bars');
  const fieldLabels = document.querySelectorAll('#field-labels span');
  const viewButtons = [...document.querySelectorAll('.view-button')];
  const visualCaption = document.getElementById('visual-caption');
  const visualDetail = document.getElementById('visual-detail');
  let currentView = 'moments';
  let barElements = [];
  let hoveredBar = null;
  let focusedBar = null;
  let selectedBar = null;

  function updateBarHighlight() {
    const active = hoveredBar ?? focusedBar ?? selectedBar;
    barElements.forEach((bar, index) => {
      bar.dataset.active = String(index === active);
      bar.dataset.dimmed = String(active !== null && index !== active);
      bar.setAttribute('aria-pressed', String(index === selectedBar));
    });
    visualDetail.textContent = active === null
      ? 'Hover, focus, or tap a bar to inspect an expense.'
      : `Entry ${active + 1} of ${entries.length} · ${entries[active].category} · ₹${entries[active].amount.toFixed(2)} (sample)`;
  }

  entries.forEach((entry, index) => {
    const bar = document.createElement('button');
    bar.className = 'viz-bar';
    bar.type = 'button';
    bar.dataset.category = entry.category;
    bar.style.setProperty('--bar-height', `${20 + entry.amount / 411.75 * 54}%`);
    bar.setAttribute('aria-label', `Sample expense ${index + 1} of ${entries.length}: ${entry.category}, ₹${entry.amount.toFixed(2)}`);
    const label = document.createElement('span');
    label.setAttribute('aria-hidden', 'true');
    label.innerHTML = `₹${entry.amount.toFixed(2)}<small>${entry.category}</small>`;
    bar.appendChild(label);
    bar.addEventListener('pointerenter', () => {
      if (finePointer.matches) { hoveredBar = index; updateBarHighlight(); }
    });
    bar.addEventListener('pointerleave', () => {
      if (hoveredBar === index) { hoveredBar = null; updateBarHighlight(); }
    });
    bar.addEventListener('focus', () => { focusedBar = index; updateBarHighlight(); });
    bar.addEventListener('blur', () => {
      if (focusedBar === index) { focusedBar = null; updateBarHighlight(); }
    });
    bar.addEventListener('click', () => {
      selectedBar = selectedBar === index ? null : index;
      updateBarHighlight();
    });
    bar.addEventListener('keydown', event => {
      if (event.key === 'Escape') { selectedBar = null; bar.blur(); updateBarHighlight(); }
    });
    bars.appendChild(bar);
    barElements.push(bar);
  });
  updateBarHighlight();

  function placeBars() {
    const width = bars.getBoundingClientRect().width;
    if (!width) return;
    const step = width / entries.length;
    barElements.forEach((bar, index) => {
      const slot = currentView === 'moments' ? index : categoryOrder.indexOf(index);
      const barWidth = bar.getBoundingClientRect().width;
      const x = step * slot + (step - barWidth) / 2;
      bar.style.setProperty('--bar-x', `${Math.round(x)}px`);
    });
  }
  function setView(view) {
    if (view !== 'moments' && view !== 'categories') return;
    currentView = view;
    viewButtons.forEach(button => button.setAttribute('aria-pressed', String(button.dataset.view === view)));
    visualField.dataset.view = view;
    const labels = view === 'moments' ? ['01', '02', '03', '04', '05'] : ['Petrol', 'Petrol', 'Groceries', 'Groceries', 'Education'];
    fieldLabels.forEach((label, index) => { label.textContent = labels[index]; });
    visualCaption.textContent = view === 'moments' ? 'Five separate moments, kept in order.' : 'The same entries, gathered by category.';
    placeBars();
  }
  viewButtons.forEach(button => button.addEventListener('click', () => setView(button.dataset.view)));
  const resizeObserver = new ResizeObserver(placeBars);
  resizeObserver.observe(bars);
  placeBars();

  const storySteps = document.querySelectorAll('[data-story-view]');
  if ('IntersectionObserver' in window) {
    const storyObserver = new IntersectionObserver(entries => {
      const visible = entries.filter(entry => entry.isIntersecting).sort((a, b) => b.intersectionRatio - a.intersectionRatio);
      if (visible[0]) setView(visible[0].target.dataset.storyView);
    }, { threshold: [.35, .55], rootMargin: '-15% 0px -15% 0px' });
    storySteps.forEach(step => storyObserver.observe(step));
  }

  const syncButton = document.getElementById('sync-demo');
  const syncScene = document.getElementById('control-scene');
  const syncCaption = document.getElementById('sync-caption');
  syncButton.addEventListener('click', () => {
    const synced = syncButton.getAttribute('aria-pressed') !== 'true';
    syncButton.setAttribute('aria-pressed', String(synced));
    syncButton.firstChild.textContent = synced ? 'Return to local only ' : 'Show the optional sync ';
    syncScene.dataset.synced = String(synced);
    syncCaption.textContent = synced ? 'When you choose Sync, the Android app can send a copy to your Sheet.' : 'Nothing leaves the Android app in the background.';
  });

  const guideSection = document.querySelector('.guide-section');
  const guideBookLink = document.querySelector('.guide-book-link');
  let guideOpening = false;
  let guideOpenTimer = 0;
  document.querySelectorAll('[data-guide-open]').forEach(link => link.addEventListener('click', event => {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || reducedMotion.matches) return;
    event.preventDefault();
    if (guideOpening) return;
    guideOpening = true;
    const bounds = guideBookLink.getBoundingClientRect();
    const needsScroll = link !== guideBookLink || bounds.top > innerHeight - 220 || bounds.bottom < 160;
    if (needsScroll) guideBookLink.scrollIntoView({ behavior: 'instant', block: 'center' });
    const startDelay = needsScroll ? 80 : 0;
    window.setTimeout(() => { guideSection.dataset.bookOpening = 'true'; }, startDelay);
    guideOpenTimer = window.setTimeout(() => { window.location.assign(link.href); }, startDelay + 980);
  }));
  window.addEventListener('pageshow', () => {
    window.clearTimeout(guideOpenTimer);
    guideOpening = false;
    delete guideSection.dataset.bookOpening;
  });

  if ('IntersectionObserver' in window && !reducedMotion.matches) {
    const revealObserver = new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) return;
        entry.target.animate([
          { clipPath: 'inset(0 0 25% 0)', transform: 'translateY(22px)' },
          { clipPath: 'inset(0 0 0 0)', transform: 'translateY(0)' }
        ], { duration: 750, easing: 'cubic-bezier(0.77, 0, 0.175, 1)' });
        revealObserver.unobserve(entry.target);
      });
    }, { threshold: .22, rootMargin: '0px 0px -8% 0px' });
    document.querySelectorAll('.reveal').forEach(element => revealObserver.observe(element));
  }

  let scrollScheduled = false;
  function updateScrollProgress() {
    const max = document.documentElement.scrollHeight - window.innerHeight;
    const progress = max > 0 ? window.scrollY / max : 0;
    root.style.setProperty('--scroll-progress', String(progress));
    scrollScheduled = false;
  }
  window.addEventListener('scroll', () => {
    if (scrollScheduled) return;
    scrollScheduled = true;
    requestAnimationFrame(updateScrollProgress);
  }, { passive: true });
  updateScrollProgress();
})();
