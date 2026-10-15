// Projector view: the live class game, as large as the screen allows.

import { api, el, followLiveGame } from "./api.js";
import { drawWorld, renderLegend, speciesColor } from "./world.js";

const $ = id => document.getElementById(id);
const STATUS = { setup: "Getting ready", running: "Live", paused: "Paused", ended: "Game over" };
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
    const best = Math.max(...game.species.map(s => s.current));
    const winners = game.species.filter(s => s.current === best);
    const names = winners.map(w => {
      const swatch = el("span", { className: "swatch" });
      swatch.style.background = speciesColor(w.id);
      return el("span", { className: "winner" }, swatch, w.name);
    });
    overlay.replaceChildren(
      el("div", { className: "overlay-title" }, winners.length > 1 ? "It's a tie!" : "Winner"),
      ...names,
      el("div", { className: "muted" }, `${best} bugs after ${game.tick} ticks`));
    overlay.classList.remove("hidden");
  } else if (game.status === "setup" && studentUrls.length) {
    overlay.replaceChildren(
      el("div", { className: "overlay-title" }, "Submit your bug at"),
      ...studentUrls.map(url => el("div", { className: "url" }, url.replace(/^http:\/\//, ""))),
      el("div", { className: "muted" }, game.species.length
        ? `${game.species.length} species ready`
        : "Waiting for species…"));
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

api("GET", "/api/info").then(info => {
  studentUrls = info.studentUrls || [];
  render();
}).catch(() => {});

followLiveGame(frame => {
  game = frame;
  render();
}, connected => {
  if (!connected) {
    $("status").textContent = "Reconnecting…";
    $("status").className = "pill err";
  }
});
