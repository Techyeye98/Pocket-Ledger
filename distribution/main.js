(() => {
  'use strict';
  const root = document.documentElement;
  const deviceMotion = matchMedia('(prefers-reduced-motion: reduce)');
  try { if(localStorage.getItem('pl-motion')==='off') root.dataset.motionPreference='off'; } catch {}
  const reducedMotion = {get matches(){return deviceMotion.matches || root.dataset.motionPreference==='off';}};
  const finePointer = matchMedia('(hover: hover) and (pointer: fine)');
  const themeButton = document.getElementById('theme-toggle');
  const menuButton = document.getElementById('menu-toggle');
  const mobileNav = document.getElementById('mobile-nav');
  const updateTheme = () => {
    const dark = root.dataset.theme !== 'light';
    themeButton?.setAttribute('aria-label', `Switch to ${dark ? 'light' : 'dark'} theme`);
    document.querySelector('meta[name="theme-color"]').content = dark ? '#081e18' : '#f3f5ec';
  };
  updateTheme();
  themeButton?.addEventListener('click', () => {
    root.dataset.theme = root.dataset.theme === 'light' ? 'dark' : 'light';
    try { localStorage.setItem('pl-theme', root.dataset.theme); } catch {}
    updateTheme();
  });
  function closeMenu(returnFocus = false) {
    if (!menuButton) return;
    menuButton.setAttribute('aria-expanded', 'false');
    menuButton.setAttribute('aria-label', 'Open menu');
    mobileNav.hidden = true;
    if (returnFocus) menuButton.focus();
  }
  menuButton?.addEventListener('click', () => {
    const open = menuButton.getAttribute('aria-expanded') !== 'true';
    menuButton.setAttribute('aria-expanded', String(open));
    menuButton.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
    mobileNav.hidden = !open;
    if (open) mobileNav.querySelector('a').focus();
  });
  mobileNav?.querySelectorAll('a').forEach(link => link.addEventListener('click', () => closeMenu()));
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && menuButton?.getAttribute('aria-expanded') === 'true') closeMenu(true);
    if (event.key === 'Tab' && menuButton?.getAttribute('aria-expanded') === 'true') {
      const links = [...mobileNav.querySelectorAll('a')];
      const first = links[0], last = links[links.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); menuButton.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); menuButton.focus(); }
      else if (document.activeElement === menuButton) { event.preventDefault(); (event.shiftKey ? last : first).focus(); }
    }
  });
  const barsHost = document.getElementById('bars');
  if (barsHost) {
    const moments = [
      {category:'Petrol',amount:411.75},{category:'Groceries',amount:89},
      {category:'Education',amount:99},{category:'Groceries',amount:114},{category:'Petrol',amount:300}
    ];
    const grouped = new Map();
    moments.forEach(entry => grouped.set(entry.category,(grouped.get(entry.category)||0)+entry.amount));
    const categories = [...grouped].map(([category,amount])=>({category,amount}));
    let currentView='moments', entries=moments, selected=null, focused=null, hovered=null;
    const bars=document.getElementById('bars'), labels=document.getElementById('field-labels');
    const caption=document.getElementById('visual-caption'), detail=document.getElementById('visual-detail');
    const viewButtons=[...document.querySelectorAll('.view-button')];
    let barElements=[];
    const highlight=()=>{
      const active=hovered??focused??selected;
      barElements.forEach((bar,i)=>{
        bar.dataset.active=String(i===active);bar.dataset.dimmed=String(active!==null&&i!==active);
        bar.setAttribute('aria-pressed',String(i===selected));
      });
      detail.textContent=active===null?'Select a bar to inspect an expense.':`${entries[active].category} · ₹${entries[active].amount.toFixed(2)}${currentView==='categories'?' total':''} (sample)`;
    };
    const place=()=>{
      const width=bars.getBoundingClientRect().width, step=width/entries.length;
      barElements.forEach((bar,i)=>bar.style.setProperty('--bar-x',`${Math.round(step*i+(step-bar.getBoundingClientRect().width)/2)}px`));
    };
    function render(){
      bars.replaceChildren();labels.replaceChildren();barElements=[];
      const max=Math.max(...entries.map(entry=>entry.amount));
      entries.forEach((entry,i)=>{
        const bar=document.createElement('button');bar.type='button';bar.className='viz-bar';bar.dataset.category=entry.category;
        bar.style.setProperty('--bar-height',`${20+entry.amount/max*54}%`);
        bar.setAttribute('aria-label',`${currentView==='moments'?'Sample expense':'Sample category total'} ${i+1}: ${entry.category}, ₹${entry.amount.toFixed(2)}`);
        const amount=document.createElement('span');amount.setAttribute('aria-hidden','true');amount.textContent=`₹${entry.amount.toFixed(2)}`;bar.append(amount);
        bar.addEventListener('pointerenter',()=>{if(finePointer.matches){hovered=i;highlight();}});
        bar.addEventListener('pointerleave',()=>{hovered=null;highlight();});
        bar.addEventListener('focus',()=>{focused=i;highlight();});bar.addEventListener('blur',()=>{focused=null;highlight();});
        bar.addEventListener('click',()=>{selected=selected===i?null:i;highlight();});
        bar.addEventListener('keydown',event=>{if(event.key==='Escape'){selected=null;bar.blur();highlight();}});
        bars.append(bar);barElements.push(bar);
        const label=document.createElement('span');label.textContent=currentView==='moments'?String(i+1).padStart(2,'0'):entry.category;label.style.width=`${100/entries.length}%`;labels.append(label);
      });
      highlight();place();
    }
    viewButtons.forEach(button=>button.addEventListener('click',()=>{
      currentView=button.dataset.view;entries=currentView==='moments'?moments:categories;
      selected=focused=hovered=null;
      viewButtons.forEach(b=>b.setAttribute('aria-pressed',String(b===button)));
      caption.textContent=currentView==='moments'?'Five moments, in order.':'Three categories. The same total.';
      render();
    }));
    render();
    if('ResizeObserver' in window)new ResizeObserver(place).observe(bars);
    else window.addEventListener('resize',place);
  }

  const syncButton = document.getElementById('sync-demo');
  syncButton?.addEventListener('click', () => {
    const synced = syncButton.getAttribute('aria-pressed') !== 'true';
    syncButton.setAttribute('aria-pressed', String(synced));
    syncButton.firstChild.textContent = synced ? 'Return to local only ' : 'Show optional sync ';
    document.getElementById('control-scene').dataset.synced = String(synced);
    document.getElementById('sync-caption').textContent = synced
      ? 'Accounts, entries and people mirror to your Sheet. Split bills stay on this phone.'
      : 'No scheduled background sync. Connected saves can attempt sync.';
  });

  // Website-only examples; no app storage, Sheet connection or real money is touched.
  document.querySelectorAll('.money-study').forEach(study => {
    const button = study.querySelector('.study-action');
    const initialLabel = button.innerHTML;
    const status = study.querySelector('[data-status]');
    const initialStatus = status.textContent;
    let active = false;
    button.hidden = false;
    button.addEventListener('click', () => {
      active = !active;
      study.dataset.active = String(active);
      button.innerHTML = active ? 'Reset example <span aria-hidden="true">↶</span>' : initialLabel;
      if (study.id === 'account-study') {
        study.querySelector('[data-bank]').textContent = active ? '₹7,900' : '₹8,400';
        study.querySelector('[data-cash]').textContent = active ? '₹2,100' : '₹1,600';
        status.textContent = active ? '₹500 moved from bank to cash. Still ₹10,000. No new spending.' : initialStatus;
      } else {
        study.querySelector('[data-receive]').textContent = active ? '₹600' : '₹800';
        status.textContent = active ? 'Aarav paid back ₹200. He owes ₹200; Meera owes ₹400.' : initialStatus;
      }
    });
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
    if(guideSection) delete guideSection.dataset.bookOpening;
  });


  // Keep the original trace depth and reveals on the shared Lenis/GSAP clock.
  function onPage({alignInitialHash=false} = {}) {
    if (!window.gsap || !window.ScrollTrigger) return () => {};
    const { gsap, ScrollTrigger } = window;
    gsap.registerPlugin(ScrollTrigger);
    const media = gsap.matchMedia();
    let lenis = null;
    let disposed = false;
    let heroVisible = true;
    const art = document.querySelector('.hero-backdrop');
    const traces=document.querySelector('.hero-traces'), beats=document.querySelector('.hero-beats'), glow=document.querySelector('.hero-glow');
    const hero = document.querySelector('.hero');
    const target = { x:0, y:0 }, pointer = { x:0, y:0 };
    const move = event => {
      if (!hero || !finePointer.matches || reducedMotion.matches) return;
      const rect = hero.getBoundingClientRect();
      target.x = (event.clientX - rect.left) / rect.width * 2 - 1;
      target.y = (event.clientY - rect.top) / rect.height * 2 - 1;
    };
    const reset = () => { target.x = target.y = 0; };
    hero?.addEventListener('pointermove', move, {passive:true});
    hero?.addEventListener('pointerleave', reset);
    const observer = art ? new IntersectionObserver(([entry]) => { heroVisible = entry.isIntersecting; travel(); }) : null;
    if (art) observer.observe(art);
    let travelPaused;
    const travel=()=>{if(!traces)return;const paused=reducedMotion.matches||document.hidden||!heroVisible;if(paused===travelPaused)return;travelPaused=paused;if(paused)traces.pauseAnimations();else traces.unpauseAnimations();};
    document.addEventListener('visibilitychange',travel);travel();
    const draw = time => {
      if (document.hidden) return;
      travel();
      lenis?.raf(time * 1000);
      const max = root.scrollHeight - innerHeight;
      root.style.setProperty('--scroll-progress', String(max > 0 ? scrollY / max : 0));
      if (reducedMotion.matches) return;
      if (art && heroVisible) {
        pointer.x += (target.x - pointer.x) * .08;
        pointer.y += (target.y - pointer.y) * .08;
        if(traces) traces.style.transform=`translate3d(${pointer.x*16}px,${pointer.y*12}px,0)`;
        if(beats) beats.style.transform=`translate3d(${pointer.x*-9}px,${pointer.y*-5}px,0)`;
        if(glow) glow.style.transform=`translate3d(${pointer.x*26}px,${pointer.y*18}px,0)`;
      }

    };
    gsap.ticker.lagSmoothing(0);
    gsap.ticker.add(draw);
    root.dataset.motion = 'off';
    media.add('(prefers-reduced-motion: no-preference)', () => {
      if (reducedMotion.matches) return;
      root.dataset.motion = 'on';
      if (window.Lenis) {
        lenis = new Lenis({autoRaf:false, smoothWheel:true, syncTouch:false, lerp:.11, anchors:false, respectReducedMotion:true});
        lenis.on('scroll', ScrollTrigger.update);
        root.dataset.scrollEngine = 'lenis';
      }
      gsap.utils.toArray('.reveal').forEach(el=>gsap.from(el,{y:22,duration:.75,ease:'power3.out',clearProps:'transform',scrollTrigger:{trigger:el,start:'top 92%',once:true}}));
      return () => {
        lenis?.destroy(); lenis = null;
        delete root.dataset.scrollEngine;
        root.dataset.motion = 'off';
        [traces,beats,glow].filter(Boolean).forEach(el=>el.style.removeProperty('transform'));
        travel();
      };
    });
    function anchor(event) {
      const link = event.target.closest('a[href^="#"]');
      if (!link || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const hash = link.getAttribute('href');
      if (hash.length < 2) return;
      const el = document.getElementById(decodeURIComponent(hash.slice(1)));
      if (!el) return;
      event.preventDefault();
      const offset = document.querySelector('.site-header').offsetHeight + 20;
      const focusTarget = () => {
        const hadTabIndex = el.hasAttribute('tabindex');
        if (!hadTabIndex) { el.tabIndex = -1; el.addEventListener('blur', () => el.removeAttribute('tabindex'), {once:true}); }
        el.focus({preventScroll:true});
      };
      // Keep hashes native so Back/Forward can restore the reader's place.
      history.pushState(null,'',hash);
      if (lenis) lenis.scrollTo(scrollY + el.getBoundingClientRect().top - offset,{duration:.85,onComplete:focusTarget});
      else { el.scrollIntoView({behavior:'instant',block:'start'}); focusTarget(); }
    }
    document.addEventListener('click',anchor);
    const interruptKeyboard = event => {
      if (!lenis || event.target.closest('input,textarea,select,[contenteditable=true]')) return;
      if (['ArrowUp','ArrowDown','PageUp','PageDown','Home','End',' '].includes(event.key)) {
        lenis.stop(); lenis.start();
      }
    };
    document.addEventListener('keydown',interruptKeyboard);
    const resize = () => { lenis?.resize(); ScrollTrigger.refresh(); };
    document.querySelectorAll('details').forEach(el => el.addEventListener('toggle',resize));
    let restoreCall;
    const restore = event => {
      if (disposed) return;
      const keepPosition = event?.type === 'pageshow' && event.persisted;
      restoreCall?.kill();
      restoreCall = gsap.delayedCall(.05, () => {
        resize();
        let target = null;
        try { target = document.getElementById(decodeURIComponent(location.hash.slice(1))); } catch {}
        const offset = document.querySelector('.site-header').offsetHeight + 20;
        // Numeric document coordinates avoid stale Lenis state after native fragment/history jumps.
        if (target && lenis && !keepPosition) lenis.scrollTo(scrollY + target.getBoundingClientRect().top - offset,{immediate:true,force:true});
        else if (target && !keepPosition) target.scrollIntoView({behavior:'instant',block:'start'});
        else lenis?.scrollTo(scrollY,{immediate:true,force:true});
      });
    };
    (document.fonts?.ready || Promise.resolve()).then(() => {if (!disposed) alignInitialHash ? restore() : resize();});
    window.addEventListener('load',restore);
    window.addEventListener('pageshow',restore);
    window.addEventListener('popstate',restore);
    return () => {
      disposed = true; document.removeEventListener('visibilitychange',travel); media.revert(); gsap.ticker.remove(draw); observer?.disconnect();
      hero?.removeEventListener('pointermove',move); hero?.removeEventListener('pointerleave',reset);
      document.removeEventListener('click',anchor); document.removeEventListener('keydown',interruptKeyboard); restoreCall?.kill(); window.removeEventListener('popstate',restore); window.removeEventListener('load',restore); window.removeEventListener('pageshow',restore);
      document.querySelectorAll('details').forEach(el => el.removeEventListener('toggle',resize));
    };
  }
  let cleanup = onPage({alignInitialHash:true});
  const motionButton = document.getElementById('motion-toggle');
  const updateMotionLabel = () => {
    if (!motionButton) return;
    motionButton.textContent = reducedMotion.matches ? 'Motion: off' : 'Motion: on';
    motionButton.setAttribute('aria-label', reducedMotion.matches ? 'Motion: off. Turn motion on' : 'Motion: on. Turn motion off');
  };
  updateMotionLabel();
  deviceMotion.addEventListener('change',updateMotionLabel);
  motionButton?.addEventListener('click', () => {
    root.dataset.motionPreference = root.dataset.motionPreference === 'off' ? 'on' : 'off';
    try { localStorage.setItem('pl-motion',root.dataset.motionPreference); } catch {}
    cleanup(); cleanup = onPage(); updateMotionLabel();
  });
  window.addEventListener('pagehide',event => { if (!event.persisted) cleanup(); });
})();
