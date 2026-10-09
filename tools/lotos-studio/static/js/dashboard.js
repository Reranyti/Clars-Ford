"use strict";
/* Dashboard: one look at everything. */

function ring(pct, color = "var(--ok)", size = 86, label = "") {
  const r = 34, c = 2 * Math.PI * r, s = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  s.setAttribute("viewBox", "0 0 86 86"); s.setAttribute("width", size); s.setAttribute("height", size);
  s.innerHTML = `<circle cx="43" cy="43" r="${r}" fill="none" stroke="var(--panel3)" stroke-width="9"/>
    <circle cx="43" cy="43" r="${r}" fill="none" stroke="${color}" stroke-width="9" stroke-linecap="round" stroke-dasharray="${c * pct / 100} ${c}" transform="rotate(-90 43 43)"/>
    <text x="43" y="48" text-anchor="middle" fill="#fff" font-size="17" font-weight="700">${label || Math.round(pct) + "%"}</text>`;
  return s;
}

registerPage("dashboard", {
  title: "Главная", icon: "home", group: "Работа", order: 0,
  async render(root) {
    root.append(h("p", { class: "muted" }, "Загрузка…"));
    let d;
    try { d = await api("/api/dashboard"); } catch (e) { root.innerHTML = ""; root.append(h("div", { class: "card" }, "Не удалось получить данные: " + e.message)); return; }
    const ratings = await Docs.get("ratings"), rated = ratings.filter(r => r.stars > 0);
    root.innerHTML = "";

    // alerts
    const alerts = [];
    for (const k of d.disks) if (k.free_gb < 5) alerts.push({ lvl: k.free_gb < 3 ? "bad" : "warn", text: `На диске ${k.letter}: свободно только ${k.free_gb} ГБ. ${k.letter === "C" ? "Лаунчер и сборка падают, когда меньше 3 ГБ." : ""}` });
    if (d.crash) alerts.push({ lvl: "warn", text: `Есть отчёт о вылете (${d.crash.time}). Открой «Диагностика», там сказано, что это значит.`, go: "diag" });
    if (d.build.jar && d.build.in_mods && !d.build.same) alerts.push({ lvl: "warn", text: "Собранный мод и мод в папке mods отличаются. Нажми «Положить jar в mods» в «Панели мода».", go: "panel" });
    if (!d.build.in_mods) alerts.push({ lvl: "warn", text: "Мода нет в папке mods.", go: "panel" });
    if (d.git.dirty > 0) alerts.push({ lvl: "ok", text: `Есть ${d.git.dirty} изменённых файлов, не записанных в git.`, go: "panel" });
    if (!alerts.length) alerts.push({ lvl: "ok", text: "Всё спокойно: тревог нет." });

    root.append(h("div", { class: "hero" }, h("h2", {}, "Мастерская Lotos"),
      h("div", { class: "muted" }, "Всё о моде в одном месте. Ctrl+K — поиск и команды, Ctrl+J — быстро записать мысль."),
      h("div", { class: "row", style: "margin-top:12px" },
        h("button", { class: "btn primary", onclick: () => go("episode") }, icon("film"), "Открыть эпизод"),
        h("button", { class: "btn", onclick: quickCapture }, icon("plus"), "Быстрая запись"),
        h("button", { class: "btn", onclick: () => go("shader") }, icon("sliders"), "Настроить шейдер"),
        h("button", { class: "btn", onclick: () => go("panel") }, icon("terminal"), "Собрать мод"))));
    for (const a of alerts) root.append(h("div", { class: "alert " + a.lvl }, h("div", { style: "flex:1" }, a.text), a.go ? h("button", { class: "btn sm", onclick: () => go(a.go) }, "открыть") : null));

    const cards = h("div", { class: "grid g3", style: "margin-top:14px" });
    // episodes
    const epCard = h("div", { class: "card" }, h("h2", {}, "Эпизоды"));
    for (const e of d.episodes) {
      const ok = e.status.ok || 0, pct = e.shots ? ok / e.shots * 100 : 0, tot = Math.max(e.shots, 1);
      epCard.append(h("div", { class: "row", style: "margin:10px 0;cursor:pointer;flex-wrap:nowrap", onclick: () => { store("ls_ep", e.id); go("episode"); } },
        ring(pct), h("div", { style: "flex:1;min-width:0" }, h("b", {}, e.title), h("div", { class: "muted small" }, `${e.shots} кадров · ${e.duration.toFixed(0)} с`),
          h("div", { class: "bar", style: "margin-top:6px" },
            h("i", { style: `width:${(e.status.ok || 0) / tot * 100}%;background:var(--ok)` }), h("i", { style: `width:${(e.status.ask || 0) / tot * 100}%;background:var(--warn)` }),
            h("i", { style: `width:${(e.status.redo || 0) / tot * 100}%;background:var(--bad)` })),
          h("div", { class: "muted small" }, `✔ ${e.status.ok || 0}  ? ${e.status.ask || 0}  ✖ ${e.status.redo || 0}  ✎ ${e.status.draft || 0}`))));
    }
    cards.append(epCard);
    // mod state
    const gitCard = h("div", { class: "card" }, h("h2", {}, "Мод и git"),
      h("div", { class: "kv" },
        h("span", { class: "muted" }, "Ветка"), h("span", {}, d.git.branch.replace(/^## /, "")),
        h("span", { class: "muted" }, "Изменено"), h("span", {}, `${d.git.dirty} (новых файлов: ${d.git.untracked})`),
        h("span", { class: "muted" }, "Последний коммит"), h("span", {}, d.git.last.hash ? `${d.git.last.hash} · ${d.git.last.when}` : "—"),
        h("span", { class: "muted" }, "Сборка"), h("span", {}, d.build.jar ? "собрана " + d.build.built : "нет файла"),
        h("span", { class: "muted" }, "В папке mods"), h("span", { class: d.build.in_mods && d.build.same ? "ok" : "" }, d.build.in_mods ? (d.build.same ? "✔ та же версия" : "есть, но другая") : "нет")),
      h("div", { class: "muted small", style: "margin-top:8px" }, d.git.last.msg || ""));
    cards.append(gitCard);
    // disks and log
    const dk = h("div", { class: "card" }, h("h2", {}, "Диски и логи"));
    for (const k of d.disks) dk.append(h("div", { style: "margin-bottom:10px" }, h("div", { class: "row" }, h("b", {}, k.letter + ":"), h("span", { class: "muted" }, `свободно ${k.free_gb} из ${k.total_gb} ГБ`)),
      h("div", { class: "bar" }, h("i", { style: `width:${100 - k.free_pct}%;background:${k.free_gb < 3 ? "var(--bad)" : k.free_gb < 6 ? "var(--warn)" : "var(--accent)"}` }))));
    dk.append(h("div", { class: "kv" }, h("span", { class: "muted" }, "Ошибок в логе игры"), h("span", { class: d.log.errors ? "bad" : "ok" }, d.log.errors + (d.log.time ? "  (лог от " + d.log.time + ")" : "")),
      h("span", { class: "muted" }, "Последний вылет"), h("span", {}, d.crash ? d.crash.time : "нет")));
    cards.append(dk);
    root.append(cards);

    const row2 = h("div", { class: "grid g3" });
    const todo = h("div", { class: "card" }, h("h2", {}, "Дальше", h("span", { class: "tag acc" }, d.counts.tasks_open_n)));
    if (!d.counts.tasks_open.length) todo.append(h("div", { class: "muted" }, "Дел нет. Запиши первое: Ctrl+J."));
    for (const t of d.counts.tasks_open) todo.append(h("div", { class: "row", style: "margin:4px 0" }, h("span", { class: "tag " + (t.prio >= 2 ? "bad" : "") }, t.prio >= 2 ? "срочно" : "дело"), t.text));
    todo.append(h("div", { style: "margin-top:8px" }, h("button", { class: "btn sm", onclick: () => go("tasks") }, "все дела")));
    row2.append(todo);
    const inbox = h("div", { class: "card" }, h("h2", {}, "Входящие"),
      h("div", { class: "grid g2" },
        h("div", { class: "stat" }, h("b", {}, d.counts.ideas), h("span", {}, "идей и предложений")),
        h("div", { class: "stat" }, h("b", {}, d.counts.messages_new), h("span", {}, "новых сообщений Claude")),
        h("div", { class: "stat" }, h("b", {}, d.counts.screenshots), h("span", {}, "скриншотов")),
        h("div", { class: "stat" }, h("b", {}, d.counts.mods), h("span", {}, "модов в сборке"))));
    row2.append(inbox);
    const rate = h("div", { class: "card" }, h("h2", {}, "Твои оценки"));
    if (rated.length) {
      const avg = rated.reduce((a, r) => a + r.stars, 0) / rated.length;
      rate.append(h("div", { class: "row", style: "flex-wrap:nowrap" }, ring(avg * 10, "var(--warn)", 86, avg.toFixed(1)), h("div", {}, h("b", {}, "Средняя оценка из 10"), h("div", { class: "muted small" }, `Оценено: ${rated.length} из ${ratings.length}`))));
      for (const r of rated.slice(0, 4)) rate.append(h("div", { class: "row small", style: "margin-top:4px" }, h("span", { class: "tag warn" }, r.stars + "/10"), r.title));
    } else rate.append(h("div", { class: "muted" }, "Пока нет оценок. Оцени Lotos Studio, шейдер и сцены: это помогает мне понять, что получилось."));
    rate.append(h("div", { style: "margin-top:10px" }, h("button", { class: "btn sm", onclick: () => go("ratings") }, "поставить оценки")));
    row2.append(rate);
    root.append(row2);
    refreshBadges();
  },
});
