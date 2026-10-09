"use strict";
/* Story (12 lines, the write-up of each line, a line x episode map) and World (heroes, places). */

const LINE_STATUS = { idea: "идея", draft: "черновик", written: "прописана", locked: "утверждена" };

MD.world = w => {
  const o = ["# Мир, герои и сюжет", "", "_Выгрузка из Lotos Studio. В git не попадает._", ""];
  if (w.synopsis && w.synopsis.trim()) o.push("## Сюжет в целом", "", w.synopsis.trim(), "");
  o.push("## Герои", "");
  for (const x of w.heroes) o.push(`### ${x.name}`, `- Роль: ${x.role || "—"}`, `- Внешность: ${x.look || "—"}`, `- Голос: ${x.voice || "—"}`, `- Заметки: ${x.notes || "—"}`, "");
  o.push("## Места", "");
  for (const x of w.places) o.push(`### ${x.name}`, `- Описание: ${x.desc || "—"}`, `- Вид: ${x.look || "—"}`, "");
  o.push("## Сюжетные линии", "");
  for (const l of w.lines) {
    if (l.secret) { o.push(`### ${l.n}. (секретная линия, автор скрыл от Claude)`, ""); continue; }
    o.push(`### ${l.n}. ${l.name} [${LINE_STATUS[l.status] || l.status}]`, "");
    if (l.summary) o.push(`Коротко: ${l.summary}`, "");
    if (l.prescription) o.push("Прописка:", "", l.prescription, "");
    for (const [ep, t] of Object.entries(l.beats || {})) if (t && t.trim()) o.push(`- Эпизод ${ep}: ${t.trim()}`);
    o.push("");
  }
  return o.join("\n");
};

/* ---------------------------------------------------------------- story */
registerPage("story", {
  title: "Сюжет", icon: "map", group: "Работа", order: 2,
  async render(root) {
    const w = await Docs.get("world"), eps = await api("/api/episodes"); const save = () => Docs.save("world");
    let tab = store("ls_story_tab") || "lines"; let sel = Number(store("ls_line")) || 0; if (sel >= w.lines.length) sel = 0;
    const syn = h("textarea", { class: "tall", placeholder: "Общий сюжет своими словами: о чём всё это, куда идёт, чем кончается (можно оставить пустым и открывать по частям).", style: "min-height:120px" });
    syn.value = w.synopsis || ""; syn.addEventListener("input", () => { w.synopsis = syn.value; save(); });
    root.append(h("div", { class: "card" }, h("h2", {}, "Сюжет в целом"), syn), h("div", { class: "tabs" },
      [["lines", "12 линий и прописка"], ["map", "Карта: линия × эпизод"]].map(([k, t]) => h("button", { class: tab === k ? "on" : "", onclick: () => { store("ls_story_tab", k); renderPage(); } }, t))));
    if (tab === "map") return renderMap(root, w, eps, save);
    const list = h("div", { class: "lane" }), form = h("div", {});
    const drawList = () => { list.innerHTML = ""; w.lines.forEach((l, i) => list.append(h("div", { class: "lineitem" + (i === sel ? " on" : ""), onclick: () => { sel = i; store("ls_line", i); drawList(); drawForm(); } },
      h("span", { class: "swatch", style: `background:${l.color}` }), h("div", { style: "flex:1;min-width:0" }, h("div", { style: "font-weight:600" }, `${l.n}. ${l.name}`), h("div", { class: "muted small" }, l.secret ? "секретная" : (l.summary || "—").slice(0, 46))),
      h("span", { class: "tag " + (l.status === "locked" ? "ok" : l.status === "written" ? "info" : "") }, LINE_STATUS[l.status])))); };
    const drawForm = () => {
      const l = w.lines[sel]; form.innerHTML = "";
      const inp = (k, ph) => { const e = h("input", { type: "text", placeholder: ph || "" }); e.value = l[k] || ""; e.addEventListener("input", () => { l[k] = e.value; save(); drawList(); }); return e; };
      const area = (k, ph, cls) => { const e = h("textarea", { class: cls || "", placeholder: ph || "" }); e.value = l[k] || ""; e.addEventListener("input", () => { l[k] = e.value; save(); }); return e; };
      const st = h("select", {}, Object.entries(LINE_STATUS).map(([k, v]) => h("option", { value: k }, v))); st.value = l.status; st.onchange = () => { l.status = st.value; save(); drawList(); };
      const col = h("input", { type: "color", value: l.color }); col.oninput = () => { l.color = col.value; save(); drawList(); };
      const secret = h("input", { type: "checkbox", checked: !!l.secret, onchange: e => { l.secret = e.target.checked; save(); drawList(); } });
      l.beats = l.beats || {};
      form.append(h("div", { class: "card" }, h("h2", {}, `Линия ${l.n}`),
        h("div", { class: "grid g2" }, h("label", { class: "f" }, h("span", {}, "Название"), inp("name", "как называется линия")), h("label", { class: "f" }, h("span", {}, "Статус"), st)),
        h("div", { class: "row", style: "margin-bottom:10px" }, h("span", { class: "muted" }, "цвет"), col, h("label", { class: "row tight", style: "margin-left:auto" }, secret, h("span", { class: "muted" }, "секретная: не выгружать для Claude"))),
        h("label", { class: "f" }, h("span", {}, "Коротко (одна-две фразы)"), area("summary", "о чём эта линия")),
        h("label", { class: "f" }, h("span", {}, "Сюжетная прописка (подробно: события, повороты, чем заканчивается)"), area("prescription", "пиши как удобно, хоть тезисами", "tall"))),
        h("div", { class: "card" }, h("h2", {}, "Что происходит по эпизодам"), eps.map(ep => h("label", { class: "f" }, h("span", {}, ep.title), (() => { const e = h("textarea", { placeholder: "что линия делает в этом эпизоде" }); e.value = l.beats[ep.id] || ""; e.addEventListener("input", () => { l.beats[ep.id] = e.value; save(); }); return e; })()))));
    };
    root.append(h("div", { class: "split" }, h("div", {}, h("div", { class: "card" }, h("h2", {}, "Линии"), list)), form)); drawList(); drawForm();
  },
});
function renderMap(root, w, eps, save) {
  const tb = h("table", {}, h("tr", {}, h("th", {}, ""), eps.map(e => h("th", {}, e.title))));
  for (const l of w.lines) {
    const tr = h("tr", {}, h("th", { style: "text-align:left" }, h("span", { class: "swatch", style: `background:${l.color};display:inline-block;margin-right:6px` }), `${l.n}. ${l.name}`));
    for (const e of eps) { const t = (l.beats || {})[e.id]; tr.append(h("td", { class: t ? "has" : "no", title: t || "пусто", onclick: async () => { const v = await promptBox(`${l.name} · ${e.title}`, t || "", true); if (v == null) return; l.beats = l.beats || {}; l.beats[e.id] = v; save(); renderPage(); } }, t ? "●" : "+")); }
    tb.append(tr);
  }
  root.append(h("div", { class: "card matrix" }, h("div", { class: "muted", style: "margin-bottom:8px" }, "● значит, что для этой линии в этом эпизоде что-то записано. Нажми на клетку, чтобы написать или поправить."), tb));
}

/* ---------------------------------------------------------------- world: heroes and places */
registerPage("world", {
  title: "Герои и места", icon: "users", group: "Работа", order: 3,
  async render(root) {
    const w = await Docs.get("world"), save = () => Docs.save("world");
    let tab = store("ls_world_tab") || "heroes"; const key = tab === "heroes" ? "heroes" : "places"; const arr = w[key];
    let sel = Math.max(0, arr.findIndex(x => x.id === store(tab === "heroes" ? "ls_hero" : "ls_place"))); const list = h("div", { class: "lane" }), form = h("div", {});
    const drawList = () => { list.innerHTML = ""; arr.forEach((x, i) => list.append(h("div", { class: "lineitem" + (i === sel ? " on" : ""), onclick: () => { sel = i; store(tab === "heroes" ? "ls_hero" : "ls_place", x.id); drawList(); drawForm(); } },
      h("span", { class: "swatch", style: `background:${x.color || "var(--accent)"}` }), h("div", { style: "flex:1;min-width:0" }, h("div", { style: "font-weight:600" }, x.name), h("div", { class: "muted small" }, (x.role || x.desc || "—").slice(0, 44)))))); };
    const drawForm = () => {
      form.innerHTML = ""; const x = arr[sel]; if (!x) { form.append(h("div", { class: "card muted" }, "Пусто. Добавь первого.")); return; }
      const inp = (k, ph) => { const e = h("input", { type: "text", placeholder: ph || "" }); e.value = x[k] || ""; e.addEventListener("input", () => { x[k] = e.value; save(); drawList(); }); return e; };
      const area = (k, ph) => { const e = h("textarea", { placeholder: ph || "" }); e.value = x[k] || ""; e.addEventListener("input", () => { x[k] = e.value; save(); }); return e; };
      const f = (l, el) => h("label", { class: "f" }, h("span", {}, l), el);
      const refsBox = h("div", { class: "refs" }), drawRefs = () => { refsBox.innerHTML = ""; (x.refs = x.refs || []).forEach((r, i) => refsBox.append(h("div", { class: "ref" }, h("a", { href: "/refs/" + r, target: "_blank" }, h("img", { src: "/refs/" + r })), h("button", { class: "x", onclick: () => { x.refs.splice(i, 1); save(); drawRefs(); } }, "×")))); }; drawRefs();
      const file = h("input", { type: "file", accept: "image/*", multiple: true, onchange: async () => { for (const fl of file.files) { try { (x.refs = x.refs || []).push(await uploadFile(fl)); } catch (e) { toast("Не загрузилось: " + e.message, "err"); } } save(); drawRefs(); } });
      const col = h("input", { type: "color", value: x.color || "#7a5cff" }); col.oninput = () => { x.color = col.value; save(); drawList(); };
      const body = tab === "heroes"
        ? [f("Имя", inp("name")), f("Роль в сюжете", inp("role", "кто это для истории")), f("Внешность", area("look", "как выглядит: рост, одежда, приметы, цвета")), f("Голос и манера", area("voice", "как говорит и двигается")), f("Заметки и тайны", area("notes", ""))]
        : [f("Название", inp("name")), f("Что это за место", area("desc", "")), f("Как выглядит (свет, цвета, материалы)", area("look", ""))];
      form.append(h("div", { class: "card" }, h("h2", {}, x.name), body, h("div", { class: "row" }, h("span", { class: "muted" }, "цвет"), col),
        h("h3", {}, "Референсы (твои картинки)"), refsBox, h("div", { style: "margin-top:8px" }, file),
        h("div", { style: "margin-top:14px" }, h("button", { class: "btn sm danger", onclick: async () => { if (!await confirmBox(`Удалить «${x.name}»? Кадры, где он выбран, сохранят текстовое имя.`)) return; arr.splice(sel, 1); sel = Math.max(0, sel - 1); save(); drawList(); drawForm(); } }, "Удалить"))));
    };
    root.append(h("div", { class: "tabs" }, [["heroes", "Герои"], ["places", "Места"]].map(([k, t]) => h("button", { class: tab === k ? "on" : "", onclick: () => { store("ls_world_tab", k); renderPage(); } }, t))),
      h("div", { class: "split" }, h("div", { class: "card" }, h("h2", {}, tab === "heroes" ? "Герои" : "Места"), list,
        h("button", { class: "btn sm primary", style: "margin-top:10px", onclick: async () => { const n = await promptBox("Имя / название"); if (!n) return; arr.push(tab === "heroes" ? { id: uid(), name: n, role: "", look: "", voice: "", notes: "", color: "#7a5cff", refs: [] } : { id: uid(), name: n, desc: "", look: "", refs: [] }); sel = arr.length - 1; save(); drawList(); drawForm(); } }, "+ добавить")), form));
    drawList(); drawForm();
  },
});
