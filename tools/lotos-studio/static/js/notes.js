"use strict";
/* Ideas and suggestions, messages for Claude, tasks, ratings. */

const KINDS = { idea: "Идея", improve: "Улучшение проекта", suggestion: "Предложение" };
const IDEA_STATUS = { new: "новая", taken: "беру в работу", done: "сделано", rejected: "отклонена" };

MD.ideas = list => ["# Идеи, улучшения и предложения", "", "_Выгрузка из Lotos Studio. В git не попадает._", "",
  ...list.map(i => `- [${i.status === "done" ? "x" : " "}] (${KINDS[i.kind] || i.kind}, ${IDEA_STATUS[i.status] || i.status}${i.votes ? `, голосов: ${i.votes}` : ""}${i.source === "claude" ? ", от Claude" : ""}) ${i.text.replace(/\n/g, " ")}`)].join("\n");
MD.messages = list => {
  const open = list.filter(m => m.status !== "done");
  const order = { high: 0, normal: 1, low: 2 };
  return ["# Для Claude", "", "_Сообщения от автора из Lotos Studio. Читай сверху вниз: сначала срочные. Сделанное автор отмечает сам._", "",
    ...(open.length ? open.sort((a, b) => (order[a.priority] ?? 1) - (order[b.priority] ?? 1)).flatMap(m => [`## ${m.priority === "high" ? "СРОЧНО. " : ""}${fmtDate(m.created)} [${m.status === "new" ? "новое" : "прочитано"}]`, "", m.text, ...(m.attach && m.attach.length ? ["", "Вложения: " + m.attach.join(", ")] : []), ""]) : ["Нет открытых сообщений.", ""])].join("\n");
};
MD.tasks = list => ["# Дела", "", ...list.map(t => `- [${t.done ? "x" : " "}] ${t.prio >= 2 ? "(срочно) " : ""}${t.text}`)].join("\n");
MD.ratings = list => ["# Оценки автора", "", "_Чем выше, тем лучше (из 10). 0 значит «ещё не оценено»._", "", ...list.map(r => `- ${r.title} [${r.kind}]: ${r.stars ? r.stars + "/10" : "не оценено"}${r.comment ? ". " + r.comment : ""}${r.date ? " (" + r.date + ")" : ""}`)].join("\n");

/* ---------------------------------------------------------------- ideas */
registerPage("ideas", {
  title: "Идеи и предложения", icon: "bulb", group: "Работа", order: 6,
  async render(root) {
    const list = await Docs.get("ideas"), save = () => Docs.save("ideas");
    let fk = "", fs = "", q = "";
    const box = h("div", {});
    const draw = () => {
      box.innerHTML = "";
      const items = list.filter(i => (!fk || i.kind === fk) && (!fs || i.status === fs) && (!q || lc(i.text + " " + (i.tags || []).join(" ")).includes(lc(q)))).sort((a, b) => (b.votes - a.votes) || String(b.created).localeCompare(String(a.created)));
      for (const i of items) box.append(h("div", { class: "idea " + (i.status === "done" || i.status === "rejected" ? "done" : "") },
        h("div", { class: "vote" }, h("button", { class: "btn sm", onclick: () => { i.votes++; save(); draw(); } }, "▲"), h("b", {}, i.votes || 0), h("button", { class: "btn sm", onclick: () => { i.votes--; save(); draw(); } }, "▼")),
        h("div", { class: "body" }, h("div", { class: "row", style: "margin-bottom:4px" }, h("span", { class: "tag acc" }, KINDS[i.kind] || i.kind), h("span", { class: "tag " + (i.status === "done" ? "ok" : i.status === "taken" ? "info" : i.status === "rejected" ? "bad" : "") }, IDEA_STATUS[i.status]),
          i.source === "claude" ? h("span", { class: "tag warn" }, "от Claude") : null, (i.tags || []).map(t => h("span", { class: "tag" }, t)), h("span", { class: "muted small" }, fmtDate(i.created))), i.text),
        h("div", { class: "col" }, h("div", { class: "row tight" },
          Object.entries(IDEA_STATUS).filter(([k]) => k !== i.status).map(([k, v]) => h("button", { class: "btn sm", onclick: () => { i.status = k; save(); draw(); } }, v)),
          h("button", { class: "btn sm", title: "В список дел", onclick: async () => { const t = await Docs.get("tasks"); t.unshift({ id: uid(), text: i.text, done: false, prio: 1, created: nowIso() }); Docs.save("tasks"); i.status = "taken"; save(); draw(); refreshBadges(); toast("Добавлено в «Дела»", "ok"); } }, "→ дела"),
          h("button", { class: "btn sm", title: "Попросить Claude сделать", onclick: () => sendToClaude("Сделай эту идею: " + i.text) }, "→ Claude"),
          h("button", { class: "btn sm danger", onclick: async () => { if (await confirmBox("Удалить запись?")) { list.splice(list.indexOf(i), 1); save(); draw(); } } }, "×")))));
      if (!items.length) box.append(h("div", { class: "muted" }, "Ничего нет по этому фильтру."));
    };
    const kind = h("select", {}, Object.entries(KINDS).map(([k, v]) => h("option", { value: k }, v))), text = h("textarea", { placeholder: "Новая идея, улучшение или предложение…" });
    const tags = h("input", { type: "text", placeholder: "теги через запятую (необязательно)" });
    root.append(
      h("div", { class: "card" }, h("h2", {}, "Записать"), h("div", { class: "grid g2" }, h("label", { class: "f" }, h("span", {}, "Что это"), kind), h("label", { class: "f" }, h("span", {}, "Теги"), tags)), text,
        h("button", { class: "btn primary", style: "margin-top:8px", onclick: () => { const t = text.value.trim(); if (!t) return; list.unshift({ id: uid(), kind: kind.value, text: t, status: "new", votes: 0, tags: tags.value.split(",").map(x => x.trim()).filter(Boolean), source: "author", created: nowIso() }); text.value = ""; tags.value = ""; save(); draw(); } }, "Добавить")),
      h("div", { class: "toolbar" },
        h("select", { style: "max-width:220px", onchange: e => { fk = e.target.value; draw(); } }, h("option", { value: "" }, "все виды"), Object.entries(KINDS).map(([k, v]) => h("option", { value: k }, v))),
        h("select", { style: "max-width:220px", onchange: e => { fs = e.target.value; draw(); } }, h("option", { value: "" }, "все статусы"), Object.entries(IDEA_STATUS).map(([k, v]) => h("option", { value: k }, v))),
        h("input", { type: "search", placeholder: "поиск", style: "max-width:260px", oninput: e => { q = e.target.value; draw(); } })), box);
    draw();
  },
});

/* ---------------------------------------------------------------- messages for Claude */
registerPage("claude", {
  title: "Для Claude", icon: "mail", group: "Работа", order: 7,
  async render(root) {
    const list = await Docs.get("messages"), save = () => { Docs.save("messages"); refreshBadges(); };
    const box = h("div", {}), text = h("textarea", { class: "tall", placeholder: "Пиши подробно, как удобно: что сделать, что не так, что запомнить. Один раз написал здесь, в чате остаётся одна фраза." });
    const prio = h("select", {}, [["normal", "обычное"], ["high", "срочно"], ["low", "не горит"]].map(([v, t]) => h("option", { value: v }, t)));
    const draw = () => {
      box.innerHTML = "";
      for (const m of list) box.append(h("div", { class: "idea " + (m.status === "done" ? "done" : "") },
        h("div", { class: "body" }, h("div", { class: "row", style: "margin-bottom:4px" }, h("span", { class: "tag " + (m.priority === "high" ? "bad" : "acc") }, m.priority === "high" ? "срочно" : m.priority === "low" ? "не горит" : "обычное"),
          h("span", { class: "tag " + (m.status === "done" ? "ok" : m.status === "new" ? "warn" : "info") }, m.status === "done" ? "сделано" : m.status === "new" ? "новое" : "прочитано Claude"), h("span", { class: "muted small" }, fmtDate(m.created))), m.text,
          m.attach && m.attach.length ? h("div", { class: "muted small", style: "margin-top:4px" }, "Вложения: " + m.attach.join(", ")) : null),
        h("div", { class: "row tight" }, m.status !== "done" ? h("button", { class: "btn sm good", onclick: () => { m.status = "done"; save(); draw(); } }, "сделано") : h("button", { class: "btn sm", onclick: () => { m.status = "new"; save(); draw(); } }, "вернуть"),
          h("button", { class: "btn sm danger", onclick: async () => { if (await confirmBox("Удалить сообщение?")) { list.splice(list.indexOf(m), 1); save(); draw(); } } }, "×"))));
      if (!list.length) box.append(h("div", { class: "muted" }, "Пока пусто."));
    };
    root.append(h("div", { class: "card" }, h("h2", {}, "Как это работает"), h("div", { class: "muted" }, "Всё, что ты пишешь здесь, лежит в файле ", h("code", {}, "art/episodes/for_claude.md"), ". В чате достаточно одной фразы: «прочитай for_claude.md». Это экономит твои токены."),
      h("div", { style: "margin-top:8px" }, h("button", { class: "btn sm primary", onclick: () => copyText("Прочитай art/episodes/for_claude.md и сделай по списку") }, "Скопировать фразу для чата"))),
      h("div", { class: "card" }, h("h2", {}, "Новое сообщение"), text, h("div", { class: "row", style: "margin-top:8px" }, h("span", { class: "muted" }, "важность"), prio,
        h("button", { class: "btn primary", onclick: () => { const t = text.value.trim(); if (!t) return; list.unshift({ id: uid(), text: t, priority: prio.value, status: "new", created: nowIso(), attach: [] }); text.value = ""; save(); draw(); } }, "Отправить"))), box);
    draw();
  },
});

/* ---------------------------------------------------------------- tasks */
registerPage("tasks", {
  title: "Дела", icon: "check", group: "Работа", order: 8,
  async render(root) {
    const list = await Docs.get("tasks"), save = () => { Docs.save("tasks"); refreshBadges(); };
    const box = h("div", {}), inp = h("input", { type: "text", placeholder: "Что надо сделать? Enter — добавить" });
    const draw = () => {
      box.innerHTML = ""; const sorted = [...list].sort((a, b) => (a.done - b.done) || ((b.prio || 0) - (a.prio || 0)));
      for (const t of sorted) box.append(h("div", { class: "idea " + (t.done ? "done" : ""), style: "align-items:center" }, h("input", { type: "checkbox", checked: t.done, onchange: e => { t.done = e.target.checked; save(); draw(); } }),
        h("div", { class: "body" }, t.text), h("select", { style: "width:130px", onchange: e => { t.prio = Number(e.target.value); save(); draw(); } }, [[0, "не горит"], [1, "обычное"], [2, "срочно"]].map(([v, l]) => h("option", { value: v, selected: (t.prio ?? 1) === v }, l))),
        h("button", { class: "btn sm danger", onclick: () => { list.splice(list.indexOf(t), 1); save(); draw(); } }, "×")));
      if (!list.length) box.append(h("div", { class: "muted" }, "Дел нет."));
    };
    inp.addEventListener("keydown", e => { if (e.key === "Enter" && inp.value.trim()) { list.unshift({ id: uid(), text: inp.value.trim(), done: false, prio: 1, created: nowIso() }); inp.value = ""; save(); draw(); } });
    root.append(h("div", { class: "card" }, inp), box); draw();
  },
});

/* ---------------------------------------------------------------- ratings */
function starsEl(value, onSet, big = false) {
  const wrap = h("div", { class: "stars" + (big ? " big" : "") });
  const draw = v => { wrap.innerHTML = ""; for (let i = 1; i <= 10; i++) wrap.append(h("button", { class: i <= v ? "on" : "", title: i + "/10", onclick: () => { value = value === i ? 0 : i; onSet(value); draw(value); } }, "★")); };
  draw(value); return wrap;
}
registerPage("ratings", {
  title: "Оценки", icon: "star", group: "Работа", order: 9,
  async render(root) {
    const list = await Docs.get("ratings"), save = () => Docs.save("ratings");
    const box = h("div", {}), hero = h("div", { class: "hero" });
    const draw = () => {
      const rated = list.filter(r => r.stars > 0), avg = rated.length ? rated.reduce((a, r) => a + r.stars, 0) / rated.length : 0, studio = list[0];
      hero.innerHTML = ""; hero.append(h("h2", {}, studio ? studio.title : "Оценка"), h("div", { class: "muted", style: "margin-bottom:8px" }, "Поставь оценку от 1 до 10 звёзд и напиши, что хорошо, а что нет. Я читаю это и подстраиваюсь."),
        studio ? starsEl(studio.stars, v => { studio.stars = v; studio.date = new Date().toLocaleDateString("ru-RU"); save(); draw(); }, true) : null, rated.length ? h("div", { class: "muted", style: "margin-top:8px" }, `Средняя оценка всего: ${avg.toFixed(1)} из 10 (оценено ${rated.length} из ${list.length})`) : null);
      box.innerHTML = "";
      for (const r of list) box.append(h("div", { class: "idea" }, h("div", { class: "body" }, h("div", { class: "row", style: "margin-bottom:6px" }, h("b", {}, r.title), h("span", { class: "tag" }, r.kind), r.date ? h("span", { class: "muted small" }, r.date) : null),
        starsEl(r.stars, v => { r.stars = v; r.date = new Date().toLocaleDateString("ru-RU"); save(); draw(); }),
        h("textarea", { style: "margin-top:8px;min-height:54px", placeholder: "что хорошо, что плохо, что поменять", oninput: e => { r.comment = e.target.value; save(); }, value: r.comment || "" })),
        h("button", { class: "btn sm danger", onclick: async () => { if (await confirmBox("Убрать из оценок?")) { list.splice(list.indexOf(r), 1); save(); draw(); } } }, "×")));
    };
    const title = h("input", { type: "text", placeholder: "Что оценить? (сцена, функция, версия, идея…)" }), kind = h("input", { type: "text", placeholder: "вид (сцена, шейдер…)", style: "max-width:200px" });
    root.append(hero, h("div", { class: "card" }, h("div", { class: "row" }, h("div", { style: "flex:1;min-width:200px" }, title), kind, h("button", { class: "btn primary", onclick: () => { if (!title.value.trim()) return; list.push({ id: uid(), title: title.value.trim(), kind: kind.value.trim() || "другое", stars: 0, comment: "", date: "" }); title.value = ""; kind.value = ""; save(); draw(); } }, "Добавить для оценки"))), box);
    draw();
  },
});
