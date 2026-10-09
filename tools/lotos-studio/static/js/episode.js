"use strict";
/* Episode editor: storyboard with edit / grid / animatic views, timeline, templates, undo, filters. */

const P = {
  sizes: ["Общий план", "Средний план", "Крупный план", "Деталь (рука, предмет)", "Через плечо"],
  angles: ["На уровне глаз", "Сверху", "Снизу", "Со спины", "Сбоку", "От первого лица", "Голландский угол"],
  moves: ["Статика", "Медленный наезд", "Медленный отъезд", "Ручная камера (дрожь)", "Панорама", "Облёт", "Резкий рывок"],
  emotions: ["", "Спокойствие", "Злость", "Страх", "Пустота", "Боль", "Решимость", "Растерянность", "Ложь"],
  transitions: ["Резкая склейка", "Затемнение", "Вспышка", "Глитч", "Наплыв"],
  lights: [
    { name: "Холодный фиолет (как 13-я концовка)", key: "#6a4cff", fill: "#0a0818" },
    { name: "Красная тревога", key: "#ff2a2a", fill: "#1a0303" },
    { name: "Тёплый кабинет", key: "#ff9a4a", fill: "#1c0e05" },
    { name: "Тьма и один луч", key: "#cfd8ff", fill: "#02030a" },
    { name: "Бирюза лаборатории", key: "#4ad8d0", fill: "#031516" },
    { name: "Белая вспышка", key: "#ffffff", fill: "#1a1a26" },
    { name: "Свой цвет", key: null, fill: null },
  ],
  statuses: { draft: "✎ Черновик", ask: "? Нужно подтвердить", ok: "✔ Подтверждено", redo: "✖ Переделать" },
};
const TEMPLATES = [
  { name: "Диалог", set: { size: "Средний план", angle: "На уровне глаз", move: "Статика", duration: 5, emotion: "Спокойствие" } },
  { name: "Раскрытие", set: { size: "Крупный план", angle: "Снизу", move: "Медленный наезд", duration: 6, lightPreset: "Тьма и один луч", keyColor: "#cfd8ff", fillColor: "#02030a", emotion: "Страх" } },
  { name: "Погоня", set: { size: "Общий план", angle: "Со спины", move: "Ручная камера (дрожь)", duration: 3, lightPreset: "Красная тревога", keyColor: "#ff2a2a", fillColor: "#1a0303", emotion: "Страх" } },
  { name: "Тишина", set: { size: "Общий план", angle: "На уровне глаз", move: "Статика", duration: 7, lightPreset: "Тьма и один луч", keyColor: "#cfd8ff", fillColor: "#02030a", sound: "тишина", emotion: "Пустота" } },
  { name: "Удар", set: { size: "Деталь (рука, предмет)", angle: "Сбоку", move: "Резкий рывок", duration: 1.5, transition: "Вспышка", lightPreset: "Белая вспышка", keyColor: "#ffffff", fillColor: "#1a1a26", emotion: "Боль" } },
  { name: "Похищение", set: { size: "Средний план", angle: "Сверху", move: "Медленный отъезд", duration: 5, lightPreset: "Бирюза лаборатории", keyColor: "#4ad8d0", fillColor: "#031516", emotion: "Растерянность" } },
];

function blankShot(title = "Новый кадр") {
  return {
    id: uid(), title, location: "", characters: [], action: "", size: P.sizes[1], angle: P.angles[0], move: P.moves[0],
    lightPreset: P.lights[0].name, keyColor: P.lights[0].key, fillColor: P.lights[0].fill, contrast: 70, duration: 4,
    sound: "", text: "", emotion: "", transition: P.transitions[0], notes: "", refs: [], status: "draft", comment: "", rating: 0,
  };
}
const norm = s => Object.assign(blankShot(), s);

/* ---------------------------------------------------------------- colours and retelling */
function hex2rgb(x) { x = String(x || "#000000").replace("#", ""); if (x.length === 3) x = x.split("").map(c => c + c).join(""); const n = parseInt(x, 16) || 0; return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
const rgba = (x, a) => { const [r, g, b] = hex2rgb(x); return `rgba(${r},${g},${b},${a})`; };
const mixc = (a, b, t) => { const A = hex2rgb(a), B = hex2rgb(b); return `rgb(${A.map((v, i) => Math.round(v + (B[i] - v) * t)).join(",")})`; };
function retell(s, i) {
  const p = [];
  p.push(`Кадр ${i + 1}${s.title ? ` «${s.title}»` : ""}.`);
  if (s.location) p.push(`Место: ${s.location}.`);
  p.push(s.characters.length ? `В кадре: ${s.characters.join(", ")}.` : "В кадре никого нет.");
  if (s.action.trim()) p.push(`Происходит: ${s.action.trim().replace(/[.!]+$/, "")}.`);
  if (s.emotion) p.push(`Эмоция: ${lc(s.emotion)}.`);
  p.push(`Камера: ${lc(s.size)}, ${lc(s.angle)}, ${lc(s.move)}.`);
  p.push(`Свет: ${s.lightPreset === "Свой цвет" ? "свой цвет " + s.keyColor : lc(s.lightPreset)}, контраст ${s.contrast}%.`);
  p.push(`Длительность ${s.duration} с, переход: ${lc(s.transition)}.`);
  if (s.sound.trim()) p.push(`Звук: ${s.sound.trim()}.`);
  if (s.text.trim()) p.push(`Текст на экране: «${s.text.trim()}».`);
  return p.join("\n");
}
function readiness(s) {
  const checks = [s.title, s.location, s.characters.length, s.action.trim(), s.sound.trim() || s.text.trim(), s.emotion];
  return Math.round(checks.filter(Boolean).length / checks.length * 100);
}

/* ---------------------------------------------------------------- frame schematic (also used by the animatic) */
function wrapText(ctx, text, maxW) {
  const words = text.split(/\s+/), lines = []; let line = "";
  for (const w of words) { const t = line ? line + " " + w : w; if (ctx.measureText(t).width > maxW && line) { lines.push(line); line = w; } else line = t; }
  if (line) lines.push(line);
  return lines;
}
function drawPreview(cv, s, o = {}) {
  const W = cv.width = o.w || 640, H = cv.height = o.h || 360, k = W / 640, ctx = cv.getContext("2d");
  const key = s.keyColor || "#6a4cff", fill = s.fillColor || "#0a0818", con = (s.contrast ?? 70) / 100;
  const ang = s.angle || "", size = s.size || "", prog = o.progress ?? 0.5;
  ctx.fillStyle = "#000"; ctx.fillRect(0, 0, W, H);
  ctx.save();
  // slow push-in / pull-out and shake follow the camera move, driven by progress
  const mv = s.move || "";
  let zoom = 1;
  if (/наезд/.test(mv)) zoom = 1 + .18 * prog; else if (/отъезд/.test(mv)) zoom = 1.18 - .18 * prog;
  if (zoom !== 1) { ctx.translate(W / 2, H / 2); ctx.scale(zoom, zoom); ctx.translate(-W / 2, -H / 2); }
  if (/Ручная/.test(mv) && o.time != null) ctx.translate(Math.sin(o.time * 17) * 3 * k, Math.cos(o.time * 13) * 3 * k);
  if (/Панорама/.test(mv)) ctx.translate((prog - .5) * -W * .12, 0);
  if (ang.startsWith("Голландский")) { ctx.translate(W / 2, H / 2); ctx.rotate(-0.14); ctx.scale(1.3, 1.3); ctx.translate(-W / 2, -H / 2); }
  const g = ctx.createLinearGradient(0, 0, 0, H);
  g.addColorStop(0, mixc(fill, "#000000", .15)); g.addColorStop(1, mixc(fill, "#000000", .6));
  ctx.fillStyle = g; ctx.fillRect(-W * .3, -H * .3, W * 1.6, H * 1.6);
  const hz = ang.startsWith("Сверху") ? .3 : ang.startsWith("Снизу") ? .84 : .62;
  ctx.fillStyle = mixc(fill, "#000000", .45); ctx.fillRect(-W * .3, H * hz, W * 1.6, H);
  ctx.strokeStyle = rgba(key, .25); ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(-W * .3, H * hz); ctx.lineTo(W * 1.3, H * hz); ctx.stroke();
  const flick = o.time != null && /Красная/.test(s.lightPreset || "") ? .85 + .15 * Math.sin(o.time * 9) : 1;
  const lx = W * (ang.startsWith("Сбоку") ? .18 : .72), ly = H * .14;
  const rg = ctx.createRadialGradient(lx, ly, 0, lx, ly, W * .75);
  rg.addColorStop(0, rgba(key, (.25 + .5 * con) * flick)); rg.addColorStop(1, rgba(key, 0));
  ctx.fillStyle = rg; ctx.fillRect(-W * .3, -H * .3, W * 1.6, H * 1.6);

  const names = s.characters.slice(0, 6);
  ctx.fillStyle = "#05040a"; ctx.strokeStyle = rgba(key, .9);
  if (size.startsWith("Деталь")) {
    ctx.lineWidth = 3 * k;
    ctx.beginPath(); ctx.ellipse(W * .5, H * .58, W * .2, H * .25, -.3, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
    for (let i = 0; i < 4; i++) { ctx.beginPath(); ctx.ellipse(W * (.37 + i * .085), H * .3, W * .026, H * .13, -.15 + i * .09, 0, Math.PI * 2); ctx.fill(); ctx.stroke(); }
  } else {
    const sc = size.startsWith("Общий") ? .32 : size.startsWith("Средний") ? .6 : size.startsWith("Крупный") ? 1.15 : .95;
    names.forEach((nm, i) => {
      const cx = W * (i + 1) / (names.length + 1) + (size.startsWith("Через") && i === 0 ? -W * .14 : 0);
      const breathe = o.time != null ? Math.sin(o.time * 2 + i) * 1.5 * k : 0;
      const bodyH = H * .62 * sc, bodyW = bodyH * .34, headR = bodyH * .115;
      const baseY = H * hz + bodyH * .28 * (size.startsWith("Крупный") ? 1.9 : 1);
      const topY = baseY - bodyH + breathe;
      ctx.lineWidth = Math.max(1.5, 3 * sc) * k;
      ctx.beginPath(); ctx.rect(cx - bodyW / 2, topY + headR * 2.1, bodyW, bodyH - headR * 2.1); ctx.fill(); ctx.stroke();
      ctx.beginPath(); ctx.arc(cx, topY + headR, headR, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
      if (sc <= .7) { ctx.fillStyle = "rgba(255,255,255,.7)"; ctx.font = `${11 * k}px Segoe UI`; ctx.textAlign = "center"; ctx.fillText(nm.replace(/\s*\(.*\)/, ""), cx, topY - 6 * k); ctx.textAlign = "left"; ctx.fillStyle = "#05040a"; }
    });
  }
  ctx.restore();

  const vg = ctx.createRadialGradient(W / 2, H / 2, H * .3, W / 2, H / 2, W * .66);
  vg.addColorStop(0, "rgba(0,0,0,0)"); vg.addColorStop(1, `rgba(0,0,0,${.3 + .45 * con})`);
  ctx.fillStyle = vg; ctx.fillRect(0, 0, W, H);
  if (!o.clean) {
    ctx.fillStyle = "rgba(255,255,255,.85)"; ctx.font = `${13 * k}px Segoe UI`; ctx.textBaseline = "top";
    ctx.fillText(`${size} · ${mv}`, 12 * k, 10 * k);
    if (/наезд|отъезд/.test(mv) && o.arrows !== false) {
      const inward = /наезд/.test(mv); ctx.strokeStyle = "rgba(255,255,255,.7)"; ctx.lineWidth = 2 * k;
      for (const [x, y, dx, dy] of [[30, 50, 1, 1], [W / k - 30, 50, -1, 1], [30, H / k - 50, 1, -1], [W / k - 30, H / k - 50, -1, -1]]) {
        const s2 = inward ? 1 : -1; ctx.beginPath(); ctx.moveTo(x * k, y * k); ctx.lineTo((x + 26 * dx * s2) * k, (y + 26 * dy * s2) * k); ctx.stroke();
      }
    }
  }
  if (s.text) {
    ctx.font = `${16 * k}px Georgia, serif`; ctx.textAlign = "center"; ctx.textBaseline = "alphabetic";
    const lines = wrapText(ctx, s.text, W * .8);
    lines.forEach((ln, i) => { const y = H - 20 * k - (lines.length - 1 - i) * 22 * k; ctx.fillStyle = "rgba(0,0,0,.75)"; ctx.fillText(ln, W / 2 + 1, y + 1); ctx.fillStyle = "#fff"; ctx.fillText(ln, W / 2, y); });
    ctx.textAlign = "left";
  }
}
const thumbCache = new Map();
function thumbUrl(s) {
  const key = JSON.stringify([s.keyColor, s.fillColor, s.contrast, s.size, s.angle, s.move, s.characters, s.text, s.lightPreset]);
  if (!thumbCache.has(key)) { const c = document.createElement("canvas"); drawPreview(c, s, { w: 320, h: 180, clean: false }); thumbCache.set(key, c.toDataURL("image/jpeg", .8)); }
  return thumbCache.get(key);
}

/* ---------------------------------------------------------------- state */
const E = { eps: [], ep: null, sel: 0, dirty: false, timer: null, view: "edit", status: "", q: "", undo: [], redo: [], base: "", snapT: null, heroes: [], places: [] };
const cur = () => (E.ep && E.ep.shots[E.sel]) || null;
const snap = () => JSON.stringify({ title: E.ep.title, shots: E.ep.shots });
function changed() { E.dirty = true; refreshList(); refreshSide(); refreshTimeline(); scheduleSave(); clearTimeout(E.snapT); E.snapT = setTimeout(commitSnap, 600); }
function commitSnap() { if (!E.ep) return; clearTimeout(E.snapT); E.snapT = null; const now = snap(); if (now !== E.base) { E.undo.push(E.base); if (E.undo.length > 60) E.undo.shift(); E.base = now; E.redo = []; } }
function applySnap(str) { const d = JSON.parse(str); E.ep.title = d.title; E.ep.shots = d.shots.map(norm); E.sel = Math.min(E.sel, E.ep.shots.length - 1); E.base = str; E.dirty = true; scheduleSave(); renderPage(); }
function undo() { commitSnap(); if (!E.undo.length) return toast("Больше нечего отменять"); E.redo.push(snap()); applySnap(E.undo.pop()); }
function redo() { if (!E.redo.length) return toast("Нечего возвращать"); E.undo.push(snap()); applySnap(E.redo.pop()); }
function scheduleSave() { setSave("есть изменения…"); clearTimeout(E.timer); E.timer = setTimeout(saveNow, 900); }
async function saveNow() {
  clearTimeout(E.timer); if (!E.ep || !E.dirty) return;
  try { const r = await post(`/api/episode?id=${E.ep.id}`, E.ep); E.dirty = false; setSave("сохранено " + new Date().toLocaleTimeString() + (r.changed ? " · выгружено в art/episodes" : "")); }
  catch (e) { setSave("ОШИБКА сохранения: " + e.message, true); }
}
window.addEventListener("beforeunload", e => { if (E.dirty) { e.preventDefault(); e.returnValue = ""; } });
async function loadEpisode(id) {
  const ep = await api(`/api/episode?id=${id}`);
  ep.shots = (ep.shots || []).map(norm); if (!ep.shots.length) ep.shots.push(blankShot("Вступление"));
  E.ep = ep; E.sel = 0; E.dirty = false; E.undo = []; E.redo = []; E.base = snap(); store("ls_ep", id);
}
async function uploadFile(file) {
  const r = await fetch("/api/upload?name=" + encodeURIComponent(file.name || "paste.png"), { method: "POST", body: await file.arrayBuffer() });
  if (!r.ok) throw new Error(await r.text());
  return (await r.json()).file;
}
async function addRefs(files) {
  const s = cur(); if (!s) return;
  for (const f of files) { if (!f.type.startsWith("image/")) continue; try { s.refs.push(await uploadFile(f)); } catch (e) { toast("Не загрузилось: " + e.message, "err"); } }
  buildForm($("#formcol")); changed();
}
document.addEventListener("paste", e => {
  if (S.page !== "episode" || E.view !== "edit") return;
  const files = [...(e.clipboardData?.items || [])].filter(i => i.type.startsWith("image/")).map(i => i.getAsFile()).filter(Boolean);
  if (files.length) { e.preventDefault(); addRefs(files); }
});
document.addEventListener("keydown", e => {
  if (S.page !== "episode") return;
  const typing = /INPUT|TEXTAREA|SELECT/.test(document.activeElement?.tagName || "");
  if ((e.ctrlKey || e.metaKey) && e.key === "s") { e.preventDefault(); saveNow(); }
  else if (!typing && (e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "z") { e.preventDefault(); undo(); }
  else if (!typing && (e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "y") { e.preventDefault(); redo(); }
  else if (e.altKey && e.key === "ArrowUp") { e.preventDefault(); moveShot(-1); }
  else if (e.altKey && e.key === "ArrowDown") { e.preventDefault(); moveShot(1); }
});
function moveShot(d) { const a = E.ep.shots, j = E.sel + d; if (j < 0 || j >= a.length) return; [a[E.sel], a[j]] = [a[j], a[E.sel]]; E.sel = j; refreshList(); refreshTimeline(); scheduleSave(); clearTimeout(E.snapT); E.snapT = setTimeout(commitSnap, 600); }
function addShot(set) { const s = Object.assign(blankShot(), set || {}); E.ep.shots.splice(E.sel + 1, 0, s); E.sel = E.sel + 1; E.dirty = true; commitSnap(); scheduleSave(); renderPage(); }

/* ---------------------------------------------------------------- page */
registerPage("episode", {
  title: "Эпизод", icon: "film", group: "Работа", order: 1,
  async render(root) {
    if (!E.ep) {
      E.eps = await api("/api/episodes"); const want = store("ls_ep");
      await loadEpisode(E.eps.some(e => e.id === want) ? want : E.eps[0].id);
    }
    E.heroes = await getHeroNames(); E.places = await getPlaceNames();
    onLeave(() => { stopAnimatic(); return saveNow(); });
    const views = [["edit", "Редактор", "film"], ["grid", "Сетка кадров", "grid"], ["anim", "Аниматик", "play"]];
    root.append(
      h("div", { class: "toolbar" },
        h("div", { class: "tabs", style: "margin:0;border:0" }, views.map(([v, t, ic]) => h("button", { class: E.view === v ? "on" : "", onclick: () => { stopAnimatic(); E.view = v; renderPage(); } }, icon(ic, 15), " ", t))),
        h("div", { class: "grow" }),
        h("button", { class: "btn sm", onclick: undo, title: "Ctrl+Z" }, "↶ отменить"), h("button", { class: "btn sm", onclick: redo, title: "Ctrl+Y" }, "↷ вернуть")),
      h("div", { class: "timeline", id: "timeline" }));
    refreshTimeline();
    if (E.view === "edit") renderEdit(root); else if (E.view === "grid") renderGrid(root); else renderAnimatic(root);
  },
});

function refreshTimeline() {
  const tl = $("#timeline"); if (!tl) return; tl.innerHTML = "";
  const total = E.ep.shots.reduce((a, s) => a + (Number(s.duration) || 1), 0) || 1;
  let dragFrom = -1;
  E.ep.shots.forEach((s, i) => {
    const seg = h("div", { class: `seg ${s.status} ${i === E.sel ? "on" : ""}`, draggable: "true", title: `${i + 1}. ${s.title} · ${s.duration} с`,
      style: `flex:${Math.max(Number(s.duration) || 1, .5)} 1 0;background:linear-gradient(135deg, ${s.keyColor}, ${mixc(s.fillColor, "#000000", .2)})`,
      onclick: () => { E.sel = i; if (E.view === "edit") { refreshList(); buildForm($("#formcol")); refreshSide(); refreshTimeline(); } else { E.view = "edit"; renderPage(); } } }, i + 1);
    seg.addEventListener("dragstart", () => { dragFrom = i; });
    seg.addEventListener("dragover", e => e.preventDefault());
    seg.addEventListener("drop", e => { e.preventDefault(); if (dragFrom < 0 || dragFrom === i) return; const [m] = E.ep.shots.splice(dragFrom, 1); E.ep.shots.splice(i, 0, m); E.sel = i; E.dirty = true; commitSnap(); scheduleSave(); renderPage(); });
    tl.append(seg);
  });
  tl.title = `Всего ${total.toFixed(1)} с. Перетаскивай кадры, чтобы менять порядок.`;
}

/* ---------------------------------------------------------------- edit view */
function renderEdit(root) {
  const left = h("aside", { class: "col", id: "listcol" }), mid = h("section", { class: "col", id: "formcol" }), right = h("aside", { class: "col", id: "sidecol" });
  root.append(h("datalist", { id: "dl-loc" }, E.places.map(l => h("option", { value: l }))), h("div", { class: "split3" }, left, mid, right));
  buildList(left); buildForm(mid); buildSide(right);
}
function buildList(left) {
  const epSel = h("select", { onchange: async () => { await saveNow(); await loadEpisode(epSel.value); renderPage(); } }, E.eps.map(e => h("option", { value: e.id }, `${e.title} (${e.shots})`)));
  epSel.value = E.ep.id;
  const title = h("input", { type: "text", value: E.ep.title, oninput: () => { E.ep.title = title.value; changed(); } });
  const tmpl = h("select", { onchange: () => { const t = TEMPLATES.find(x => x.name === tmpl.value); tmpl.value = ""; if (t) addShot(Object.assign({ title: t.name }, t.set)); } },
    h("option", { value: "" }, "+ из шаблона…"), TEMPLATES.map(t => h("option", { value: t.name }, t.name)));
  const fst = h("select", { onchange: () => { E.status = fst.value; refreshList(); } }, h("option", { value: "" }, "все статусы"), Object.entries(P.statuses).map(([k, v]) => h("option", { value: k }, v)));
  fst.value = E.status;
  const q = h("input", { type: "search", placeholder: "поиск по кадрам", value: E.q, oninput: () => { E.q = q.value; refreshList(); } });
  left.append(
    h("div", { class: "card" }, h("h2", {}, "Эпизод"),
      h("div", { class: "row" }, epSel, h("button", { class: "btn sm", onclick: async () => {
        const t = await promptBox("Название нового эпизода", "Эпизод " + (E.eps.length + 1)); if (!t) return;
        await saveNow(); const r = await post("/api/episode_new", { title: t }); E.eps = await api("/api/episodes"); await loadEpisode(r.id); renderPage();
      } }, "+ новый")),
      h("label", { class: "f", style: "margin-top:8px" }, h("span", {}, "Название"), title), h("div", { class: "muted small", id: "epstat" })),
    h("div", { class: "card" }, h("h2", {}, "Кадры"), q, h("div", { style: "height:6px" }), fst, h("div", { id: "shotlist", style: "margin-top:8px" }),
      h("div", { class: "row", style: "margin-top:8px" },
        h("button", { class: "btn sm primary", onclick: () => addShot() }, "+ кадр"),
        h("button", { class: "btn sm", onclick: () => { const c = JSON.parse(JSON.stringify(cur())); c.id = uid(); c.status = "draft"; E.ep.shots.splice(E.sel + 1, 0, c); E.sel++; E.dirty = true; commitSnap(); scheduleSave(); renderPage(); } }, "копия"),
        h("button", { class: "btn sm", onclick: () => moveShot(-1), title: "Alt+↑" }, "↑"), h("button", { class: "btn sm", onclick: () => moveShot(1), title: "Alt+↓" }, "↓"),
        h("button", { class: "btn sm danger", onclick: async () => { if (E.ep.shots.length < 2 || !await confirmBox("Удалить кадр? Это можно отменить кнопкой «отменить».")) return; E.ep.shots.splice(E.sel, 1); E.sel = Math.max(0, E.sel - 1); E.dirty = true; commitSnap(); scheduleSave(); renderPage(); } }, "удалить")),
      h("div", { style: "margin-top:8px" }, tmpl)),
    h("div", { class: "card" }, h("h2", {}, "Экспорт и история"),
      h("div", { class: "toolbar" },
        h("button", { class: "btn sm", onclick: printSheet }, "Лист для печати"),
        h("button", { class: "btn sm", onclick: () => copyText(E.ep.shots.map((s, i) => retell(s, i)).join("\n\n")) }, "Скопировать пересказ"),
        h("button", { class: "btn sm", onclick: () => download(E.ep.id + ".json", JSON.stringify(E.ep, null, 2), "application/json") }, "Скачать JSON"),
        h("label", { class: "btn sm" }, "Загрузить JSON", h("input", { type: "file", accept: ".json", style: "display:none", onchange: async ev => {
          const f = ev.target.files[0]; if (!f) return; try { const d = JSON.parse(await f.text()); if (!Array.isArray(d.shots)) throw new Error("нет списка shots");
            if (!await confirmBox("Заменить текущий эпизод содержимым файла? Старая версия останется в истории.")) return; E.ep.title = d.title || E.ep.title; E.ep.shots = d.shots.map(norm); E.dirty = true; commitSnap(); scheduleSave(); renderPage(); } catch (e) { toast("Файл не подошёл: " + e.message, "err"); } } }))),
      h("div", { class: "muted small" }, "Каждое сохранение выгружает art/episodes/<эпизод>.md и .json: оттуда это читает Claude (в git не попадает)."),
      h("div", { id: "hist", style: "margin-top:8px" }, h("button", { class: "btn sm", onclick: showHistory }, "История версий"))));
  refreshList();
}
function refreshList() {
  const box = $("#shotlist"); if (!box) return; box.innerHTML = "";
  const q = lc(E.q);
  E.ep.shots.forEach((s, i) => {
    if (E.status && s.status !== E.status) return;
    if (q && !lc([s.title, s.action, s.location, s.characters.join(" "), s.text, s.sound].join(" ")).includes(q)) return;
    box.append(h("div", { class: "shotitem" + (i === E.sel ? " on" : ""), onclick: () => selectShot(i) }, h("div", { class: "n" }, i + 1),
      h("div", { style: "min-width:0;flex:1" }, h("div", { class: "t" }, s.title || "(без названия)"), h("div", { class: "s" }, `${s.size} · ${s.duration} с · готовность ${readiness(s)}%`)),
      h("div", { class: "badge " + s.status }, P.statuses[s.status].split(" ")[0])));
  });
  const total = E.ep.shots.reduce((a, s) => a + (Number(s.duration) || 0), 0), ok = E.ep.shots.filter(s => s.status === "ok").length;
  const st = $("#epstat"); if (st) st.textContent = `Кадров: ${E.ep.shots.length} · ${total.toFixed(1)} с · подтверждено ${ok}/${E.ep.shots.length}`;
}
function selectShot(i) { E.sel = i; refreshList(); buildForm($("#formcol")); refreshSide(); refreshTimeline(); }

function chipsField(options, arr) {
  const wrap = h("div", {}), box = h("div", { class: "chips" });
  const draw = () => { box.innerHTML = ""; for (const o of [...options, ...arr.filter(x => !options.includes(x))]) box.append(h("button", { type: "button", class: "chip" + (arr.includes(o) ? " on" : ""), onclick: () => { const k = arr.indexOf(o); if (k >= 0) arr.splice(k, 1); else arr.push(o); draw(); changed(); } }, o)); };
  draw();
  const add = h("input", { type: "text", placeholder: "добавить своё и нажать Enter", onkeydown: e => { if (e.key === "Enter" && add.value.trim()) { arr.push(add.value.trim()); add.value = ""; draw(); changed(); } } });
  wrap.append(box, add); return wrap;
}
function buildForm(mid) {
  mid.innerHTML = ""; const s = cur();
  if (!s) { mid.append(h("div", { class: "card muted" }, "Нет кадров.")); return; }
  const f = (label, el) => h("label", { class: "f" }, h("span", {}, label), el);
  const text = (key, ph) => { const el = h("input", { type: "text", placeholder: ph || "" }); el.value = s[key] ?? ""; el.addEventListener("input", () => { s[key] = el.value; changed(); }); return el; };
  const area = (key, ph) => { const el = h("textarea", { placeholder: ph || "" }); el.value = s[key] ?? ""; el.addEventListener("input", () => { s[key] = el.value; changed(); }); return el; };
  const select = (key, opts) => { const o = opts.includes(s[key]) ? opts : [...opts, s[key]]; const el = h("select", {}, o.map(x => h("option", { value: x }, x || "—"))); el.value = s[key]; el.addEventListener("change", () => { s[key] = el.value; changed(); }); return el; };
  const lightSel = h("select", {}, P.lights.map(l => h("option", { value: l.name }, l.name))), keyIn = h("input", { type: "color" }), fillIn = h("input", { type: "color" });
  lightSel.value = s.lightPreset; keyIn.value = s.keyColor; fillIn.value = s.fillColor;
  lightSel.addEventListener("change", () => { s.lightPreset = lightSel.value; const L = P.lights.find(x => x.name === s.lightPreset); if (L && L.key) { s.keyColor = L.key; s.fillColor = L.fill; keyIn.value = s.keyColor; fillIn.value = s.fillColor; } changed(); });
  const custom = () => { s.keyColor = keyIn.value; s.fillColor = fillIn.value; s.lightPreset = "Свой цвет"; lightSel.value = "Свой цвет"; changed(); };
  keyIn.addEventListener("input", custom); fillIn.addEventListener("input", custom);
  const contrast = h("input", { type: "range", min: 0, max: 100, value: s.contrast }); contrast.addEventListener("input", () => { s.contrast = Number(contrast.value); changed(); });
  const dur = h("input", { type: "number", min: 0.5, max: 600, step: 0.5, value: s.duration }); dur.addEventListener("input", () => { s.duration = Number(dur.value) || 0; changed(); });
  const rating = h("input", { type: "range", min: 0, max: 10, value: s.rating }), rl = h("span", { class: "tag warn" }, s.rating ? s.rating + "/10" : "нет");
  rating.addEventListener("input", () => { s.rating = Number(rating.value); rl.textContent = s.rating ? s.rating + "/10" : "нет"; changed(); });
  const loc = text("location", "где происходит"); loc.setAttribute("list", "dl-loc");
  const refs = h("div", { class: "refs" }, s.refs.map((r, i) => h("div", { class: "ref" }, h("a", { href: "/refs/" + r, target: "_blank" }, h("img", { src: "/refs/" + r })), h("button", { class: "x", type: "button", onclick: () => { s.refs.splice(i, 1); buildForm($("#formcol")); changed(); } }, "×"))));
  const file = h("input", { type: "file", accept: "image/*", multiple: true, onchange: () => addRefs([...file.files]) });
  const drop = h("div", { class: "muted", style: "border:1px dashed var(--line2);border-radius:10px;padding:12px;margin-top:8px;text-align:center" }, "Перетащи сюда свой рисунок, фото или скриншот. Или вставь из буфера Ctrl+V.");
  drop.addEventListener("dragover", e => e.preventDefault()); drop.addEventListener("drop", e => { e.preventDefault(); addRefs([...e.dataTransfer.files]); });
  mid.append(
    h("div", { class: "card" }, h("h2", {}, `Кадр ${E.sel + 1}: что в кадре`, h("span", { class: "tag acc" }, "готовность " + readiness(s) + "%")),
      f("Название кадра", text("title", "коротко, для списка")), f("Место", loc), f("Кто в кадре", chipsField(E.heroes.length ? E.heroes : ["Рей (игрок)"], s.characters)),
      f("Что происходит (своими словами)", area("action", "что видно и что делают")), f("Эмоция", select("emotion", P.emotions))),
    h("div", { class: "card" }, h("h2", {}, "Камера"), h("div", { class: "grid g3" }, f("План", select("size", P.sizes)), f("Ракурс", select("angle", P.angles)), f("Движение", select("move", P.moves)))),
    h("div", { class: "card" }, h("h2", {}, "Свет"), f("Пресет", lightSel), h("div", { class: "row" }, h("span", { class: "muted" }, "основной"), keyIn, h("span", { class: "muted" }, "фон"), fillIn, h("span", { class: "muted", style: "margin-left:10px" }, "контраст"), h("div", { style: "flex:1;min-width:120px" }, contrast))),
    h("div", { class: "card" }, h("h2", {}, "Время, звук, текст"), h("div", { class: "grid g2" }, f("Длительность, с", dur), f("Переход", select("transition", P.transitions))),
      f("Звук и музыка", text("sound", "что слышно: шаги, дыхание, гул, тишина…")), f("Текст или субтитр на экране", area("text", "если есть")), f("Заметки для себя", area("notes", ""))),
    h("div", { class: "card" }, h("h2", {}, "Референсы (твои)"), refs, drop, h("div", { style: "margin-top:8px" }, file)),
    h("div", { class: "card" }, h("h2", {}, "Твоя оценка кадра"), h("div", { class: "row" }, h("div", { style: "flex:1" }, rating), rl)));
}
function buildSide(right) {
  right.append(
    h("div", { class: "card" }, h("h2", {}, "Предпросмотр кадра"), h("canvas", { class: "preview", id: "pv" }), h("div", { class: "muted small", style: "margin-top:6px" }, "Схема: свет, ракурс, масштаб героев, движение камеры. Не финальная картинка.")),
    h("div", { class: "card" }, h("h2", {}, "Как я это понял"), h("div", { class: "retell", id: "retell" }), h("div", { class: "stbtns", id: "stbtns" }),
      h("label", { class: "f" }, h("span", {}, "Твой комментарий (что не так / что важно)"), h("textarea", { id: "comment" })),
      h("div", { class: "row" }, h("button", { class: "btn sm", onclick: () => copyText(retell(cur(), E.sel)) }, "Скопировать пересказ"),
        h("button", { class: "btn sm", onclick: () => sendToClaude(`Кадр ${E.sel + 1} эпизода «${E.ep.title}» (${cur().title}). ${cur().comment || "Посмотри и скажи, что неясно."}\n\n${retell(cur(), E.sel)}`) }, "→ Claude"))));
  const cm = $("#comment"); cm.value = cur()?.comment || "";
  cm.addEventListener("input", () => { const s = cur(); if (s) { s.comment = cm.value; E.dirty = true; scheduleSave(); } });
  refreshSide();
}
function refreshSide() {
  const s = cur(); if (!s || !$("#pv")) return;
  drawPreview($("#pv"), s); $("#retell").textContent = retell(s, E.sel);
  const cm = $("#comment"); if (document.activeElement !== cm) cm.value = s.comment || "";
  const box = $("#stbtns"); box.innerHTML = "";
  for (const [k, label] of Object.entries(P.statuses)) box.append(h("button", { class: `btn ${k}` + (s.status === k ? " on" : ""), onclick: () => { s.status = k; changed(); } }, label));
}
async function showHistory() {
  const box = $("#hist"); box.innerHTML = ""; const files = await api(`/api/history?id=${E.ep.id}`);
  if (!files.length) { box.append(h("div", { class: "muted" }, "Пока нет старых версий.")); return; }
  for (const f of files.slice(0, 12)) {
    const m = f.match(/_(\d{8})_(\d{6})/); const label = m ? `${m[1].slice(6)}.${m[1].slice(4, 6)} ${m[2].slice(0, 2)}:${m[2].slice(2, 4)}:${m[2].slice(4)}` : f;
    box.append(h("div", { class: "row", style: "margin-bottom:4px" }, h("span", {}, label), h("button", { class: "btn sm", onclick: async () => {
      if (!await confirmBox("Вернуть эту версию? Текущая сохранится в истории.")) return;
      const ep = await api("/api/history_get?file=" + encodeURIComponent(f)); E.ep.title = ep.title; E.ep.shots = ep.shots.map(norm); E.sel = 0; E.dirty = true; commitSnap(); await saveNow(); renderPage();
    } }, "вернуть")));
  }
}
function printSheet() {
  const cv = document.createElement("canvas"), w = window.open("", "_blank"); if (!w) { toast("Браузер заблокировал окно печати", "err"); return; }
  let html = `<!doctype html><meta charset="utf-8"><title>${esc(E.ep.title)}</title><style>body{font:13px Segoe UI,sans-serif;margin:20px}.s{display:grid;grid-template-columns:330px 1fr;gap:12px;border:1px solid #999;margin:0 0 12px;padding:8px;page-break-inside:avoid}img{width:330px}pre{white-space:pre-wrap;margin:0;font:inherit}</style><h1>${esc(E.ep.title)}</h1>`;
  E.ep.shots.forEach((s, i) => { drawPreview(cv, s); html += `<div class="s"><img src="${cv.toDataURL()}"><pre>${esc(retell(s, i))}\n\n[${esc(P.statuses[s.status])}]${s.comment ? "\nКомментарий: " + esc(s.comment) : ""}</pre></div>`; });
  w.document.write(html); w.document.close(); setTimeout(() => w.print(), 400);
}

/* ---------------------------------------------------------------- grid view */
function renderGrid(root) {
  const box = h("div", { class: "thumbs" });
  E.ep.shots.forEach((s, i) => box.append(h("div", { class: "thumb", onclick: () => { E.sel = i; E.view = "edit"; renderPage(); } }, h("img", { src: thumbUrl(s) }),
    h("div", { class: "cap" }, h("div", { class: "row" }, h("b", {}, `${i + 1}. ${s.title}`), h("span", { class: "badge " + s.status }, P.statuses[s.status].split(" ")[0])),
      h("div", { class: "muted small" }, `${s.size} · ${s.duration} с · ${s.characters.join(", ") || "никого"}`)))));
  root.append(h("div", { class: "muted", style: "margin-bottom:10px" }, "Вся раскадровка одним взглядом. Нажми на кадр, чтобы править."), box);
}

/* ---------------------------------------------------------------- animatic */
const A = { raf: 0, playing: false, t: 0, last: 0, speak: false, prevIdx: -1, prevImg: null };
function stopAnimatic() { A.playing = false; clearTimeout(A.raf); try { speechSynthesis.cancel(); } catch (e) { /* ignore */ } }
function renderAnimatic(root) {
  const shots = E.ep.shots, total = shots.reduce((a, s) => a + (Number(s.duration) || 1), 0) || 1;
  const starts = []; let acc = 0; for (const s of shots) { starts.push(acc); acc += Number(s.duration) || 1; }
  const cv = h("canvas", {}), cap = h("div", { class: "caption" }), seek = h("input", { type: "range", min: 0, max: Math.round(total * 100), value: 0 });
  const tl = h("span", { class: "muted" }, "0.0 / " + total.toFixed(1) + " с"), playBtn = h("button", { class: "btn primary" }, icon("play", 16), " Играть");
  const speak = h("input", { type: "checkbox", onchange: () => { A.speak = speak.checked; } });
  const idxAt = t => { let i = 0; for (let k = 0; k < shots.length; k++) if (t >= starts[k]) i = k; return i; };
  const overlayFor = (s, local) => {
    const ctx = cv.getContext("2d"), W = cv.width, H = cv.height, d = Math.min(.45, (Number(s.duration) || 1) * .3);
    if (local > d) return; const k = 1 - local / d;
    if (s.transition === "Затемнение") { ctx.fillStyle = `rgba(0,0,0,${k})`; ctx.fillRect(0, 0, W, H); }
    else if (s.transition === "Вспышка") { ctx.fillStyle = `rgba(255,255,255,${k * .9})`; ctx.fillRect(0, 0, W, H); }
    else if (s.transition === "Глитч") { for (let i = 0; i < 9; i++) { const y = Math.random() * H, hh = 8 + Math.random() * 40, dx = (Math.random() - .5) * 90 * k; ctx.drawImage(cv, 0, y, W, hh, dx, y, W, hh); } ctx.fillStyle = `rgba(255,0,80,${.12 * k})`; ctx.fillRect(0, 0, W, H); }
    else if (s.transition === "Наплыв" && A.prevImg) { ctx.globalAlpha = k; ctx.drawImage(A.prevImg, 0, 0, W, H); ctx.globalAlpha = 1; }
  };
  const frame = () => {
    const t = Math.min(A.t, total - .001), i = idxAt(t), s = shots[i], local = t - starts[i], dur = Number(s.duration) || 1;
    if (i !== A.prevIdx) {
      if (A.prevIdx >= 0 && shots[A.prevIdx]) { const c = document.createElement("canvas"); drawPreview(c, shots[A.prevIdx], { w: cv.width, h: cv.height, progress: 1, clean: true }); A.prevImg = c; }
      A.prevIdx = i; E.sel = i; if (A.speak && s.text && playing()) { try { const u = new SpeechSynthesisUtterance(s.text); u.lang = "ru-RU"; speechSynthesis.cancel(); speechSynthesis.speak(u); } catch (e) { /* ignore */ } }
    }
    drawPreview(cv, s, { w: 960, h: 540, progress: local / dur, time: A.t, clean: true, arrows: false });
    overlayFor(s, local);
    cap.innerHTML = ""; cap.append(h("div", {}, h("b", {}, `${i + 1}. ${s.title}`), h("span", { class: "muted" }, `  ·  ${s.size}, ${s.move}`)), s.sound ? h("div", { class: "snd" }, "♪ " + s.sound) : null);
    seek.value = Math.round(A.t * 100); tl.textContent = `${A.t.toFixed(1)} / ${total.toFixed(1)} с`;
  };
  const playing = () => A.playing;
  const loop = ts => { if (!A.playing) return; const dt = (ts - A.last) / 1000; A.last = ts; A.t += dt; if (A.t >= total) { A.t = total - .001; frame(); setPlay(false); return; } frame(); A.raf = setTimeout(() => loop(performance.now()), 16); };
  const setPlay = on => { A.playing = on; playBtn.innerHTML = ""; playBtn.append(icon(on ? "pause" : "play", 16), on ? " Пауза" : " Играть"); if (on) { if (A.t >= total - .05) { A.t = 0; A.prevIdx = -1; } A.last = performance.now(); A.raf = setTimeout(() => loop(performance.now()), 16); } else { clearTimeout(A.raf); try { speechSynthesis.cancel(); } catch (e) { /* ignore */ } } };
  playBtn.onclick = () => setPlay(!A.playing);
  seek.oninput = () => { A.t = Number(seek.value) / 100; A.prevIdx = -1; frame(); };
  const jump = d => { const i = Math.max(0, Math.min(shots.length - 1, idxAt(A.t) + d)); A.t = starts[i]; A.prevIdx = -1; frame(); };
  document.onkeydown = e => { if (E.view === "anim" && S.page === "episode" && e.code === "Space" && !/INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName)) { e.preventDefault(); setPlay(!A.playing); } };
  A.t = 0; A.prevIdx = -1; A.prevImg = null;
  root.append(h("div", { class: "card anim" }, h("div", { class: "muted", style: "margin-bottom:8px" }, "Аниматик: схемы кадров по порядку и длительности, с переходами, звуковыми метками и текстом. Проверь ритм до того, как я что-то соберу."),
    cv, cap, seek, h("div", { class: "row", style: "margin-top:8px" }, h("button", { class: "btn", onclick: () => jump(-1) }, "⏮"), playBtn, h("button", { class: "btn", onclick: () => jump(1) }, "⏭"), tl,
      h("label", { class: "row tight", style: "margin-left:auto" }, speak, h("span", { class: "muted small" }, "озвучивать субтитры голосом браузера")))));
  frame();
}
MD.episodes = null;
