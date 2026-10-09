"use strict";
/* Shader tuner: sliders for every option of the LotosCinema pack, presets, before/after on screenshots, checks. */

const SHADER_INFO = {
  EXPOSURE: ["Экспозиция", "Общая яркость. Меньше значит темнее и кинематографичнее."],
  GRADE_CONTRAST: ["Контраст", "Разница между светлым и тёмным."],
  GRADE_SATURATION: ["Насыщенность цвета", "Меньше значит выцветший, мрачный вид."],
  SHADOW_TINT: ["Холодные тени", "Сколько сине-фиолетового в тенях."],
  SKY_MOOD: ["Настроение неба", "Небо уходит в холодный фиолет (видно только в игре)."],
  BLOOM_STRENGTH: ["Свечение", "Ореол вокруг ярких мест и ламп."],
  VIGNETTE: ["Виньетка", "Затемнение по краям кадра."],
  FILM_GRAIN: ["Зерно", "Плёночный шум."],
  CHROMA: ["Цветовое смещение", "Лёгкий разлёт цветов к краям."],
  LETTERBOX: ["Кино-полосы", "1 = чёрные полосы сверху и снизу (для записи видео)."],
  SHADOW_STRENGTH: ["Сила теней", "Тени от солнца и луны (видно только в игре)."],
  AO_STRENGTH: ["Затенение углов", "Тёмные стыки и щели (только в игре)."],
  HAZE_DENSITY: ["Дымка вдали", "Холодная дымка по расстоянию (только в игре)."],
  GODRAYS: ["Лучи света", "Объёмные лучи от солнца и луны (только в игре)."],
  BUMP_STRENGTH: ["Рельеф текстур", "Насколько пиксели блоков ловят свет (только в игре)."],
  SPECULAR_STRENGTH: ["Блеск", "Блики на металле, камне, льду (только в игре)."],
  RELIEF_LIGHT: ["Рельефный свет", "Объём граней от направленного света (только в игре)."],
};
const SHADER_GROUPS = [
  ["Кино-цвет и камера (виден в предпросмотре)", ["EXPOSURE", "GRADE_CONTRAST", "GRADE_SATURATION", "SHADOW_TINT", "SKY_MOOD", "BLOOM_STRENGTH", "VIGNETTE", "FILM_GRAIN", "CHROMA", "LETTERBOX"]],
  ["Свет и тени мира (видны только в игре)", ["SHADOW_STRENGTH", "AO_STRENGTH", "HAZE_DENSITY", "GODRAYS"]],
  ["Блоки и предметы (видны только в игре)", ["BUMP_STRENGTH", "SPECULAR_STRENGTH", "RELIEF_LIGHT"]],
];
const SHADER_PRESETS = {
  "Кино (по умолчанию)": { EXPOSURE: 0.7, GRADE_CONTRAST: 1.3, GRADE_SATURATION: 0.78, SHADOW_TINT: 0.55, SKY_MOOD: 0.6, BLOOM_STRENGTH: 0.45, VIGNETTE: 0.55, FILM_GRAIN: 0.05, CHROMA: 0.0015, LETTERBOX: 0, SHADOW_STRENGTH: 0.45, AO_STRENGTH: 0.55, HAZE_DENSITY: 0.35, GODRAYS: 0.55, BUMP_STRENGTH: 1.2, SPECULAR_STRENGTH: 1.0, RELIEF_LIGHT: 0.8 },
  "Мягкий": { EXPOSURE: 0.85, GRADE_CONTRAST: 1.1, GRADE_SATURATION: 0.9, SHADOW_TINT: 0.35, SKY_MOOD: 0.3, BLOOM_STRENGTH: 0.25, VIGNETTE: 0.3, FILM_GRAIN: 0.03, CHROMA: 0.0008, GODRAYS: 0.4, BUMP_STRENGTH: 0.6 },
  "Жёсткий и тёмный": { EXPOSURE: 0.6, GRADE_CONTRAST: 1.5, GRADE_SATURATION: 0.6, SHADOW_TINT: 0.7, SKY_MOOD: 1.0, BLOOM_STRENGTH: 0.6, VIGNETTE: 0.7, FILM_GRAIN: 0.08, CHROMA: 0.003, SHADOW_STRENGTH: 0.6, AO_STRENGTH: 0.75, HAZE_DENSITY: 0.5, GODRAYS: 0.8, BUMP_STRENGTH: 1.6, SPECULAR_STRENGTH: 1.5, RELIEF_LIGHT: 1.2 },
  "Для записи видео": { EXPOSURE: 0.7, BLOOM_STRENGTH: 0.6, VIGNETTE: 0.55, FILM_GRAIN: 0.05, LETTERBOX: 1, GODRAYS: 0.8 },
};
const userPresets = () => { try { return JSON.parse(store("ls_shader_presets") || "{}"); } catch (e) { return {}; } };

registerPage("shader", {
  title: "Шейдер", icon: "sliders", group: "Инструменты", order: 0,
  async render(root) {
    const opts = await api("/api/shader_options"), byName = Object.fromEntries(opts.map(o => [o.name, o])), loaded = {}, curv = {};
    for (const o of opts) { loaded[o.name] = o.value; curv[o.name] = o.value; }
    const con = h("div", { class: "console" }, "Здесь появится вывод."), results = h("div", { class: "pairs" }), picked = new Set(), rows = {};
    const idxOf = (o, v) => o.choices.findIndex(c => Math.abs(parseFloat(c) - parseFloat(v)) < 1e-9);
    const setValue = (name, v) => { const o = byName[name]; if (!o) return; const i = idxOf(o, v); if (i < 0) return; curv[name] = o.choices[i]; const r = rows[name]; if (r) { r.range.value = i; r.val.textContent = o.choices[i]; r.row.classList.toggle("changed", curv[name] !== loaded[name]); } };
    const usePreset = p => { for (const [k, v] of Object.entries(p)) setValue(k, v); };
    const presetBar = h("div", { class: "row" });
    const drawPresets = () => { presetBar.innerHTML = ""; for (const [n, p] of Object.entries(SHADER_PRESETS)) presetBar.append(h("button", { class: "btn sm", onclick: () => usePreset(p) }, n));
      for (const [n, p] of Object.entries(userPresets())) presetBar.append(h("button", { class: "btn sm", title: "твой пресет", onclick: () => usePreset(p) }, "★ " + n), h("button", { class: "btn sm danger", onclick: () => { const u = userPresets(); delete u[n]; store("ls_shader_presets", JSON.stringify(u)); drawPresets(); } }, "×")); };
    drawPresets();
    const groups = h("div", {});
    for (const [title, names] of SHADER_GROUPS) {
      const card = h("div", { class: "card" }, h("h2", {}, title));
      for (const name of names) {
        const o = byName[name]; if (!o) continue; const [label, desc] = SHADER_INFO[name] || [name, ""];
        const range = h("input", { type: "range", min: 0, max: o.choices.length - 1, step: 1, value: idxOf(o, o.value) }), val = h("div", { class: "val", style: "text-align:right;font-weight:600" }, o.value);
        const row = h("div", { class: "opt" }, h("div", {}, h("div", { class: "nm" }, label), h("div", { class: "ds" }, desc)), range, val);
        range.oninput = () => { curv[name] = o.choices[Number(range.value)]; val.textContent = curv[name]; row.classList.toggle("changed", curv[name] !== loaded[name]); };
        rows[name] = { row, range, val }; card.append(row);
      }
      groups.append(card);
    }
    const shotsBox = h("div", { class: "shots" }, h("span", { class: "muted" }, "Загрузка…"));
    const apply = async () => { const vals = {}; for (const k of Object.keys(curv)) vals[k] = curv[k]; const r = await post("/api/shader_options", { values: vals }); for (const k of Object.keys(curv)) loaded[k] = curv[k]; for (const n of Object.keys(rows)) rows[n].row.classList.remove("changed"); return r; };
    const withBtn = async (btn, fn) => { btn.disabled = true; try { await fn(); } catch (e) { toast("Ошибка: " + e.message, "err"); } finally { btn.disabled = false; } };
    const bPrev = h("button", { class: "btn primary" }, "Применить и показать на скриншоте"), bGame = h("button", { class: "btn" }, "Применить и положить в игру");
    bPrev.onclick = () => withBtn(bPrev, async () => { await apply(); await runJob("shader_run", { names: [...picked] }, con, results, null); });
    bGame.onclick = () => withBtn(bGame, async () => { await apply(); await runJob("shader_install", {}, con, null, null); toast("В игре: F3+R перезагрузит шейдеры", "ok"); });
    root.append(
      h("div", { class: "card" }, h("h2", {}, "Пресеты"), presetBar,
        h("div", { class: "row", style: "margin-top:10px" }, bPrev, bGame,
          h("button", { class: "btn", onclick: () => { for (const k of Object.keys(loaded)) setValue(k, loaded[k]); } }, "Сбросить несохранённое"),
          h("button", { class: "btn", onclick: async () => { const n = await promptBox("Название своего пресета"); if (!n) return; const u = userPresets(); u[n] = { ...curv }; store("ls_shader_presets", JSON.stringify(u)); drawPresets(); toast("Пресет сохранён", "ok"); } }, "Сохранить как свой пресет"))),
      h("div", { class: "card" }, h("h2", {}, "Скриншоты для проверки"), h("div", { class: "muted small", style: "margin-bottom:8px" }, "Отметь один-два скриншота (F2 в игре). Если ничего не отмечено, возьму два последних. Предпросмотр показывает только «кино-цвет и камеру»: тени, лучи и блики видны лишь в игре."), shotsBox),
      groups, h("div", { class: "card" }, h("h2", {}, "Результат"), results, h("h3", {}, "Вывод"), con),
      h("div", { class: "card" }, h("h2", {}, "Проверки без запуска игры"), h("div", { class: "row" },
        jobBtn("Проверить компиляцию", "shader_compile", {}, con, results, "btn"),
        jobBtn("Только положить пак в игру", "shader_install", {}, con, null, "btn"))));
    try {
      const list = (await api("/api/screenshots")).slice(0, 16); shotsBox.innerHTML = "";
      if (!list.length) shotsBox.append(h("span", { class: "muted" }, "Скриншотов нет."));
      for (const s of list) { const cb = h("input", { type: "checkbox" }), lab = h("label", {}, cb, h("img", { src: "/mcshots/" + encodeURIComponent(s.name), loading: "lazy" }), h("div", { class: "cap" }, `${s.time} · ${s.mb} МБ`));
        cb.onchange = () => { if (cb.checked) picked.add(s.name); else picked.delete(s.name); lab.classList.toggle("on", cb.checked); }; shotsBox.append(lab); }
    } catch (e) { shotsBox.textContent = "Не удалось получить скриншоты: " + e.message; }
  },
});

/* ---------------------------------------------------------------- screenshots gallery */
registerPage("shots", {
  title: "Скриншоты", icon: "image", group: "Инструменты", order: 1,
  async render(root) {
    const list = await api("/api/screenshots"), notes = await Docs.get("shotnotes"), save = () => Docs.save("shotnotes");
    let min = 0; const gal = h("div", { class: "gal" });
    const draw = () => { gal.innerHTML = ""; list.filter(s => (notes[s.name]?.stars || 0) >= min).forEach((s, i, arr) => { const n = notes[s.name] || {};
      gal.append(h("div", { class: "it", onclick: () => view(arr, i) }, h("img", { src: "/mcshots/" + encodeURIComponent(s.name), loading: "lazy" }), h("div", { class: "cap" }, h("div", { class: "row" }, s.time, h("span", { class: "muted" }, s.mb + " МБ"), n.stars ? h("span", { class: "tag warn" }, "★ " + n.stars) : null), n.note ? h("div", { class: "muted small" }, n.note) : null))); });
      if (!gal.children.length) gal.append(h("div", { class: "muted" }, "Нет скриншотов.")); };
    function view(arr, i) {
      const s = arr[i], n = notes[s.name] = notes[s.name] || {}, note = h("textarea", { placeholder: "заметка: что здесь хорошо или плохо", value: n.note || "" });
      note.oninput = () => { n.note = note.value; save(); };
      const m = modal({ wide: true, body: h("div", {}, h("img", { src: "/mcshots/" + encodeURIComponent(s.name), style: "width:100%;border-radius:10px" }),
        h("div", { class: "row", style: "margin:10px 0" }, h("b", {}, s.name), starsEl(n.stars || 0, v => { n.stars = v; save(); })), note,
        h("div", { class: "row", style: "margin-top:10px" },
          h("button", { class: "btn", onclick: () => { m.close(); view(arr, (i - 1 + arr.length) % arr.length); } }, "← назад"), h("button", { class: "btn", onclick: () => { m.close(); view(arr, (i + 1) % arr.length); } }, "вперёд →"),
          h("button", { class: "btn primary", onclick: () => sendToClaude(`Посмотри скриншот ${s.name} (папка screenshots в .minecraft). ${n.note || ""}`, "normal", [s.name]) }, "→ Claude: посмотри"),
          h("button", { class: "btn", onclick: () => { m.close(); store("ls_pick_shot", s.name); go("shader"); } }, "в шейдер"))), actions: [{ label: "Закрыть", onclick: () => draw() }] });
    }
    root.append(h("div", { class: "toolbar" }, h("span", { class: "muted" }, "Показывать от"), h("select", { style: "max-width:140px", onchange: e => { min = Number(e.target.value); draw(); } }, [0, 1, 3, 5, 7, 9].map(v => h("option", { value: v }, v ? "★ " + v : "всех"))),
      h("button", { class: "btn sm", onclick: () => post("/api/open", { key: "screenshots" }) }, "Открыть папку")), gal); draw();
  },
});
