"use strict";
/* Lotos Studio core: helpers, icons, documents, router, modals, jobs, palette. */

const $ = (s, r = document) => r.querySelector(s);
function h(tag, attrs, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === false || v == null) continue;
    if (k === "class") e.className = v;
    else if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
    else if (k === "value") e.value = v;
    else if (k === "checked") e.checked = !!v;
    else if (k === "html") e.innerHTML = v;
    else e.setAttribute(k, v === true ? "" : v);
  }
  for (const c of kids.flat(Infinity)) {
    if (c == null || c === false) continue;
    e.append(c.nodeType ? c : document.createTextNode(String(c)));
  }
  return e;
}
async function api(path, opts) {
  const r = await fetch(path, opts);
  if (!r.ok) throw new Error((await r.text()) || r.status);
  return r.json();
}
const post = (path, obj) => api(path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(obj) });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const esc = s => String(s).replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const lc = t => (t || "").toLowerCase();
const uid = () => Math.random().toString(16).slice(2, 10);
const nowIso = () => new Date().toISOString().slice(0, 16);
const fmtDate = iso => { if (!iso) return ""; const d = new Date(iso); return isNaN(d) ? iso : d.toLocaleString("ru-RU", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" }); };
function store(k, v) { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch (e) { return null; } }
async function copyText(t) {
  try { await navigator.clipboard.writeText(t); } catch (e) { const ta = h("textarea", { value: t }); document.body.append(ta); ta.select(); document.execCommand("copy"); ta.remove(); }
  toast("Скопировано", "ok");
}
function download(name, text, type = "text/plain") { const a = h("a", { href: URL.createObjectURL(new Blob([text], { type })), download: name }); document.body.append(a); a.click(); a.remove(); }

/* ---------------------------------------------------------------- icons */
const ICONS = {
  home: '<path d="M3 11l9-8 9 8"/><path d="M5 10v10h14V10"/>',
  film: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 4v16M17 4v16M3 9h4M3 15h4M17 9h4M17 15h4"/>',
  users: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c.5-4 3-6 6.5-6s6 2 6.5 6"/><circle cx="17" cy="9" r="2.5"/><path d="M17 14c2.5.3 4 2 4.5 5"/>',
  map: '<path d="M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z"/><path d="M9 4v14M15 6v14"/>',
  book: '<path d="M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z"/><path d="M5 17a3 3 0 0 1 3-3h11"/>',
  graph: '<circle cx="6" cy="6" r="2.5"/><circle cx="18" cy="8" r="2.5"/><circle cx="12" cy="18" r="2.5"/><path d="M8 7l8 1M7 8l4 8M17 10l-4 6"/>',
  bulb: '<path d="M9 18h6M10 21h4"/><path d="M12 3a6 6 0 0 0-3.5 10.9c.7.6 1 1.3 1 2.1h5c0-.8.3-1.5 1-2.1A6 6 0 0 0 12 3z"/>',
  star: '<path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"/>',
  mail: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 7l9 6 9-6"/>',
  check: '<path d="M5 12l4 4 10-10"/>',
  sliders: '<path d="M4 7h10M18 7h2M4 17h2M10 17h10"/><circle cx="16" cy="7" r="2"/><circle cx="8" cy="17" r="2"/>',
  image: '<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="1.6"/><path d="M4 18l5-5 4 4 3-3 4 4"/>',
  terminal: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 9l3 3-3 3M12 16h5"/>',
  pulse: '<path d="M3 12h4l3-7 4 14 3-7h4"/>',
  file: '<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4M9 12h6M9 16h6"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2"/>',
  search: '<circle cx="11" cy="11" r="6"/><path d="M20 20l-4-4"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  play: '<path d="M7 4l13 8-13 8z"/>',
  pause: '<path d="M8 5v14M16 5v14"/>',
  menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
  grid: '<rect x="4" y="4" width="7" height="7"/><rect x="13" y="4" width="7" height="7"/><rect x="4" y="13" width="7" height="7"/><rect x="13" y="13" width="7" height="7"/>',
};
function icon(name, size = 18) {
  const s = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  for (const [k, v] of Object.entries({ viewBox: "0 0 24 24", width: size, height: size, fill: "none", stroke: "currentColor", "stroke-width": "1.8", "stroke-linecap": "round", "stroke-linejoin": "round" })) s.setAttribute(k, v);
  s.innerHTML = ICONS[name] || "";
  return s;
}

/* ---------------------------------------------------------------- feedback */
function setSave(text, err) { const e = $("#savestate"); if (e) { e.textContent = text; e.className = "savestate" + (err ? " err" : ""); } }
function toast(msg, kind = "") {
  const t = h("div", { class: "toast " + kind }, msg); $("#toasts").append(t);
  setTimeout(() => t.remove(), kind === "err" ? 6000 : 2600);
}
function modal({ title, body, actions = [], wide = false, cls = "", onClose = null }) {
  const ov = h("div", { class: "overlay" });
  const box = h("div", { class: `modal ${wide ? "wide" : ""} ${cls}` }, title ? h("h2", {}, title) : null, body,
    actions.length ? h("div", { class: "acts" }, actions.map(a => h("button", { class: "btn " + (a.primary ? "primary" : ""), onclick: () => { if (a.onclick) a.onclick(); if (a.close !== false) close(); } }, a.label))) : null);
  const close = () => { if (!ov.isConnected) return; ov.remove(); document.removeEventListener("keydown", onKey, true); if (onClose) onClose(); };
  const onKey = e => { if (e.key === "Escape") { e.stopPropagation(); close(); } };
  ov.addEventListener("mousedown", e => { if (e.target === ov) close(); });
  document.addEventListener("keydown", onKey, true);
  ov.append(box); document.body.append(ov);
  return { close, box };
}
const confirmBox = (text, yes = "Да") => new Promise(res => {
  let done = false; const fin = v => { if (!done) { done = true; res(v); } };
  modal({ title: "Подтверди", body: h("p", {}, text), onClose: () => fin(false),
    actions: [{ label: "Отмена" }, { label: yes, primary: true, onclick: () => fin(true) }] });
});
const promptBox = (title, value = "", multiline = false) => new Promise(res => {
  const inp = multiline ? h("textarea", { value }) : h("input", { type: "text", value });
  let done = false; const fin = v => { if (!done) { done = true; res(v); } };
  const m = modal({ title, body: inp, onClose: () => fin(null), actions: [{ label: "Отмена" }, { label: "Готово", primary: true, onclick: () => fin(inp.value) }] });
  setTimeout(() => inp.focus(), 30);
  if (!multiline) inp.addEventListener("keydown", e => { if (e.key === "Enter") { fin(inp.value); m.close(); } });
});

/* ---------------------------------------------------------------- documents (saved on the server, exported to art/episodes) */
const MD = {};            // doc name -> function(data) returning markdown for art/episodes/<name>.md
const Docs = {
  cache: {}, timers: {},
  async get(name) { if (!this.cache[name]) this.cache[name] = await api("/api/doc?name=" + name); return this.cache[name]; },
  save(name) { clearTimeout(this.timers[name]); setSave("есть изменения…"); this.timers[name] = setTimeout(() => this.flush(name), 700); },
  async flush(name) {
    clearTimeout(this.timers[name]);
    try { await post("/api/doc?name=" + name, { data: this.cache[name], md: MD[name] ? MD[name](this.cache[name]) : undefined }); setSave("сохранено " + new Date().toLocaleTimeString()); }
    catch (e) { setSave("ОШИБКА сохранения: " + e.message, true); toast("Не сохранилось: " + e.message, "err"); }
  },
  async flushAll() { for (const n of Object.keys(this.timers)) if (this.timers[n]) await this.flush(n); },
};
async function getHeroNames() { try { return (await Docs.get("world")).heroes.map(x => x.name); } catch (e) { return []; } }
async function getPlaceNames() { try { return (await Docs.get("world")).places.map(x => x.name); } catch (e) { return []; } }
async function sendToClaude(text, priority = "normal", attach = []) {
  const m = await Docs.get("messages");
  m.unshift({ id: uid(), text, priority, status: "new", created: nowIso(), attach });
  Docs.save("messages"); toast("Отправлено в «Для Claude»", "ok");
}

/* ---------------------------------------------------------------- jobs (long actions on the server) */
function appendLines(con, lines) {
  for (const ln of lines) {
    const cls = /^\$/.test(ln) ? "c" : /error|ошибк|fail|exception|не удалось|завершено с ошибкой|ВНИМАНИЕ/i.test(ln) ? "e" : /\bOK\b|готово|ALL OK|скопировано|Сохранено/i.test(ln) ? "g" : "";
    con.append(h("div", { class: cls }, ln));
  }
  con.scrollTop = con.scrollHeight;
}
function compareView(before, after) {
  const wrap = h("div", { class: "cmp" }), a = h("img", { src: before }), b = h("img", { class: "top", src: after });
  const bar = h("div", { class: "barv" }), set = pct => { pct = Math.max(0, Math.min(100, pct)); b.style.clipPath = `inset(0 0 0 ${pct}%)`; bar.style.left = pct + "%"; };
  set(50);
  const move = e => { const r = wrap.getBoundingClientRect(); set((e.clientX - r.left) / r.width * 100); };
  wrap.addEventListener("pointerdown", e => { wrap.setPointerCapture(e.pointerId); move(e); wrap.onpointermove = move; });
  wrap.addEventListener("pointerup", () => { wrap.onpointermove = null; });
  wrap.append(a, b, bar, h("span", { class: "lab l" }, "без шейдера"), h("span", { class: "lab r" }, "с шейдером"));
  return wrap;
}
function showImages(box, images) {
  box.innerHTML = "";
  for (const im of images)
    box.append(h("div", { class: "pair" }, h("div", { class: "muted small", style: "margin-bottom:6px" }, im.name + (im.before ? "  ·  тяни ползунок: слева без шейдера, справа с ним" : "")),
      im.before ? compareView(im.before, im.after) : h("a", { href: im.after, target: "_blank" }, h("img", { src: im.after, style: "width:100%;border-radius:10px" }))));
}
async function runJob(kind, params, con, results, btn) {
  if (btn) btn.disabled = true;
  con.innerHTML = ""; if (results) results.innerHTML = "";
  try {
    const { id } = await post("/api/job", { kind, params: params || {} });
    con.dataset.job = id;                              // so a "stop" button knows which job to stop
    let since = 0;
    for (;;) {
      const j = await api(`/api/job?id=${id}&since=${since}`);
      appendLines(con, j.lines); since = j.next;
      if (j.status !== "running") { if (results) showImages(results, j.images); return j; }
      await sleep(600);
    }
  } catch (e) { appendLines(con, ["Ошибка связи с сервером: " + e.message]); }
  finally { if (btn) btn.disabled = false; }
}
const jobBtn = (label, kind, params, con, results, cls = "btn") => { const b = h("button", { class: cls }, label); b.addEventListener("click", () => runJob(kind, typeof params === "function" ? params() : params, con, results, b)); return b; };

/* ---------------------------------------------------------------- router */
const PAGES = {};         // key -> { title, icon, group, order, render(root) }
const S = { page: "dashboard", leave: [], badges: {} };
const onLeave = fn => S.leave.push(fn);
function registerPage(key, def) { PAGES[key] = def; }
const CORE_PAGES = ["dashboard", "episode", "claude", "shader"];       // what the simple mode shows
function navLink(key, p) {
  return h("a", { class: S.page === key ? "on" : "", href: "#" + key, onclick: () => $("#side").classList.remove("open") }, icon(p.icon), p.title, S.badges[key] ? h("span", { class: "bd" }, S.badges[key]) : null);
}
function renderNav() {
  const nav = $("#nav"); nav.innerHTML = "";
  const simple = store("ls_simple") !== "0";
  const rest = group => Object.entries(PAGES).filter(([k, p]) => p.group === group && (!simple || !CORE_PAGES.includes(k))).sort((a, b) => a[1].order - b[1].order);
  if (simple) {
    nav.append(h("div", { class: "grp" }, "Главное"));
    for (const k of CORE_PAGES) if (PAGES[k]) nav.append(navLink(k, PAGES[k]));
    const open = S.more || !CORE_PAGES.includes(S.page);
    nav.append(h("a", { onclick: () => { S.more = !open; renderNav(); } }, icon("grid"), open ? "Скрыть остальное" : "Ещё разделы"));
    if (open) for (const group of ["Работа", "Инструменты"]) { const items = rest(group); if (!items.length) continue; nav.append(h("div", { class: "grp" }, group)); for (const [k, p] of items) nav.append(navLink(k, p)); }
  } else {
    for (const group of ["Работа", "Инструменты"]) { nav.append(h("div", { class: "grp" }, group)); for (const [k, p] of rest(group)) nav.append(navLink(k, p)); }
  }
  let mb = $("#modebtn");
  if (!mb) { mb = h("button", { class: "kbd-hint", id: "modebtn", style: "margin-top:8px" }); $(".side-foot").append(mb); }
  mb.textContent = simple ? "Простой режим: включён" : "Простой режим: выключен";
  mb.onclick = () => { store("ls_simple", simple ? "0" : "1"); S.more = false; renderNav(); };
}
async function renderPage() {
  for (const fn of S.leave.splice(0)) { try { await fn(); } catch (e) { /* ignore */ } }
  const p = PAGES[S.page] || PAGES.dashboard, root = $("#app");
  $("#ptitle").textContent = p.title; document.title = p.title + " · Lotos Studio";
  root.innerHTML = ""; renderNav();
  try { await p.render(root); } catch (e) { root.append(h("div", { class: "card" }, h("h2", {}, "Ошибка страницы"), h("pre", { class: "console" }, String(e && e.stack || e)))); }
}
function go(key) { if (location.hash === "#" + key) renderPage(); else location.hash = key; }
window.addEventListener("hashchange", () => { const k = location.hash.slice(1); if (PAGES[k] && k !== S.page || (PAGES[k] && k === S.page)) { S.page = k; store("ls_page", k); renderPage(); } });
async function refreshBadges() {
  try {
    const [m, t] = await Promise.all([Docs.get("messages"), Docs.get("tasks")]);
    S.badges.claude = m.filter(x => x.status === "new").length || 0; S.badges.tasks = t.filter(x => !x.done).length || 0;
    renderNav();
  } catch (e) { /* ignore */ }
}

/* ---------------------------------------------------------------- command palette and quick capture */
async function commandList() {
  const cmds = Object.entries(PAGES).map(([k, p]) => ({ label: p.title, hint: "перейти", run: () => go(k) }));
  cmds.push({ label: "Быстрая запись", hint: "Ctrl+J", run: quickCapture });
  cmds.push({ label: "Скопировать «прочитай for_claude.md»", hint: "для чата", run: () => copyText("Прочитай art/episodes/for_claude.md и сделай по списку") });
  try { for (const p of await Docs.get("wiki")) cmds.push({ label: p.title, hint: "вики", run: () => { store("ls_wiki", p.id); go("wiki"); } }); } catch (e) { /* ignore */ }
  try { for (const x of (await Docs.get("world")).heroes) cmds.push({ label: x.name, hint: "герой", run: () => { store("ls_hero", x.id); go("world"); } }); } catch (e) { /* ignore */ }
  return cmds;
}
async function openPalette() {
  const all = await commandList(); let idx = 0, list = all;
  const inp = h("input", { type: "text", placeholder: "Куда перейти или что найти…" }), box = h("div", {});
  const draw = () => { box.innerHTML = ""; list.slice(0, 12).forEach((c, i) => box.append(h("div", { class: "it" + (i === idx ? " on" : ""), onclick: () => { m.close(); c.run(); } }, c.label, h("small", {}, c.hint)))); };
  const m = modal({ body: h("div", { class: "pal" }, inp, box) }); draw(); setTimeout(() => inp.focus(), 20);
  inp.addEventListener("input", () => { const q = lc(inp.value); list = all.filter(c => lc(c.label).includes(q) || lc(c.hint).includes(q)); idx = 0; draw(); });
  inp.addEventListener("keydown", e => {
    if (e.key === "ArrowDown") { idx = Math.min(idx + 1, Math.min(list.length, 12) - 1); draw(); e.preventDefault(); }
    else if (e.key === "ArrowUp") { idx = Math.max(idx - 1, 0); draw(); e.preventDefault(); }
    else if (e.key === "Enter" && list[idx]) { m.close(); list[idx].run(); }
  });
}
function quickCapture() {
  const kind = h("select", {}, [["idea", "Идея"], ["improve", "Улучшение проекта"], ["suggestion", "Предложение"], ["claude", "Сообщение для Claude"], ["task", "Дело"]].map(([v, t]) => h("option", { value: v }, t)));
  const text = h("textarea", { placeholder: "Что записать? Одной-двумя фразами." });
  const m = modal({ title: "Быстрая запись", body: h("div", {}, h("label", { class: "f" }, h("span", {}, "Куда"), kind), text),
    actions: [{ label: "Отмена" }, { label: "Записать", primary: true, close: false, onclick: async () => {
      const t = text.value.trim(); if (!t) return;
      if (kind.value === "claude") await sendToClaude(t);
      else if (kind.value === "task") { const d = await Docs.get("tasks"); d.unshift({ id: uid(), text: t, done: false, prio: 1, created: nowIso() }); Docs.save("tasks"); toast("Записано в «Дела»", "ok"); }
      else { const d = await Docs.get("ideas"); d.unshift({ id: uid(), kind: kind.value, text: t, status: "new", votes: 0, tags: [], source: "author", created: nowIso() }); Docs.save("ideas"); toast("Записано", "ok"); }
      refreshBadges(); m.close(); if (S.page === "ideas" || S.page === "claude" || S.page === "tasks") renderPage();
    } }] });
  setTimeout(() => text.focus(), 30);
}
function shortcutsHelp() {
  const rows = [["Ctrl+K", "поиск и команды"], ["Ctrl+J", "быстрая запись идеи / сообщения / дела"], ["Ctrl+S", "сохранить сейчас"], ["Ctrl+Z / Ctrl+Y", "отменить / вернуть (в редакторе эпизода, вне полей ввода)"], ["Alt+↑ / Alt+↓", "переместить кадр"], ["Пробел", "старт/пауза в аниматике"], ["?", "эта подсказка"]];
  modal({ title: "Горячие клавиши", body: h("div", { class: "kv" }, rows.map(([a, b]) => [h("b", {}, a), h("span", {}, b)])), actions: [{ label: "Закрыть", primary: true }] });
}
