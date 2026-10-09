"use strict";
/* Tools: mod panel, environments (Windows Sandbox, WSL, project sandboxes), diagnostics, logs, status. */

MD.wincheck = list => ["# Механики только для Windows: проверка", "", ...list.map(x => `- [${x.status === "ok" ? "x" : x.status === "bad" ? "!" : " "}] ${x.text}${x.note ? " — " + x.note : ""}`)].join("\n");

/* ---------------------------------------------------------------- mod panel */
registerPage("panel", {
  title: "Панель мода", icon: "terminal", group: "Инструменты", order: 2,
  async render(root) {
    const con = h("div", { class: "console" }, "Здесь появится вывод.");
    const msg = h("input", { type: "text", placeholder: "сообщение коммита (по-человечески, без упоминаний ИИ)" });
    const scopes = ["src", "shaderpack", "art", "tools"], chosen = new Set(scopes), push = h("input", { type: "checkbox" });
    const commit = h("button", { class: "btn primary" }, "Закоммитить");
    commit.onclick = async () => {
      if (!msg.value.trim()) { toast("Напиши сообщение коммита", "err"); return; }
      if (push.checked && !await confirmBox("Репозиторий ПУБЛИЧНЫЙ: после отправки это увидят все. Отправить на GitHub?", "Отправить")) return;
      runJob("git_commit", { message: msg.value, scope: [...chosen], push: push.checked }, con, null, commit);
    };
    const pushBtn = h("button", { class: "btn" }, "Отправить на GitHub (push)");
    pushBtn.onclick = async () => { if (await confirmBox("Репозиторий ПУБЛИЧНЫЙ: все записанные коммиты станут видны всем. Отправить?", "Отправить")) runJob("git_push", {}, con, null, pushBtn); };
    const folders = [["project", "Проект"], ["mods", "mods"], ["screenshots", "Скриншоты"], ["logs", "Логи игры"], ["shaderpacks", "Шейдер-паки"], ["art", "art"], ["episodes", "Заметки (art/episodes)"], ["studio", "Данные Studio"]];
    const modsBox = h("div", {}, h("span", { class: "muted" }, "Загрузка…")), actBox = h("div", {});
    // the REAL game is touched only on purpose, after a confirmation
    const realBtn = (label, kind) => { const b = h("button", { class: "btn" }, label); b.onclick = async () => { if (await confirmBox("Это твоя НАСТОЯЩАЯ игра (папка mods). Закрой Minecraft. Продолжить?", "Да, в настоящую")) runJob(kind, {}, con, null, b); }; return b; };
    root.append(
      h("div", { class: "grid g2" },
        h("div", {}, h("div", { class: "card" }, h("h2", {}, "Сборка мода"),
          h("div", { class: "toolbar" }, jobBtn("Собрать и поставить в тестовую игру", "build_game", {}, con, null, "btn primary"), jobBtn("Только поставить в тестовую игру", "game_install", {}, con, null), jobBtn("Только собрать", "build", {}, con, null)),
          h("div", { class: "muted small" }, "Тестовая игра: отдельная папка D:\\mc-sandbox со своими модами и мирами. Сборка идёт минуты."),
          h("h3", {}, "В настоящую игру (осторожно)"), h("div", { class: "toolbar" }, realBtn("Положить jar в настоящие mods", "copy_jar"), realBtn("Собрать и положить в настоящие mods", "build_copy")),
          h("div", { class: "muted small" }, "Один раз в лаунчере: Установки → Новая → Forge 1.20.1 → «Папка игры» D:\\mc-sandbox. Играй этим профилем для проверок: миры и удаление мира не заденут настоящие.")),
          h("div", { class: "card" }, h("h2", {}, "Git"), h("div", { class: "toolbar" }, jobBtn("Что изменилось", "git_status", {}, con, null), pushBtn), msg,
            h("div", { class: "row", style: "margin:8px 0" }, h("span", { class: "muted" }, "Добавить:"), scopes.map(sc => h("label", { class: "row tight" }, h("input", { type: "checkbox", checked: true, onchange: e => { if (e.target.checked) chosen.add(sc); else chosen.delete(sc); } }), sc)), h("label", { class: "row tight", style: "margin-left:10px" }, push, "сразу отправить")), commit,
            h("div", { class: "muted small", style: "margin-top:8px" }, "Заметки о сюжете (art/episodes) в git не попадают: они в .gitignore.")),
          h("div", { class: "card" }, h("h2", {}, "Диск и копии"), h("div", { class: "toolbar" }, jobBtn("Свободное место", "disk", {}, con, null), jobBtn("Резервная копия данных Studio", "backup", {}, con, null)))),
        h("div", {}, h("div", { class: "card" }, h("h2", {}, "Открыть папку"), h("div", { class: "row" }, folders.map(([k, t]) => h("button", { class: "btn sm", onclick: () => post("/api/open", { key: k }).catch(e => toast(e.message, "err")) }, t)))),
          h("div", { class: "card" }, h("h2", {}, "Моды в сборке"), modsBox), h("div", { class: "card" }, h("h2", {}, "Последние действия"), actBox))),
      h("div", { class: "card" }, h("h2", {}, "Вывод"), con));
    api("/api/mods").then(m => { modsBox.innerHTML = ""; modsBox.append(h("div", { class: "muted small", style: "margin-bottom:6px" }, `${m.mods.length} файлов, ${m.total_mb} МБ`));
      for (const [b, v] of Object.entries(m.duplicates)) modsBox.append(h("div", { class: "alert warn" }, `Возможный дубль «${b}»: ${v.join(", ")}`));
      const t = h("table", { class: "t" }, m.mods.slice().sort((a, b) => b.mb - a.mb).map(x => h("tr", {}, h("td", {}, x.name), h("td", { class: "muted" }, x.mb + " МБ"))));
      modsBox.append(h("div", { style: "max-height:260px;overflow:auto" }, t)); }).catch(e => { modsBox.textContent = e.message; });
    Docs.get("activity").then(a => { actBox.innerHTML = ""; for (const x of a.slice(-10).reverse()) actBox.append(h("div", { class: "row small", style: "margin:3px 0" }, h("span", { class: "tag " + (x.status === "ok" ? "ok" : "bad") }, x.status === "ok" ? "ok" : "ошибка"), h("span", { class: "muted" }, fmtDate(x.time)), x.title, h("span", { class: "muted" }, x.seconds + " с"))); if (!a.length) actBox.append(h("div", { class: "muted" }, "Пока ничего не запускалось.")); });
  },
});

/* ---------------------------------------------------------------- environments */
registerPage("envs", {
  title: "Среды", icon: "grid", group: "Инструменты", order: 3,
  async render(root) {
    const env = await api("/api/envs"), wc = await Docs.get("wincheck"), saveWc = () => Docs.save("wincheck");
    const con = h("div", { class: "console" }, "Здесь появится вывод.");
    const mods = h("input", { type: "checkbox" }), net = h("input", { type: "checkbox" }), mem = h("select", {}, [2048, 3072, 4096].map(v => h("option", { value: v, selected: v === 3072 }, v + " МБ")));
    const xmlBox = h("pre", { class: "console", style: "display:none" });
    const sbReq = dry => ({ share_mods: mods.checked, network: net.checked, mem_mb: Number(mem.value), dry_run: dry });
    const wslCfg = "[wsl2]\nmemory=2GB\nprocessors=2\nswap=0\n";
    // Windows-only checklist
    const wcBox = h("div", {}), wcIn = h("input", { type: "text", placeholder: "механика, которую надо проверить именно на Windows (Enter — добавить)" });
    const drawWc = () => { wcBox.innerHTML = ""; for (const x of wc) wcBox.append(h("div", { class: "idea" }, h("div", { class: "body" }, h("div", { class: "row" }, h("span", { class: "tag " + (x.status === "ok" ? "ok" : x.status === "bad" ? "bad" : "") }, x.status === "ok" ? "работает" : x.status === "bad" ? "сломано" : "не проверено"), x.text),
      h("input", { type: "text", style: "margin-top:6px", placeholder: "что увидел", value: x.note || "", oninput: e => { x.note = e.target.value; saveWc(); } })),
      h("div", { class: "row tight" }, h("button", { class: "btn sm good", onclick: () => { x.status = "ok"; saveWc(); drawWc(); } }, "✔"), h("button", { class: "btn sm danger", onclick: () => { x.status = "bad"; saveWc(); drawWc(); } }, "✖"),
        h("button", { class: "btn sm", onclick: () => sendToClaude(`Механика только для Windows: «${x.text}». Статус: ${x.status}. ${x.note || ""}`) }, "→ Claude"), h("button", { class: "btn sm", onclick: () => { wc.splice(wc.indexOf(x), 1); saveWc(); drawWc(); } }, "×")))); if (!wc.length) wcBox.append(h("div", { class: "muted" }, "Пока пусто. Впиши механики, которые работают только на Windows (например, файловые диалоги, удаление мира).")); };
    wcIn.addEventListener("keydown", e => { if (e.key === "Enter" && wcIn.value.trim()) { wc.push({ id: uid(), text: wcIn.value.trim(), status: "todo", note: "" }); wcIn.value = ""; saveWc(); drawWc(); } });
    // project sandboxes
    const sbBox = h("div", {}), nameIn = h("input", { type: "text", placeholder: "имя новой песочницы, например test1", style: "max-width:260px" });
    const sbAct = (kind, name, btn) => runJob(kind, { name }, con, null, btn);
    for (const s of env.sandboxes) { const b = h("div", { class: "idea" }, h("div", { class: "body" }, h("b", {}, s.name), h("div", { class: "muted small" }, s.path + " · " + s.head)),
      h("div", { class: "row tight" }, ...[["sandbox_status", "что изменилось"], ["sandbox_compile", "проверить шейдер"], ["sandbox_build", "собрать"]].map(([k, t]) => { const bt = h("button", { class: "btn sm" }, t); bt.onclick = () => sbAct(k, s.name, bt); return bt; }),
        h("button", { class: "btn sm good", onclick: async () => { if (await confirmBox(`Перенести изменения из «${s.name}» в основной проект? Они появятся как рабочие правки, не как коммит.`)) sbAct("sandbox_apply", s.name); } }, "применить в проект"),
        h("button", { class: "btn sm danger", onclick: async () => { if (await confirmBox(`Выбросить песочницу «${s.name}»? Её несохранённые изменения пропадут (основной проект не затронут).`, "Выбросить")) { await sbAct("sandbox_remove", s.name); renderPage(); } } }, "выбросить"))); sbBox.append(b); }
    if (!env.sandboxes.length) sbBox.append(h("div", { class: "muted" }, "Песочниц нет."));
    root.append(
      h("div", { class: "grid g3" },
        h("div", { class: "card" }, h("h2", {}, "Память"), h("div", { class: "stat" }, h("b", {}, env.ram_total + " ГБ"), h("span", {}, `свободно сейчас ${env.ram_free} ГБ`)), h("div", { class: "muted small", style: "margin-top:8px" }, env.ram_total < 10 ? "Памяти мало: игру и виртуальные среды вместе держать не получится." : "Памяти хватает на лёгкую виртуальную среду.")),
        h("div", { class: "card" }, h("h2", {}, "Windows Sandbox"), h("div", { class: "stat" }, h("b", { class: env.sandbox_exe ? "" : "" }, env.sandbox_exe ? "есть" : "нет"), h("span", {}, "чистая одноразовая Windows")), h("div", { class: "muted small", style: "margin-top:8px" }, "Для проверки механик, которые работают только на Windows.")),
        h("div", { class: "card" }, h("h2", {}, "Linux (WSL)"), h("div", { class: "stat" }, h("b", {}, env.wsl.ready ? "готов" : "не установлен"), h("span", {}, env.wsl.ready ? env.wsl.distros.join(", ") : "для скриптов и управления")))),
      h("div", { class: "grid g2" },
        h("div", {}, h("div", { class: "card" }, h("h2", {}, "Чистая Windows (Sandbox)"),
          h("div", { class: "muted", style: "margin-bottom:8px" }, "Откроется отдельное окно с чистой Windows. Твой проект виден там только для чтения. После закрытия всё стирается. Игру в нём надо ставить заново (с твоим интернетом долго), поэтому лучше для проверки установки, файлов, диалогов и других системных механик."),
          h("div", { class: "row" }, h("label", { class: "row tight" }, mods, "показать папку mods"), h("label", { class: "row tight" }, net, "включить интернет"), h("span", { class: "muted" }, "память"), mem),
          h("div", { class: "row", style: "margin-top:10px" }, h("button", { class: "btn", onclick: async () => { try { const r = await post("/api/sandbox_win", sbReq(true)); xmlBox.style.display = "block"; xmlBox.textContent = r.xml; } catch (e) { toast(e.message, "err"); } } }, "Показать настройки"),
            h("button", { class: "btn primary", disabled: !env.sandbox_exe, onclick: async () => { if (!await confirmBox("Открыть чистую Windows? Закрой Minecraft и тяжёлые программы: памяти немного.", "Открыть")) return; try { await post("/api/sandbox_win", sbReq(false)); toast("Windows Sandbox запускается…", "ok"); } catch (e) { toast(e.message, "err"); } } }, "Запустить")), xmlBox),
          h("div", { class: "card" }, h("h2", {}, "Linux (WSL): как подготовить"), env.wsl.ready ? h("div", { class: "alert ok" }, "WSL уже работает: " + env.wsl.distros.join(", ")) : h("div", { class: "muted" }, "Установка делается тобой (нужны права администратора и перезагрузка). Когда скачаешь, дай мне знать, я помогу настроить."),
            h("h3", {}, "Лимит памяти (чтобы игра не тормозила)"), h("pre", { class: "console", style: "min-height:0" }, wslCfg), h("div", { class: "muted small" }, "Сохрани как C:\\Users\\PC\\.wslconfig, потом в PowerShell: wsl --shutdown"),
            h("button", { class: "btn sm", style: "margin-top:8px", onclick: () => copyText(wslCfg) }, "Скопировать"))),
        h("div", {}, h("div", { class: "card" }, h("h2", {}, "Песочницы проекта"), h("div", { class: "muted small", style: "margin-bottom:8px" }, "Отдельная копия проекта (git worktree). Можно экспериментировать с шейдером и сборкой, не трогая основные файлы, а потом применить или выбросить."),
          h("div", { class: "row", style: "margin-bottom:10px" }, nameIn, h("button", { class: "btn primary", onclick: async () => { if (!nameIn.value.trim()) return; await runJob("sandbox_create", { name: nameIn.value.trim().toLowerCase() }, con, null, null); renderPage(); } }, "Создать")), sbBox),
          h("div", { class: "card" }, h("h2", {}, "Механики только для Windows"), h("div", { class: "row", style: "margin-bottom:8px" }, wcIn), wcBox))),
      h("div", { class: "card" }, h("h2", {}, "Вывод"), con));
    drawWc();
  },
});

/* ---------------------------------------------------------------- diagnostics */
registerPage("diag", {
  title: "Диагностика", icon: "pulse", group: "Инструменты", order: 4,
  async render(root) {
    const box = h("div", {});
    const run = async () => {
      box.innerHTML = ""; box.append(h("div", { class: "muted" }, "Смотрю логи…"));
      try {
        const d = await api("/api/diagnose"); box.innerHTML = "";
        box.append(h("div", { class: "muted small", style: "margin-bottom:10px" }, "Проверено: " + d.checked.join(", ")));
        if (!d.findings.length) box.append(h("div", { class: "alert ok" }, "Явных проблем в логах не найдено."));
        for (const f of d.findings) box.append(h("div", { class: "alert " + f.level }, h("div", { style: "flex:1" }, h("b", {}, f.title), h("div", {}, f.hint), h("div", { class: "console", style: "min-height:0;margin-top:6px" }, `${f.source}: ${f.evidence}`)),
          h("button", { class: "btn sm", onclick: () => sendToClaude(`Диагностика: ${f.title}\n${f.source}: ${f.evidence}\nПодсказка программы: ${f.hint}`, "high") }, "→ Claude")));
      } catch (e) { box.innerHTML = ""; box.append(h("div", { class: "alert bad" }, "Ошибка: " + e.message)); }
    };
    root.append(h("div", { class: "toolbar" }, h("button", { class: "btn primary", onclick: run }, "Перепроверить"), h("span", { class: "muted" }, "Программа читает логи игры, вылеты и лаунчер и объясняет частые проблемы.")), box); run();
  },
});

/* ---------------------------------------------------------------- logs */
let logTimer = null;
registerPage("logs", {
  title: "Логи", icon: "file", group: "Инструменты", order: 5,
  async render(root) {
    onLeave(() => { clearInterval(logTimer); logTimer = null; });
    let src = store("ls_logsrc") || "latest", crashFile = "", sel = new Set(), cur = [];
    const lvlOf = ln => /\/(ERROR|FATAL)\]|Exception|\bERROR\b|Caused by/.test(ln) ? "e" : /\/WARN\]|\bWARN\b/.test(ln) ? "w" : /\/INFO\]|\bINFO\b/.test(ln) ? "i" : "o";
    const levels = { e: true, w: true, i: true, o: true }, out = h("div", { class: "console logs", style: "max-height:62vh" }), info = h("span", { class: "muted" }), sum = h("div", {});
    const sourceSel = h("select", { style: "max-width:220px", onchange: () => { src = sourceSel.value; store("ls_logsrc", src); renderPage(); } }, [["latest", "Игра: latest.log"], ["debug", "Игра: debug.log"], ["crash", "Вылеты"], ["launcher", "Лаунчер"], ["git", "История git"], ["activity", "Действия Studio"]].map(([v, t]) => h("option", { value: v }, t)));
    sourceSel.value = src;
    const filter = h("input", { type: "text", placeholder: "фильтр (regex), например shader|Lotos", style: "max-width:300px", oninput: () => draw() });
    const nlines = h("select", { style: "max-width:120px", onchange: () => load() }, [400, 1500, 5000].map(v => h("option", { value: v }, v + " строк")));
    const auto = h("input", { type: "checkbox", onchange: () => { clearInterval(logTimer); logTimer = null; if (auto.checked) logTimer = setInterval(load, 3000); } }), wrap = h("input", { type: "checkbox", checked: true, onchange: () => out.classList.toggle("nowrap", !wrap.checked) });
    const lvlBtns = [["e", "ошибки"], ["w", "предупр."], ["i", "инфо"], ["o", "прочее"]].map(([k, t]) => h("button", { class: "btn sm on", onclick: e => { levels[k] = !levels[k]; e.target.classList.toggle("on", levels[k]); draw(); } }, t));
    function draw() {
      out.innerHTML = ""; let re = null; try { re = filter.value ? new RegExp(filter.value, "i") : null; } catch (e) { /* bad regex: show all */ }
      const lines = []; cur.forEach((ln, i) => { const lv = lvlOf(ln); if (!levels[lv] || (re && !re.test(ln))) return; lines.push([i, ln, lv]); });
      for (const [i, ln, lv] of lines) { const d = h("div", { class: (lv === "e" ? "e" : lv === "w" ? "w" : "") + (sel.has(i) ? " sel" : ""), onclick: () => { if (sel.has(i)) sel.delete(i); else sel.add(i); d.classList.toggle("sel"); } }, ln); out.append(d); }
      info.textContent = `Показано ${lines.length} из ${cur.length}. Нажми на строки, чтобы выбрать (выбрано ${sel.size}).`;
      // summary of repeated errors
      const cnt = {}; let ne = 0, nw = 0; for (const ln of cur) { const lv = lvlOf(ln); if (lv === "e") { ne++; const k = ln.replace(/^\[[^\]]*\]\s*/, "").replace(/\[[^\]]*\]:?\s*/, "").replace(/\d+/g, "#").slice(0, 130); cnt[k] = (cnt[k] || 0) + 1; } else if (lv === "w") nw++; }
      sum.innerHTML = ""; sum.append(h("div", { class: "row" }, h("span", { class: "tag bad" }, "ошибок " + ne), h("span", { class: "tag warn" }, "предупреждений " + nw)));
      for (const [k, v] of Object.entries(cnt).sort((a, b) => b[1] - a[1]).slice(0, 6)) sum.append(h("div", { class: "small", style: "margin-top:4px" }, h("span", { class: "tag" }, "×" + v), " " + k));
      out.scrollTop = out.scrollHeight;
    }
    async function load() {
      try {
        if (src === "git") { const g = await api("/api/gitlog"); cur = g.map(x => `${x.hash}  ${x.when}  ${x.msg}`); info.textContent = ""; draw(); return; }
        if (src === "activity") { const a = await Docs.get("activity"); cur = a.slice().reverse().flatMap(x => [`${fmtDate(x.time)}  [${x.status}]  ${x.title}  (${x.seconds} с)`, ...(x.tail || []).map(t => "    " + t)]); draw(); return; }
        let r;
        if (src === "crash") { const list = await api("/api/crashes"); if (!list.length) { cur = ["Отчётов о вылете нет."]; draw(); return; } if (!crashFile) crashFile = list[0].name;
          r = await api(`/api/log?name=crashfile&file=${encodeURIComponent(crashFile)}&lines=${nlines.value}`); crashSel.innerHTML = ""; list.forEach(c => crashSel.append(h("option", { value: c.name, selected: c.name === crashFile }, `${c.time} · ${c.name} · ${c.kb} КБ`))); }
        else r = await api(`/api/log?name=${src}&lines=${nlines.value}`);
        cur = r.text.split("\n"); sel.clear(); draw(); info.textContent = `${r.path} · ${r.mtime || ""}`;
      } catch (e) { cur = ["Ошибка: " + e.message]; draw(); }
    }
    const crashSel = h("select", { style: "max-width:380px;display:" + (src === "crash" ? "block" : "none"), onchange: () => { crashFile = crashSel.value; load(); } });
    const pick = () => { const chosen = sel.size ? [...sel].sort((a, b) => a - b).map(i => cur[i]) : cur.filter(ln => lvlOf(ln) === "e").slice(-40); return chosen.join("\n").slice(0, 6000); };
    root.append(h("div", { class: "card" }, h("div", { class: "toolbar" }, sourceSel, crashSel, filter, nlines, h("button", { class: "btn sm", onclick: load }, "обновить"), h("label", { class: "row tight" }, auto, "каждые 3 с"), h("label", { class: "row tight" }, wrap, "перенос")),
      h("div", { class: "toolbar" }, lvlBtns, h("div", { class: "grow" }), h("button", { class: "btn sm", onclick: () => copyText(pick()) }, "Копировать выбранное"),
        h("button", { class: "btn sm primary", onclick: () => { const t = pick(); if (t) sendToClaude(`Логи (${src}${crashFile ? ": " + crashFile : ""}), выбранные строки или последние ошибки:\n\n${t}`, "high"); } }, "→ Claude")), h("div", { style: "margin:6px 0" }, info), out),
      h("div", { class: "card" }, h("h2", {}, "Сводка"), sum));
    load();
  },
});

/* ---------------------------------------------------------------- status and settings */
function applyAccent(c) {
  if (!c) return; const [r, g, b] = hex2rgb(c), st = document.documentElement.style;
  st.setProperty("--accent", c); st.setProperty("--glow", `rgba(${r},${g},${b},.35)`); st.setProperty("--accent2", `rgb(${Math.min(255, r + 70)},${Math.min(255, g + 70)},${Math.min(255, b + 70)})`);
}
registerPage("status", {
  title: "Состояние", icon: "gear", group: "Инструменты", order: 6,
  async render(root) {
    const st = await api("/api/status"), c = st.config, con = h("div", { class: "console" }, "Здесь появится вывод.");
    const names = { project: "Папка проекта", gradlew: "gradlew.bat", minecraft: "Папка .minecraft", mods_dir: "Папка mods", java: "Java 17 (для проверок)", lwjgl: "LWJGL (видеокарта для проверок)", shaderpack: "Шейдер-пак в проекте", classes: "Собранные классы мода" };
    const acc = h("input", { type: "color", value: store("ls_accent") || "#7a5cff", oninput: () => { applyAccent(acc.value); store("ls_accent", acc.value); } });
    root.append(
      h("div", { class: "grid g2" },
        h("div", { class: "card" }, h("h2", {}, "Что найдено"), h("div", { class: "kv" }, Object.entries(st.checks).map(([k, v]) => [h("div", {}, names[k] || k), h("div", { class: v ? "ok" : "bad" }, v ? "✔ найдено" : "✖ не найдено")]))),
        h("div", { class: "card" }, h("h2", {}, "Пути"), h("div", { class: "kv" }, ["project", "minecraft", "gradle_home", "tmp", "java_home", "jar_name", "port"].map(k => [h("div", { class: "muted" }, k), h("div", {}, String(c[k]))])),
          h("p", { class: "muted small" }, "Чтобы поменять, создай tools/lotos-studio/data/config.json с нужными ключами и перезапусти программу."))),
      h("div", { class: "card" }, h("h2", {}, "Вид и справка"), h("div", { class: "row" }, h("span", { class: "muted" }, "цвет акцента"), acc, h("button", { class: "btn sm", onclick: () => { store("ls_accent", "#7a5cff"); applyAccent("#7a5cff"); acc.value = "#7a5cff"; } }, "сбросить"), h("button", { class: "btn sm", onclick: shortcutsHelp }, "Горячие клавиши"),
        jobBtn("Резервная копия данных", "backup", {}, con, null, "btn sm"))), h("div", { class: "card" }, h("h2", {}, "Вывод"), con));
  },
});
