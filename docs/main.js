// MinimalSF · site. No dependencies, no network calls.
(() => {
  "use strict";
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;

  // for whoever opens the console
  console.log("%cMinimalSF%c\nno trackers here either. the source is on GitHub:\nhttps://github.com/DHIRAJ-J-S/MinimalSF_Launcher\n\n(try typing 404 in the demo)",
    "font: 800 28px Outfit, sans-serif; letter-spacing: -1px", "font: 12px monospace; color: #888");

  // ---------- nav border once scrolled ----------
  const nav = $(".nav");
  const onScroll = () => nav.classList.toggle("scrolled", scrollY > 8);
  addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  // ---------- the launcher demo: same ranking as the app ----------
  // name prefix > keyword > word start > anywhere
  const APPS = [
    ["Calculator"], ["Calendar"], ["Camera"], ["Chrome"], ["Clock"], ["Contacts"],
    ["F-Droid", "fd"], ["Files"], ["Firefox", "ff"], ["Gallery"], ["Gmail", "mail"],
    ["Google Maps", "maps"], ["Keep Notes"], ["LocalSend"], ["Messages", "sms"],
    ["Obtainium"], ["Phone"], ["Photos"], ["Play Store"], ["Settings"], ["Signal"],
    ["Spotify", "sp"], ["Telegram", "tg"], ["Weather"], ["WhatsApp", "wa"],
    ["YouTube", "yt"], ["YouTube Music", "ytm"]
  ].map(([name, kw]) => ({ name, kw: kw || "" }));

  const q = $("#q"), list = $("#results"), clearBtn = $("#clear");
  const launch = $("#launch"), launchName = $("#launchName"), launchIcon = $("#launchIcon");
  const tryit = $("#tryit");
  let timer = 0, resetT = 0, launching = false;

  const esc = s => s.replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

  function rank(term) {
    const t = term.toLowerCase();
    const out = [];
    for (const a of APPS) {
      const n = a.name.toLowerCase();
      let score = -1, at = -1;
      if (n.startsWith(t)) { score = 0; at = 0; }
      else if (a.kw && a.kw.startsWith(t)) { score = 1; }
      else {
        const m = n.search(new RegExp("(^|[\\s-])" + t.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
        if (m >= 0) { score = 2; at = m === 0 ? 0 : m + 1; }
        else if ((at = n.indexOf(t)) >= 0) score = 3;
      }
      if (score >= 0) out.push({ a, score, at });
    }
    return out.sort((x, y) => x.score - y.score || x.a.name.localeCompare(y.a.name));
  }

  function hl(name, at, len) {
    if (at < 0) return esc(name);
    return esc(name.slice(0, at)) + "<b>" + esc(name.slice(at, at + len)) + "</b>" + esc(name.slice(at + len));
  }

  function render() {
    clearTimeout(timer);
    let v = q.value.trim();
    clearBtn.hidden = !v;
    document.body.classList.toggle("egg", v === "404");
    if (!v) { list.innerHTML = '<li class="empty idle">your apps show up here as you type</li>'; list._r = null; return; }

    // Easter egg, same as the app: 404 is the one thing that gets a colour (the 404 ms launch delay is blue)
    if (v === "404") {
      list._r = null;
      list.innerHTML = `
        <li class="egg-row"><a href="https://github.com/DHIRAJ-J-S" target="_blank" rel="noopener">
          <span class="ic" aria-hidden="true">4</span>
          <span class="nm">you found <b>THE.404GUY</b>. app not found, developer found.</span>
          <span class="n">↗</span></a></li>
        <li class="empty">psst: there's one in the app too. play with the auto-launch delay.</li>`;
      return;
    }

    // "c2": open the 2nd result for "c"
    const num = v.match(/^(.*\D)(\d)$/);
    if (num) {
      const r = rank(num[1]);
      const pick = r[+num[2] - 1];
      if (pick) { draw(r, num[1].length); timer = setTimeout(() => open(pick.a, +num[2] - 1), 260); return; }
    }

    const r = rank(v);
    draw(r, v.length);
    if (r.length === 1) timer = setTimeout(() => open(r[0].a, 0), 420);
  }

  function draw(r, len) {
    if (!r.length) { list.innerHTML = '<li class="empty">no match. try "wa", "c2" or "maps"</li>'; return; }
    list.innerHTML = r.slice(0, 6).map((x, i) => `
      <li data-i="${i}" class="${i === 0 ? "top" : ""}">
        <span class="ic" aria-hidden="true">${esc(x.a.name[0])}</span>
        <span class="nm">${hl(x.a.name, x.at, len)}</span>
        ${x.score === 1 ? `<span class="kw">${esc(x.a.kw)}</span>` : ""}
        <span class="n">${i + 1}</span>
      </li>`).join("");
    list._r = r;
  }

  function open(app, idx) {
    if (launching) return;
    launching = true;
    const row = list.children[idx];
    const box = tryit.getBoundingClientRect();
    if (row) {
      const rb = row.getBoundingClientRect();
      launch.style.setProperty("--lx", (rb.left - box.left + 20) + "px");
      launch.style.setProperty("--ly", (rb.top - box.top + rb.height / 2) + "px");
    }
    launchName.textContent = app.name;
    launchIcon.textContent = app.name[0];
    launch.classList.add("go");
    resetT = setTimeout(() => {
      launch.classList.remove("go");
      q.value = ""; render(); launching = false;
      if (!matchMedia("(pointer: coarse)").matches) q.focus({ preventScroll: true });
    }, reduced ? 900 : 1500);
  }

  q.addEventListener("input", render);
  render();
  q.addEventListener("keydown", e => {
    if (e.key === "Enter" && list._r && list._r.length) { e.preventDefault(); open(list._r[0].a, 0); }
    if (e.key === "Escape") { q.value = ""; render(); }
  });
  clearBtn.addEventListener("click", () => { q.value = ""; render(); q.focus(); });
  list.addEventListener("click", e => {
    const li = e.target.closest("li[data-i]");
    if (li && list._r) open(list._r[+li.dataset.i].a, +li.dataset.i);
  });

  // Like the app: just start typing. Keys go to the box from anywhere on the page.
  addEventListener("keydown", e => {
    if (e.ctrlKey || e.metaKey || e.altKey || e.key.length !== 1) return;
    const t = e.target;
    if (t === q || t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName)) return;
    if (!/[a-z0-9]/i.test(e.key)) return;
    const r = tryit.getBoundingClientRect();
    if (r.bottom < 0 || r.top > innerHeight) return; // only while the demo is on screen
    stopGhost();
    q.focus({ preventScroll: true });
  });

  // Ghost typing: cycles through examples until the visitor types something themselves
  const DEMOS = ["wa", "c2", "maps", "yt", "set"];
  let touched = false, demoI = 0, ghostT = 0;
  // The first real key or tap clears whatever the ghost typed, so it doesn't mix with the visitor's text
  const stopGhost = () => {
    if (touched) return;
    touched = true;
    clearTimeout(ghostT); clearTimeout(timer); clearTimeout(resetT);
    launch.classList.remove("go"); launching = false;
    q.value = ""; render();
  };
  q.addEventListener("keydown", stopGhost);
  q.addEventListener("pointerdown", stopGhost);
  function ghost() {
    if (touched) return;
    const r = tryit.getBoundingClientRect();
    if (r.bottom < 0 || r.top > innerHeight || document.hidden) { ghostT = setTimeout(ghost, 1200); return; }
    const word = DEMOS[demoI++ % DEMOS.length];
    let i = 0;
    const step = () => {
      if (touched) return;
      q.value = word.slice(0, ++i); render();
      if (i < word.length) ghostT = setTimeout(step, 190);
      else ghostT = setTimeout(ghost, 3400);
    };
    ghostT = setTimeout(step, 500);
  }
  if (!reduced) ghostT = setTimeout(ghost, 700);

  // ---------- reveal on scroll ----------
  const rvTargets = $$(".sec-head, .step, .rank-aside, .spec-row, .perm-row, .pledges li, .ext-card, .dl-card, .faq details, .rail figure");
  rvTargets.forEach(el => el.classList.add("rv"));
  if ("IntersectionObserver" in window && !reduced) {
    const io = new IntersectionObserver(es => es.forEach(e => {
      if (!e.isIntersecting) return;
      const sibs = [...e.target.parentElement.children].filter(c => c.classList.contains("rv"));
      e.target.style.transitionDelay = Math.min(sibs.indexOf(e.target), 6) * 70 + "ms";
      e.target.classList.add("in");
      io.unobserve(e.target);
    }), { rootMargin: "0px 0px -8% 0px" });
    rvTargets.forEach(el => io.observe(el));
  } else rvTargets.forEach(el => el.classList.add("in"));

  // ---------- screenshot rail: drag, buttons, progress ----------
  const rail = $("#rail"), bar = $("#railBar");
  const prog = () => {
    const max = rail.scrollWidth - rail.clientWidth;
    const w = Math.max(12, rail.clientWidth / rail.scrollWidth * 100);
    bar.style.width = w + "%";
    bar.style.left = (max > 0 ? rail.scrollLeft / max : 0) * (100 - w) + "%";
  };
  rail.addEventListener("scroll", prog, { passive: true });
  addEventListener("resize", prog);
  prog();
  $$(".rail-ctrl button").forEach(b => b.addEventListener("click", () =>
    rail.scrollBy({ left: +b.dataset.dir * rail.clientWidth * .7, behavior: reduced ? "auto" : "smooth" })));
  let dx = 0, sl = 0, down = false, moved = false;
  rail.addEventListener("pointerdown", e => {
    if (e.pointerType !== "mouse") return;
    down = true; moved = false; dx = e.clientX; sl = rail.scrollLeft;
  });
  addEventListener("pointermove", e => {
    if (!down) return;
    const d = e.clientX - dx;
    if (Math.abs(d) > 4) { moved = true; rail.classList.add("drag"); }
    rail.scrollLeft = sl - d;
  });
  addEventListener("pointerup", () => { down = false; rail.classList.remove("drag"); });
  rail.addEventListener("click", e => { if (moved) e.preventDefault(); }, true);

  // ---------- gestures pad ----------
  const trace = $("#trace");
  const gBtns = $$("#gestures button");
  let gAuto = 0, gIdx = 0;
  function play(btn) {
    gBtns.forEach(b => b.classList.toggle("on", b === btn));
    trace.className = "trace";
    void trace.offsetWidth; // restart the animation
    trace.classList.add(btn.dataset.g);
  }
  gBtns.forEach(b => b.addEventListener("click", () => { clearInterval(gAuto); play(b); }));
  if (!reduced && "IntersectionObserver" in window) {
    new IntersectionObserver(([e]) => {
      clearInterval(gAuto);
      if (e.isIntersecting) gAuto = setInterval(() => play(gBtns[gIdx++ % gBtns.length]), 1600);
    }).observe($("#gestures"));
  }

  // ---------- todo ----------
  const flip = b => { const on = b.getAttribute("aria-pressed") !== "true"; b.setAttribute("aria-pressed", on); return on; };
  $$("#todo .t-done").forEach(b => b.addEventListener("click", () => flip(b)));
  // "!" marks a task important: red, like in the app
  $$("#todo .t-imp").forEach(b => b.addEventListener("click", () => b.closest("li").classList.toggle("imp", flip(b))));

  // ---------- copy the certificate hash ----------
  const hash = $("#hash"), copied = $("#copied");
  hash.addEventListener("click", async () => {
    try { await navigator.clipboard.writeText(hash.textContent.trim()); copied.textContent = "copied"; }
    catch { copied.textContent = "select and copy"; }
    setTimeout(() => (copied.textContent = ""), 1800);
  });

  // ---------- pause the hero video when it's off screen ----------
  const vid = $(".phone video");
  if (vid && "IntersectionObserver" in window) {
    new IntersectionObserver(([e]) => { e.isIntersecting && !reduced ? vid.play().catch(() => {}) : vid.pause(); }).observe(vid);
  }
})();
