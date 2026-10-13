"use strict";
/* Timeline: a track (picked from disk, it never leaves the browser) with a waveform, and marks placed on it by time. */

const TL_KINDS = {
  anim: ["Анимация", "#60a5fa"], action: ["Действие", "#fbbf24"], game: ["Мини-игра", "#f472b6"],
  boss: ["Босс", "#f87171"], calm: ["Тишина / пауза", "#8e89ad"], other: ["Другое", "#4ade80"],
};
const tlFmt = s => { s = Math.max(0, s); const m = Math.floor(s / 60); return m + ":" + (s - m * 60).toFixed(1).padStart(4, "0"); };
const tlParse = v => {
  const m = String(v).trim().replace(",", ".").match(/^(?:(\d+):)?(\d+(?:\.\d+)?)$/);
  return m ? (m[1] ? Number(m[1]) * 60 : 0) + Number(m[2]) : null;
};

MD.timeline = d => {
  const marks = [...d.marks].sort((a, b) => a.t - b.t);
  return ["# Шкала: " + (d.track.name || "без трека"), "", `Длительность: ${tlFmt(d.track.duration || 0)} (${(d.track.duration || 0).toFixed(1)} с).`, "",
    "_Выгрузка из Lotos Studio. Каждая отметка длится до следующей. Это заготовка автора, не готовая раскадровка._", "",
    ...marks.flatMap((m, i) => {
      const end = i + 1 < marks.length ? marks[i + 1].t : d.track.duration || m.t;
      return [`## ${tlFmt(m.t)} – ${tlFmt(end)}  ${m.title || "(без названия)"}  [${(TL_KINDS[m.kind] || TL_KINDS.other)[0]}]`, "", m.text || "", ""];
    }),
    `## Нажатия пробела (${(d.taps || []).length})`, "",
    "_Время в миллисекундах от начала трека, по одному на строку (как файл записи бита в бою)._", "",
    ...[...(d.taps || [])].sort((a, b) => a - b).map(t => String(Math.round(t * 1000)))].join("\n");
};

registerPage("timeline", {
  title: "Шкала трека", icon: "pulse", group: "Работа", order: 3,
  async render(root) {
    const d = await Docs.get("timeline"), save = () => Docs.save("timeline");
    d.taps = d.taps || [];
    const audio = new Audio(); let peaks = null, zoom = 1, scroll = 0, sel = null, url = null, rec = false, flash = 0;
    onLeave(() => { audio.pause(); if (url) URL.revokeObjectURL(url); });
    const dur = () => audio.duration && isFinite(audio.duration) ? audio.duration : d.track.duration || 420;

    const canvas = h("canvas", { style: "width:100%;height:200px;display:block;border-radius:10px;background:#0a0814;cursor:pointer" });
    const clock = h("b", { style: "font-variant-numeric:tabular-nums;font-size:18px" }, "0:00.0");
    const info = h("div", { class: "muted small" });
    const list = h("div", {});
    const view = () => { const w = canvas.clientWidth, span = dur() / zoom; return { w, span, t0: Math.min(scroll, Math.max(0, dur() - span)) }; };
    const xOf = (t, v) => (t - v.t0) / v.span * v.w, tOf = (x, v) => v.t0 + x / v.w * v.span;
    const sorted = () => [...d.marks].sort((a, b) => a.t - b.t);

    const draw = () => {
      const dpr = window.devicePixelRatio || 1, w = canvas.clientWidth, hh = canvas.clientHeight;
      if (canvas.width !== w * dpr) { canvas.width = w * dpr; canvas.height = hh * dpr; }
      const g = canvas.getContext("2d"); g.setTransform(dpr, 0, 0, dpr, 0, 0); g.clearRect(0, 0, w, hh);
      const v = view(), ms = sorted();
      ms.forEach((m, i) => {
        const end = i + 1 < ms.length ? ms[i + 1].t : dur(), x1 = xOf(m.t, v), x2 = xOf(end, v), c = (TL_KINDS[m.kind] || TL_KINDS.other)[1];
        g.fillStyle = c + (m === sel ? "55" : "26"); g.fillRect(x1, 0, x2 - x1, hh);
      });
      if (peaks) {
        g.fillStyle = "#b79bff"; const mid = hh / 2 + 6;
        for (let x = 0; x < w; x++) {
          const i0 = Math.floor(tOf(x, v) / dur() * peaks.length), i1 = Math.max(i0 + 1, Math.floor(tOf(x + 1, v) / dur() * peaks.length));
          let p = 0; for (let i = i0; i < i1 && i < peaks.length; i++) p = Math.max(p, peaks[i]);
          g.fillRect(x, mid - p * (hh / 2 - 22), 1, Math.max(1, p * (hh - 44)));
        }
      } else { g.fillStyle = "#8e89ad"; g.font = "13px sans-serif"; g.fillText("Выбери файл трека (кнопка выше): он остаётся в твоём браузере, никуда не отправляется", 14, hh / 2); }
      g.font = "11px sans-serif"; g.textBaseline = "top";
      const step = [1, 2, 5, 10, 15, 30, 60, 120].find(s => s * w / v.span >= 70) || 120;
      for (let t = Math.ceil(v.t0 / step) * step; t < v.t0 + v.span; t += step) { const x = xOf(t, v); g.fillStyle = "#3a3463"; g.fillRect(x, 0, 1, 12); g.fillStyle = "#8e89ad"; g.fillText(tlFmt(t).replace(/\.0$/, ""), x + 3, 1); }
      for (const m of ms) {
        const x = xOf(m.t, v), c = (TL_KINDS[m.kind] || TL_KINDS.other)[1];
        g.fillStyle = c; g.fillRect(x, 14, 2, hh - 14); g.fillRect(x, hh - 20, Math.min(160, g.measureText(m.title || "").width + 10), 18);
        g.fillStyle = "#07060d"; g.fillText(m.title || "", x + 5, hh - 17);
      }
      for (const t of d.taps) {
        const x = xOf(t, v); if (x < -4 || x > w + 4) continue;
        g.fillStyle = "#ffffff"; g.beginPath(); g.arc(x, 30, 4, 0, 7); g.fill();
        g.fillStyle = "rgba(255,255,255,.22)"; g.fillRect(x, 34, 1, hh - 60);
      }
      if (rec) { g.fillStyle = "#f87171"; g.beginPath(); g.arc(w - 16, 16, 6, 0, 7); g.fill(); g.font = "11px sans-serif"; g.fillText("ЗАПИСЬ ПРОБЕЛА", w - 130, 10); }
      if (performance.now() - flash < 140) { g.strokeStyle = "#fff"; g.lineWidth = 3; g.strokeRect(1, 1, w - 2, hh - 2); }
      const px = xOf(audio.currentTime || 0, v); g.fillStyle = "#fff"; g.fillRect(px, 0, 2, hh);
      clock.textContent = tlFmt(audio.currentTime || 0);
    };
    let raf = 0; const loop = () => { if (!document.body.contains(canvas)) return; draw(); raf = requestAnimationFrame(loop); }; loop(); onLeave(() => cancelAnimationFrame(raf));

    const drawList = () => {
      list.innerHTML = "";
      for (const m of sorted()) {
        const k = TL_KINDS[m.kind] || TL_KINDS.other;
        list.append(h("div", { class: "card flat", style: `border-left:4px solid ${k[1]};padding:10px 12px;margin-bottom:8px`, onclick: () => { sel = m; } },
          h("div", { class: "row" },
            h("input", { type: "text", value: tlFmt(m.t), style: "width:84px;font-variant-numeric:tabular-nums", onchange: e => { const t = tlParse(e.target.value); if (t !== null) { m.t = t; save(); } drawList(); } }),
            h("input", { type: "text", value: m.title || "", placeholder: "что происходит (коротко)", style: "flex:1;min-width:160px", oninput: e => { m.title = e.target.value; save(); } }),
            h("select", { onchange: e => { m.kind = e.target.value; save(); drawList(); } }, Object.entries(TL_KINDS).map(([key, v]) => h("option", { value: key, selected: m.kind === key }, v[0]))),
            h("button", { class: "btn sm", title: "Прослушать с этого места", onclick: () => { audio.currentTime = m.t; audio.play(); } }, "▶"),
            h("button", { class: "btn sm danger", onclick: async () => { if (await confirmBox("Убрать отметку?")) { d.marks.splice(d.marks.indexOf(m), 1); save(); drawList(); } } }, "×")),
          h("textarea", { style: "margin-top:6px;min-height:46px", placeholder: "подробнее: что видно, что слышно, что делает игрок (необязательно)", oninput: e => { m.text = e.target.value; save(); } }, m.text || "")));
      }
      if (!d.marks.length) list.append(h("div", { class: "muted" }, "Отметок нет. Встань на нужное время и нажми «Отметка здесь» (клавиша M)."));
    };
    const addMark = (t, title = "", kind = "other") => { const m = { id: uid(), t, title, kind, text: "" }; d.marks.push(m); sel = m; save(); drawList(); };
    const tapNote = h("span", { class: "muted" });
    const tapInfo = () => { tapNote.textContent = `Нажатий: ${d.taps.length}`; };
    const recBtn = h("button", { class: "btn", onclick: () => { rec = !rec; recBtn.classList.toggle("primary", rec); recBtn.textContent = rec ? "Запись пробела: ВКЛ" : "Запись пробела"; if (rec && audio.paused) toast("Пробел запустит трек, дальше каждое нажатие ляжет точкой", "ok"); } }, "Запись пробела");
    const toggle = () => { if (audio.paused) audio.play(); else audio.pause(); };

    const pick = h("input", { type: "file", accept: "audio/*,.ogg", style: "display:none", onchange: async e => {
      const f = e.target.files[0]; if (!f) return;
      if (url) URL.revokeObjectURL(url); url = URL.createObjectURL(f); audio.src = url; d.track.name = f.name; info.textContent = "Читаю трек…";
      try {
        const buf = await new (window.AudioContext || window.webkitAudioContext)().decodeAudioData(await f.arrayBuffer()), ch = buf.getChannelData(0), n = 4000, per = Math.floor(ch.length / n);
        peaks = new Float32Array(n); let top = 0;
        for (let i = 0; i < n; i++) { let p = 0; for (let j = i * per; j < (i + 1) * per; j += 8) p = Math.max(p, Math.abs(ch[j])); peaks[i] = p; top = Math.max(top, p); }
        for (let i = 0; i < n; i++) peaks[i] /= top || 1;
        d.track.duration = buf.duration; save(); info.textContent = `${f.name}: ${tlFmt(buf.duration)} (${buf.duration.toFixed(2)} с)`;
      } catch (err) { info.textContent = "Не удалось разобрать трек: " + err.message; }
    } });
    canvas.addEventListener("mousedown", e => {
      const v = view(), r = canvas.getBoundingClientRect(), x = e.clientX - r.left;
      const y = e.clientY - r.top, tap = y < 44 ? d.taps.find(t => Math.abs(xOf(t, v) - x) < 6) : undefined;
      if (tap !== undefined) {
        const i = d.taps.indexOf(tap), mv = ev => { d.taps[i] = Math.max(0, Math.min(dur(), tOf(ev.clientX - r.left, v))); };
        const up = () => { removeEventListener("mousemove", mv); removeEventListener("mouseup", up); save(); tapInfo(); };
        addEventListener("mousemove", mv); addEventListener("mouseup", up); return;
      }
      const near = d.marks.find(m => Math.abs(xOf(m.t, v) - x) < 6);
      if (near) { sel = near; const mv = ev => { near.t = Math.max(0, Math.min(dur(), tOf(ev.clientX - r.left, v))); }; const up = () => { removeEventListener("mousemove", mv); removeEventListener("mouseup", up); save(); drawList(); }; addEventListener("mousemove", mv); addEventListener("mouseup", up); }
      else audio.currentTime = Math.max(0, Math.min(dur(), tOf(x, v)));
    });
    canvas.addEventListener("dblclick", e => {
      const v = view(), r = canvas.getBoundingClientRect(), x = e.clientX - r.left, i = d.taps.findIndex(t => Math.abs(xOf(t, v) - x) < 6);
      if (i >= 0 && e.clientY - r.top < 44) { d.taps.splice(i, 1); save(); tapInfo(); }
    });
    canvas.addEventListener("wheel", e => {
      e.preventDefault(); const v = view(), r = canvas.getBoundingClientRect(), tc = tOf(e.clientX - r.left, v);
      if (e.ctrlKey || e.shiftKey) { zoom = Math.max(1, Math.min(40, zoom * (e.deltaY < 0 ? 1.25 : 0.8))); const span = dur() / zoom; scroll = Math.max(0, Math.min(dur() - span, tc - (e.clientX - r.left) / v.w * span)); }
      else scroll = Math.max(0, Math.min(dur() - v.span, v.t0 + e.deltaY / 400 * v.span));
    }, { passive: false });
    const keys = e => {
      if (/INPUT|TEXTAREA|SELECT/.test(document.activeElement?.tagName || "")) return;
      if (e.code === "Space") {
        e.preventDefault(); if (e.repeat) return;
        if (!rec) toggle();
        else if (audio.paused) audio.play();
        else { d.taps.push(Math.round(audio.currentTime * 1000) / 1000); flash = performance.now(); save(); tapInfo(); }
      }
      else if (rec && e.key === "Backspace") { d.taps.pop(); save(); tapInfo(); }
      else if (e.key.toLowerCase() === "m") addMark(Math.round((audio.currentTime || 0) * 10) / 10);
      else if (e.key === "ArrowLeft") audio.currentTime = Math.max(0, audio.currentTime - (e.shiftKey ? 5 : 1));
      else if (e.key === "ArrowRight") audio.currentTime = Math.min(dur(), audio.currentTime + (e.shiftKey ? 5 : 1));
    };
    document.addEventListener("keydown", keys); onLeave(() => document.removeEventListener("keydown", keys));

    root.append(
      h("div", { class: "card" },
        h("div", { class: "row" }, h("button", { class: "btn primary", onclick: () => pick.click() }, "Выбрать трек"), pick,
          h("button", { class: "btn", onclick: toggle }, "▶ / ❚❚  (Пробел)"), clock,
          h("button", { class: "btn", onclick: () => addMark(Math.round((audio.currentTime || 0) * 10) / 10) }, "Отметка здесь (M)"),
          h("button", { class: "btn sm", onclick: () => { zoom = 1; scroll = 0; } }, "весь трек")),
        h("div", { class: "row", style: "margin-top:8px" }, recBtn, tapNote,
          h("button", { class: "btn sm", onclick: () => copyText([...d.taps].sort((a, b) => a - b).map(t => Math.round(t * 1000)).join("\n")) }, "Скопировать (мс)"),
          h("button", { class: "btn sm danger", onclick: async () => { if (d.taps.length && await confirmBox("Стереть все " + d.taps.length + " нажатий?")) { d.taps.length = 0; save(); tapInfo(); } } }, "Стереть все")),
        info, h("div", { style: "margin-top:10px" }, canvas),
        h("div", { class: "muted small", style: "margin-top:6px" }, "Клик по волне: перемотка. Перетащи отметку, чтобы сдвинуть. Колесо: прокрутка, Ctrl+колесо: масштаб. Стрелки: ±1 с (Shift: ±5 с). В записи пробела: Backspace стирает последнее нажатие, точку можно перетащить, двойной клик по ней удаляет. Файл трека остаётся в браузере: после перезагрузки страницы выбери его снова, отметки сохранены.")),
      h("div", { class: "card" }, h("h2", {}, "Отметки"), list,
        h("div", { class: "muted small" }, "Сохраняется само в ", h("code", {}, "art/episodes/meta_line_timeline.md"), ". Каждая отметка длится до следующей.")));
    drawList(); tapInfo();
    if (d.track.name) info.textContent = `Последний трек: ${d.track.name} (${tlFmt(d.track.duration || 0)}). Выбери файл снова, чтобы слышать и видеть волну.`;
  },
});
