// Projector view: the live simulation, as large as the screen allows.
// Professor only: it needs the instructor token (from the dashboard's link,
// the browser's saved sign-in, or typed in).

import { api, el, followLiveGame, instructorToken, store } from "./api.js";
import { drawWorld, renderLegend, speciesColor } from "./world.js";

const $ = id => document.getElementById(id);
const STATUS = { setup: "Waiting to start", running: "Running", paused: "Paused", ended: "Finished" };
let game = null;
let studentUrls = [];

function render() {
  if (!game) return;
  const status = $("status");
  status.textContent = STATUS[game.status] || game.status;
  status.className = `pill ${game.status === "running" ? "live" : game.status === "ended" ? "warn" : ""}`;
  $("tick").textContent = game.status === "setup" ? "" : `tick ${game.tick}`;

  const stage = $("stage");
  drawWorld($("world"), game, stage.clientWidth - 8, stage.clientHeight - 8);
  renderLegend($("legend"), game.species);
  $("empty").classList.toggle("hidden", game.species.length > 0);
  renderOverlay();
}

function renderOverlay() {
  const overlay = $("overlay");
  if (game.status === "ended" && game.species.length) {
    const ranked = [...game.species].sort((a, b) => b.current - a.current || a.id - b.id);
    const rows = ranked.map(s => {
      const swatch = el("span", { className: "swatch" });
      swatch.style.background = speciesColor(s.id);
      return el("li", {}, swatch, el("span", { className: "name" }, s.name),
        el("span", { className: "count" }, String(s.current)));
    });
    overlay.replaceChildren(
      el("div", { className: "overlay-title" }, `Finished after ${game.tick} ticks`),
      el("ol", { className: "final" }, ...rows));
    overlay.classList.remove("hidden");
  } else if (game.status === "setup" && studentUrls.length) {
    overlay.replaceChildren(
      el("div", { className: "overlay-title" }, "Submit your program at"),
      ...studentUrls.map(url => el("div", { className: "url" }, url.replace(/^http:\/\//, ""))),
      el("div", { className: "muted" }, game.species.length
        ? `${game.species.length} program${game.species.length === 1 ? "" : "s"} loaded`
        : "Waiting for programs…"));
    overlay.classList.remove("hidden");
  } else {
    overlay.classList.add("hidden");
  }
}

function toggleFullscreen() {
  if (document.fullscreenElement) document.exitFullscreen();
  else document.documentElement.requestFullscreen().catch(() => {});
}

$("fullscreen").onclick = toggleFullscreen;
document.addEventListener("keydown", event => {
  if (event.key === "f" || event.key === "F") toggleFullscreen();
});
window.addEventListener("resize", render);

/** Shows the token form (when there is no token, or the server refused it). */
function showLogin(message) {
  $("main").classList.add("hidden");
  $("status").classList.add("hidden");
  $("login").classList.remove("hidden");
  $("login-error").textContent = message || "";
  $("login-error").classList.toggle("hidden", !message);
  $("token").focus();
}

function show(token) {
  $("login").classList.add("hidden");
  $("status").classList.remove("hidden");
  $("main").classList.remove("hidden");
  api("GET", "/api/admin/info", undefined, { Authorization: `Bearer ${token}` }).then(info => {
    studentUrls = info.studentUrls || [];
    render();
  }).catch(() => {});
  if (stopFollowing) stopFollowing();
  stopFollowing = followLiveGame(token, frame => {
    game = frame;
    render();
  }, connected => {
    if (!connected) {
      $("status").textContent = "Reconnecting…";
      $("status").className = "pill err";
    }
  }, () => {
    store.remove("bw.token");
    showLogin("That token isn't right. Check the BugsWorld window.");
  });
}

$("login-form").onsubmit = event => {
  event.preventDefault();
  const token = $("token").value.trim();
  store.set("bw.token", token);
  show(token);
};

// The dashboard's link may also be opened in a tab already showing this page.
let stopFollowing = null;
window.addEventListener("hashchange", () => {
  if (location.hash.includes("token=")) show(instructorToken());
});

const token = instructorToken();
if (token) show(token);
else showLogin();
