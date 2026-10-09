"use strict";
/* Wiki: pages in light markdown, [[links]], backlinks, tags, search. */

MD.wiki = pages => ["# Вики мода", "", "_Выгрузка из Lotos Studio. В git не попадает._", "", ...pages.flatMap(p => [`## ${p.title}`, p.tags && p.tags.length ? `Теги: ${p.tags.join(", ")}` : "", "", p.body, "", "---", ""])].join("\n");

function mdLite(src, titles, onLink) {
  const root = h("div", { class: "wiki-body" }); let ul = null;
  const inline = text => {
    const frag = document.createDocumentFragment(); const re = /\[\[([^\]]+)\]\]|\*\*([^*]+)\*\*|_([^_]+)_|`([^`]+)`/g; let last = 0, m;
    while ((m = re.exec(text))) {
      frag.append(text.slice(last, m.index));
      if (m[1]) { const t = m[1].trim(), ok = titles.has(lc(t)); frag.append(h("a", { class: "wl" + (ok ? "" : " miss"), title: ok ? "" : "страницы ещё нет: нажми, чтобы создать", onclick: () => onLink(t, ok) }, t)); }
      else if (m[2]) frag.append(h("b", {}, m[2])); else if (m[3]) frag.append(h("i", {}, m[3])); else frag.append(h("code", {}, m[4]));
      last = re.lastIndex;
    }
    frag.append(text.slice(last)); return frag;
  };
  for (const raw of src.split("\n")) {
    const line = raw.trimEnd(); let m;
    if ((m = /^(#{1,3})\s+(.*)$/.exec(line))) { ul = null; root.append(h("h" + m[1].length, {}, inline(m[2]))); }
    else if ((m = /^[-*]\s+(.*)$/.exec(line))) { if (!ul) { ul = h("ul", {}); root.append(ul); } ul.append(h("li", {}, inline(m[1]))); }
    else if (/^---+$/.test(line)) { ul = null; root.append(h("hr")); }
    else if (!line.trim()) ul = null;
    else { ul = null; root.append(h("p", { style: "margin:6px 0" }, inline(line))); }
  }
  return root;
}

registerPage("wiki", {
  title: "Вики", icon: "book", group: "Работа", order: 4,
  async render(root) {
    const pages = await Docs.get("wiki"), save = () => { Docs.save("wiki"); };
    let cur = pages.find(p => p.id === store("ls_wiki")) || pages[0], editing = false, q = "", tagF = "";
    const list = h("div", { class: "lane" }), view = h("div", {});
    const titles = () => new Set(pages.map(p => lc(p.title)));
    const open = p => { cur = p; editing = false; store("ls_wiki", p.id); drawList(); drawView(); };
    const createPage = async title => { const p = { id: uid(), title, tags: [], body: `# ${title}\n\n`, updated: nowIso() }; pages.push(p); save(); cur = p; editing = true; drawList(); drawView(); };
    const onLink = async (t, ok) => { const p = pages.find(x => lc(x.title) === lc(t)); if (p) open(p); else if (await confirmBox(`Создать страницу «${t}»?`)) createPage(t); };
    const alltags = () => [...new Set(pages.flatMap(p => p.tags || []))].sort();
    function drawList() {
      list.innerHTML = "";
      const items = pages.filter(p => (!tagF || (p.tags || []).includes(tagF)) && (!q || lc(p.title + " " + p.body).includes(lc(q)))).sort((a, b) => a.title.localeCompare(b.title, "ru"));
      for (const p of items) list.append(h("div", { class: "lineitem" + (cur && p.id === cur.id ? " on" : ""), onclick: () => open(p) }, h("div", { style: "flex:1;min-width:0" }, h("div", { style: "font-weight:600" }, p.title), h("div", { class: "muted small" }, (p.tags || []).join(", ") || "без тегов"))));
      if (!items.length) list.append(h("div", { class: "muted" }, "Ничего не найдено."));
    }
    function drawView() {
      view.innerHTML = ""; if (!cur) { view.append(h("div", { class: "card muted" }, "Нет страниц.")); return; }
      const back = pages.filter(p => p.id !== cur.id && new RegExp("\\[\\[\\s*" + cur.title.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "\\s*\\]\\]", "i").test(p.body));
      if (editing) {
        const t = h("input", { type: "text", value: cur.title }), tg = h("input", { type: "text", value: (cur.tags || []).join(", "), placeholder: "теги через запятую" }), b = h("textarea", { class: "tall", style: "min-height:420px" });
        b.value = cur.body;
        view.append(h("div", { class: "card" }, h("label", { class: "f" }, h("span", {}, "Название"), t), h("label", { class: "f" }, h("span", {}, "Теги"), tg), h("label", { class: "f" }, h("span", {}, "Текст (# заголовок, - список, **жирный**, _курсив_, [[ссылка]])"), b),
          h("div", { class: "row" }, h("button", { class: "btn primary", onclick: () => { cur.title = t.value.trim() || cur.title; cur.tags = tg.value.split(",").map(x => x.trim()).filter(Boolean); cur.body = b.value; cur.updated = nowIso(); save(); editing = false; drawList(); drawView(); } }, "Сохранить"), h("button", { class: "btn", onclick: () => { editing = false; drawView(); } }, "Отмена"))));
      } else {
        view.append(h("div", { class: "card" }, h("div", { class: "row", style: "margin-bottom:8px" }, h("span", { class: "muted small" }, "обновлено " + fmtDate(cur.updated)), (cur.tags || []).map(x => h("span", { class: "tag acc" }, x)),
          h("div", { class: "grow" }), h("button", { class: "btn sm", onclick: () => { editing = true; drawView(); } }, "Править"), h("button", { class: "btn sm", onclick: () => sendToClaude(`Вики-страница «${cur.title}»: прочитай art/episodes/wiki.md и учти.`) }, "→ Claude"),
          h("button", { class: "btn sm danger", onclick: async () => { if (!await confirmBox(`Удалить страницу «${cur.title}»?`)) return; pages.splice(pages.indexOf(cur), 1); cur = pages[0]; save(); drawList(); drawView(); } }, "Удалить")),
          mdLite(cur.body, titles(), onLink),
          back.length ? h("div", { style: "margin-top:16px" }, h("h3", {}, "Ссылаются сюда"), h("div", { class: "row" }, back.map(p => h("a", { class: "chip", onclick: () => open(p) }, p.title)))) : null));
      }
    }
    const search = h("input", { type: "search", placeholder: "поиск по вики", oninput: () => { q = search.value; drawList(); } });
    const tagSel = h("select", { onchange: () => { tagF = tagSel.value; drawList(); } }, h("option", { value: "" }, "все теги"), alltags().map(t => h("option", { value: t }, t)));
    root.append(h("div", { class: "split" }, h("div", { class: "card" }, search, h("div", { style: "height:6px" }), tagSel, h("div", { style: "height:8px" }), list,
      h("button", { class: "btn sm primary", style: "margin-top:10px", onclick: async () => { const t = await promptBox("Название новой страницы"); if (t) createPage(t.trim()); } }, "+ страница")), view));
    drawList(); drawView();
  },
});
