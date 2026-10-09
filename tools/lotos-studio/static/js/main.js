"use strict";
/* Start-up, global shortcuts. */

(async function boot() {
  $("#burger").append(icon("menu"));
  $("#burger").onclick = () => $("#side").classList.toggle("open");
  $("#hint").onclick = openPalette; $("#kp").onclick = openPalette; $("#qc").onclick = quickCapture;
  applyAccent(store("ls_accent"));
  const want = location.hash.slice(1) || store("ls_page") || "dashboard";
  S.page = PAGES[want] ? want : "dashboard";
  document.addEventListener("keydown", e => {
    const typing = /INPUT|TEXTAREA|SELECT/.test(document.activeElement?.tagName || "");
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") { e.preventDefault(); openPalette(); }
    else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "j") { e.preventDefault(); quickCapture(); }
    else if (!typing && e.key === "?") { shortcutsHelp(); }
  });
  document.addEventListener("visibilitychange", () => { if (document.hidden) Docs.flushAll(); });
  window.addEventListener("beforeunload", () => { navigator.sendBeacon && Object.keys(Docs.timers).forEach(n => { if (Docs.timers[n] && Docs.cache[n]) navigator.sendBeacon("/api/doc?name=" + n, new Blob([JSON.stringify({ data: Docs.cache[n], md: MD[n] ? MD[n](Docs.cache[n]) : undefined })], { type: "application/json" })); }); });
  await renderPage();
  refreshBadges();
})();
