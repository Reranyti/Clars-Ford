"use strict";
/* Diagrams: nodes and links you can drag. Exported as Mermaid text so Claude can read them. */

MD.diagrams = list => ["# Схемы", "", "_Выгрузка из Lotos Studio. В git не попадает. Формат Mermaid._", "", ...list.flatMap(d => {
  const name = n => `n_${n.id}`, q = t => String(t).replace(/"/g, "'");
  return [`## ${d.name}`, "", "```mermaid", "graph LR", ...d.nodes.map(n => `  ${name(n)}["${q(n.text)}"]`), ...d.edges.map(e => `  n_${e.from} -->${e.label ? `|${q(e.label)}|` : ""} n_${e.to}`), "```", ""];
})].join("\n");

registerPage("diagrams", {
  title: "Схемы", icon: "graph", group: "Работа", order: 5,
  async render(root) {
    const list = await Docs.get("diagrams"), save = () => Docs.save("diagrams");
    let cur = list.find(d => d.id === store("ls_diag")) || list[0], selNode = null, selEdge = -1, link = null;
    const NS = "http://www.w3.org/2000/svg", svg = document.createElementNS(NS, "svg"); svg.setAttribute("class", "dg"); svg.setAttribute("viewBox", "0 0 1200 640");
    svg.innerHTML = `<defs><marker id="ar" markerWidth="10" markerHeight="10" refX="9" refY="5" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#b79bff"/></marker></defs><g id="edges"></g><g id="nodes"></g>`;
    const insp = h("div", {}), names = h("div", { class: "lane" });
    const W = n => Math.max(110, n.text.length * 8.6 + 28), Hh = 42;
    const pt = e => { const p = svg.createSVGPoint(); p.x = e.clientX; p.y = e.clientY; return p.matrixTransform(svg.getScreenCTM().inverse()); };
    const el = (tag, attrs, text) => { const e = document.createElementNS(NS, tag); for (const [k, v] of Object.entries(attrs || {})) e.setAttribute(k, v); if (text != null) e.textContent = text; return e; };
    function draw() {
      const gE = svg.querySelector("#edges"), gN = svg.querySelector("#nodes"); gE.innerHTML = ""; gN.innerHTML = ""; if (!cur) return;
      const by = id => cur.nodes.find(n => n.id === id);
      cur.edges.forEach((e, i) => {
        const a = by(e.from), b = by(e.to); if (!a || !b) return;
        const ax = a.x + W(a) / 2, ay = a.y + Hh / 2, bx = b.x + W(b) / 2, by_ = b.y + Hh / 2;
        const g = el("g", { class: "edge" + (i === selEdge ? " sel" : ""), style: "cursor:pointer" });
        const dx = bx - ax, dy = by_ - ay, len = Math.hypot(dx, dy) || 1, ex = bx - dx / len * (Math.abs(dx / len) > Math.abs(dy / len) * 1.8 ? W(b) / 2 : Hh / 2 + 4), ey = by_ - dy / len * (Math.abs(dx / len) > Math.abs(dy / len) * 1.8 ? 0 : Hh / 2 + 4);
        g.append(el("line", { x1: ax, y1: ay, x2: ex, y2: ey, "marker-end": "url(#ar)" }), el("line", { x1: ax, y1: ay, x2: bx, y2: by_, stroke: "transparent", "stroke-width": 14 }), el("text", { x: (ax + bx) / 2, y: (ay + by_) / 2 - 6, "text-anchor": "middle" }, e.label || ""));
        g.addEventListener("click", ev => { ev.stopPropagation(); selEdge = i; selNode = null; draw(); drawInsp(); });
        gE.append(g);
      });
      for (const n of cur.nodes) {
        const g = el("g", { class: "node" + (n === selNode ? " sel" : ""), transform: `translate(${n.x},${n.y})`, style: "cursor:grab" });
        g.append(el("rect", { width: W(n), height: Hh, rx: 12, fill: rgba(n.color, .35), stroke: n.color }), el("text", { x: W(n) / 2, y: 26, "text-anchor": "middle" }, n.text));
        g.addEventListener("pointerdown", ev => {
          ev.stopPropagation();
          if (link) { if (link !== n.id) { cur.edges.push({ from: link, to: n.id, label: "" }); save(); selEdge = cur.edges.length - 1; toast("Связь создана: можешь подписать справа"); } link = null; selNode = null; draw(); drawInsp(); return; }
          selNode = n; selEdge = -1; drawInsp();
          const start = pt(ev), ox = n.x, oy = n.y; g.setPointerCapture(ev.pointerId);
          g.onpointermove = m => { const p = pt(m); n.x = Math.round(ox + p.x - start.x); n.y = Math.round(oy + p.y - start.y); g.setAttribute("transform", `translate(${n.x},${n.y})`); drawEdgesOnly(); };
          g.onpointerup = () => { g.onpointermove = null; save(); draw(); };
        });
        gN.append(g);
      }
    }
    function drawEdgesOnly() { const keep = selNode; draw(); selNode = keep; }
    svg.addEventListener("dblclick", async ev => { if (!cur) return; const p = pt(ev), t = await promptBox("Текст нового узла"); if (!t) return; const n = { id: uid(), x: Math.round(p.x - 50), y: Math.round(p.y - 20), text: t, color: "#7a5cff" }; cur.nodes.push(n); selNode = n; save(); draw(); drawInsp(); });
    svg.addEventListener("pointerdown", () => { if (link) { link = null; toast("Связь отменена"); } selNode = null; selEdge = -1; draw(); drawInsp(); });
    function drawInsp() {
      insp.innerHTML = ""; if (!cur) return;
      if (selNode) {
        const t = h("input", { type: "text", value: selNode.text }), c = h("input", { type: "color", value: selNode.color });
        t.oninput = () => { selNode.text = t.value; save(); draw(); }; c.oninput = () => { selNode.color = c.value; save(); draw(); };
        insp.append(h("h3", {}, "Узел"), h("label", { class: "f" }, h("span", {}, "Текст"), t), h("div", { class: "row" }, h("span", { class: "muted" }, "цвет"), c),
          h("div", { class: "row", style: "margin-top:10px" }, h("button", { class: "btn sm primary", onclick: () => { link = selNode.id; toast("Теперь нажми на узел, к которому ведёт связь"); } }, "Провести связь…"),
            h("button", { class: "btn sm danger", onclick: () => { cur.edges = cur.edges.filter(e => e.from !== selNode.id && e.to !== selNode.id); cur.nodes = cur.nodes.filter(n => n !== selNode); selNode = null; save(); draw(); drawInsp(); } }, "Удалить")));
      } else if (selEdge >= 0 && cur.edges[selEdge]) {
        const e = cur.edges[selEdge], t = h("input", { type: "text", value: e.label || "" }); t.oninput = () => { e.label = t.value; save(); draw(); };
        insp.append(h("h3", {}, "Связь"), h("label", { class: "f" }, h("span", {}, "Подпись"), t), h("button", { class: "btn sm danger", onclick: () => { cur.edges.splice(selEdge, 1); selEdge = -1; save(); draw(); drawInsp(); } }, "Удалить связь"));
      } else insp.append(h("div", { class: "muted" }, "Двойной щелчок по пустому месту: новый узел. Нажми на узел или связь, чтобы править. Узлы можно таскать."));
    }
    function drawNames() {
      names.innerHTML = ""; for (const d of list) names.append(h("div", { class: "lineitem" + (d === cur ? " on" : ""), onclick: () => { cur = d; store("ls_diag", d.id); selNode = null; selEdge = -1; drawNames(); draw(); drawInsp(); } }, h("div", { style: "flex:1" }, d.name), h("span", { class: "muted small" }, d.nodes.length + " узл.")));
    }
    root.append(h("div", { class: "split" },
      h("div", {}, h("div", { class: "card" }, h("h2", {}, "Схемы"), names,
        h("div", { class: "row", style: "margin-top:10px" }, h("button", { class: "btn sm primary", onclick: async () => { const n = await promptBox("Название схемы"); if (!n) return; const d = { id: uid(), name: n, nodes: [], edges: [] }; list.push(d); cur = d; save(); drawNames(); draw(); drawInsp(); } }, "+ схема"),
          h("button", { class: "btn sm", onclick: async () => { const n = await promptBox("Новое название", cur.name); if (n) { cur.name = n; save(); drawNames(); } } }, "переименовать"),
          h("button", { class: "btn sm danger", onclick: async () => { if (list.length < 2 || !await confirmBox(`Удалить схему «${cur.name}»?`)) return; list.splice(list.indexOf(cur), 1); cur = list[0]; save(); drawNames(); draw(); drawInsp(); } }, "удалить"))),
        h("div", { class: "card" }, insp),
        h("div", { class: "card" }, h("h2", {}, "Экспорт"), h("div", { class: "row" },
          h("button", { class: "btn sm", onclick: () => copyText(MD.diagrams([cur])) }, "Скопировать Mermaid"),
          h("button", { class: "btn sm", onclick: () => download(cur.name + ".svg", new XMLSerializer().serializeToString(svg), "image/svg+xml") }, "Скачать SVG")))),
      h("div", { class: "card" }, svg)));
    drawNames(); draw(); drawInsp();
  },
});
