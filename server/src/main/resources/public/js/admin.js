// Professor dashboard: submissions, the class game's controls, settings, results.

import { api, ApiError, el, followLiveGame, store, timeAgo, toast } from "./api.js";
import { highlight } from "./bl.js";
import { drawWorld, renderLegend, speciesColor } from "./world.js";

const $ = id => document.getElementById(id);
let token = "";
let game = null;
let submissions = [];
let following = false;
let pollTimer = 0;

// ------------------------------------------------------------------- auth

async function admin(method, path, body) {
  try {
    return await api(method, path, body, { Authorization: `Bearer ${token}` });
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) showLogin("Your session ended. Sign in again.");
    throw e;
  }
}

/** Returns the token from an /admin#token=... link, removing it from the address bar. */
function takeTokenFromAddress() {
  // The server opens /admin#token=... so the professor never has to type it.
  const match = location.hash.match(/token=([^&]+)/);
  if (!match) return null;
  history.replaceState(null, "", location.pathname);
  return decodeURIComponent(match[1]);
}

// The link may also be pasted into a tab that already shows the dashboard.
window.addEventListener("hashchange", () => {
  const linked = takeTokenFromAddress();
  if (linked) signIn(linked);
});

function showLogin(message) {
  clearInterval(pollTimer);
  $("dashboard").classList.add("hidden");
  $("share").classList.add("hidden");
  $("logout").classList.add("hidden");
  $("login").classList.remove("hidden");
  const error = $("login-error");
  error.textContent = message || "";
  error.classList.toggle("hidden", !message);
  $("token").focus();
}

async function signIn(candidate) {
  token = candidate.trim();
  try {
    await api("GET", "/api/admin/check", undefined, { Authorization: `Bearer ${token}` });
  } catch (e) {
    showLogin(e.status === 401 ? "That token isn't right. Check the BugsWorld window." : e.message);
    return;
  }
  store.set("bw.token", token);
  $("login").classList.add("hidden");
  $("dashboard").classList.remove("hidden");
  $("logout").classList.remove("hidden");
  loadInfo();
  loadSubmissions();
  clearInterval(pollTimer);
  pollTimer = setInterval(loadSubmissions, 3000);
  if (!following) {
    following = true;
    followLiveGame(frame => { game = frame; renderGame(); },
      connected => { if (!connected) setStatus("err", "Reconnecting…"); });
  }
}

$("login-form").onsubmit = event => {
  event.preventDefault();
  signIn($("token").value);
};
$("logout").onclick = () => {
  store.remove("bw.token");
  token = "";
  showLogin();
};

async function loadInfo() {
  const info = await admin("GET", "/api/admin/info");
  const url = info.studentUrls[0] || location.origin;
  $("student-url").textContent = url.replace(/^http:\/\//, "");
  $("student-url").title = info.studentUrls.join("\n");
  $("class-code").textContent = info.classCode ? `· class code ${info.classCode}` : "";
  $("share").classList.remove("hidden");
  $("copy-url").onclick = async () => {
    try {
      await navigator.clipboard.writeText(url);
      toast("Address copied");
    } catch {
      toast(url);
    }
  };
}

// ------------------------------------------------------------ submissions

async function loadSubmissions() {
  try {
    submissions = await admin("GET", "/api/admin/submissions");
    renderSubmissions();
  } catch (e) {
    if (e.status !== 401) console.warn(e);
  }
}

function newestPerStudent(list) {
  const seen = new Set();
  return list.filter(s => {
    const key = s.student.trim().toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function inGame(submissionId) {
  return !!game && game.species.some(s => s.submissionId === submissionId);
}

function renderSubmissions() {
  const query = $("search").value.trim().toLowerCase();
  let list = $("latest-only").checked ? newestPerStudent(submissions) : submissions;
  if (query) {
    list = list.filter(s => s.student.toLowerCase().includes(query)
      || s.programName.toLowerCase().includes(query));
  }
  const students = new Set(submissions.map(s => s.student.trim().toLowerCase())).size;
  $("sub-count").textContent = submissions.length ? `· ${students} student${students === 1 ? "" : "s"}` : "";
  $("no-subs").classList.toggle("hidden", submissions.length > 0);
  const canAdd = game && game.status === "setup";
  $("add-all").disabled = !canAdd || submissions.length === 0;
  $("submissions").replaceChildren(...list.map(s => {
    const added = inGame(s.id);
    return el("li", {},
      el("div", { className: "who" },
        el("strong", { title: s.student }, s.student),
        el("span", { className: "muted small", title: s.programName }, `${s.programName} · ${timeAgo(s.createdAt)}`)),
      el("button", { className: "small", onclick: () => viewSource(s) }, "View"),
      el("button", {
        className: "small primary",
        disabled: !canAdd || added,
        title: added ? "Already in the game" : canAdd ? "Add to the game" : "Start a new game to add species",
        onclick: () => addSpecies(s.id),
      }, added ? "Added" : "Add"));
  }));
}

$("search").oninput = renderSubmissions;
$("latest-only").onchange = renderSubmissions;

async function addSpecies(submissionId) {
  try {
    game = await admin("POST", "/api/admin/game/species", { submissionId });
    renderGame();
    return true;
  } catch (e) {
    toast(e.message, "err");
    return false;
  }
}

$("add-all").onclick = async () => {
  const pending = newestPerStudent(submissions).filter(s => !inGame(s.id));
  if (!pending.length) {
    toast("Everyone's newest bug is already in the game");
    return;
  }
  $("add-all").disabled = true;
  let added = 0;
  for (const s of pending) {
    if (!(await addSpecies(s.id))) break;
    added++;
  }
  toast(`Added ${added} bug${added === 1 ? "" : "s"}`);
  renderSubmissions();
};

const dialog = $("source-dialog");
let shown = null;

async function viewSource(summary) {
  try {
    shown = await admin("GET", `/api/admin/submissions/${summary.id}`);
  } catch (e) {
    toast(e.message, "err");
    return;
  }
  $("source-title").textContent = `${shown.student} · ${shown.programName}`;
  $("source-code").innerHTML = highlight(shown.source);
  $("source-add").disabled = !(game && game.status === "setup") || inGame(shown.id);
  dialog.showModal();
}

$("source-close").onclick = () => dialog.close();
$("source-add").onclick = async () => {
  if (await addSpecies(shown.id)) dialog.close();
};
$("source-delete").onclick = async () => {
  if (!confirm(`Delete ${shown.student}'s ${shown.programName}? This can't be undone.`)) return;
  try {
    await admin("DELETE", `/api/admin/submissions/${shown.id}`);
    dialog.close();
    loadSubmissions();
  } catch (e) {
    toast(e.message, "err");
  }
};

// ------------------------------------------------------------------- game

const STATUS = { setup: "Setting up", running: "Running", paused: "Paused", ended: "Ended" };

function setStatus(kind, text) {
  $("game-status").className = `pill ${kind}`;
  $("game-status").textContent = text;
}

function renderGame() {
  if (!game) return;
  const s = game.status;
  setStatus(s === "running" ? "live" : s === "ended" ? "warn" : "", STATUS[s] || s);
  $("game-tick").textContent = s === "setup" ? "" : `tick ${game.tick}`
    + (game.settings.maxTicks ? ` of ${game.settings.maxTicks}` : "");
  $("game-seed").textContent = `seed ${game.settings.seed} · ${game.rows}×${game.columns}`;

  const hasSpecies = game.species.length > 0;
  const run = $("run");
  run.textContent = s === "running" ? "❚❚ Pause" : s === "paused" ? "▶ Resume" : "▶ Start";
  run.disabled = s === "ended" || !hasSpecies;
  $("step").disabled = s === "running" || s === "ended" || !hasSpecies;
  $("reset").disabled = !hasSpecies || s === "setup";
  $("end").disabled = s === "setup" || s === "ended";
  if (document.activeElement !== $("speed")) {
    $("speed").value = game.ticksPerSecond;
    $("speed-label").textContent = game.ticksPerSecond;
  }

  $("next-step").innerHTML = nextStep(s, hasSpecies);

  const stage = $("stage");
  drawWorld($("world"), game, stage.clientWidth - 6, Math.min(640, window.innerHeight * 0.62));
  renderLegend($("legend"), game.species);
  $("no-players").classList.toggle("hidden", hasSpecies);
  addRemoveButtons();
  renderSubmissions();

  if (s === "ended" && lastStatus !== "ended") loadResults();
  lastStatus = s;
}
let lastStatus = null;

/** One line telling the professor what they can do next (static HTML only). */
function nextStep(status, hasSpecies) {
  switch (status) {
    case "setup":
      return hasSpecies
        ? "Press <b>Start</b> (or <kbd>Space</kbd>) when everyone is ready. Show the class the projector view."
        : "Add submissions from the list on the left, then press <b>Start</b>.";
    case "running":
      return "<kbd>Space</kbd> pauses. The speed slider works while the game runs.";
    case "paused":
      return "Paused. <kbd>→</kbd> steps one tick at a time; <b>Resume</b> or <kbd>Space</kbd> continues.";
    case "ended":
      return "Game over. <b>Replay</b> runs the same game again, or open <b>New game settings</b> to change the world.";
    default:
      return "";
  }
}

/** In setup, each species in the legend gets a remove button. */
function addRemoveButtons() {
  if (game.status !== "setup") return;
  const items = $("legend").children;
  const ranked = [...game.species].sort((a, b) => b.current - a.current || a.id - b.id);
  ranked.forEach((species, i) => {
    const button = el("button", {
      className: "small danger remove",
      title: `Remove ${species.name}`,
      onclick: async () => {
        try {
          game = await admin("DELETE", `/api/admin/game/species/${species.id}`);
          renderGame();
        } catch (e) {
          toast(e.message, "err");
        }
      },
    }, "Remove");
    items[i].querySelector(".count").replaceWith(button);
  });
}

async function command(path) {
  try {
    game = await admin("POST", `/api/admin/game/${path}`);
    renderGame();
  } catch (e) {
    toast(e.message, "err");
  }
}

$("run").onclick = () => command(game && game.status === "running" ? "pause" : "start");
$("step").onclick = () => command("step");
$("reset").onclick = () => {
  if (game.status !== "ended" && !confirm("Replay this game from the start? Same seed and species, so it plays out identically.")) return;
  command("reset");
};
$("end").onclick = () => {
  if (confirm("End this game now? Its result will be saved.")) command("end");
};

let speedTimer = 0;
$("speed").oninput = event => {
  $("speed-label").textContent = event.target.value;
  clearTimeout(speedTimer);
  speedTimer = setTimeout(async () => {
    try {
      game = await admin("PUT", "/api/admin/game/speed", { ticksPerSecond: Number(event.target.value) });
      renderGame();
    } catch (e) {
      toast(e.message, "err");
    }
  }, 150);
};

document.addEventListener("keydown", event => {
  const target = event.target;
  const typing = target instanceof Element && target.closest("input, textarea, select, dialog");
  if (typing || !game || $("dashboard").classList.contains("hidden")) return;
  if (event.key === " ") {
    event.preventDefault();
    if (!$("run").disabled) $("run").click();
  } else if (event.key === "ArrowRight") {
    event.preventDefault();
    if (!$("step").disabled) $("step").click();
  }
});
window.addEventListener("resize", renderGame);

// ---------------------------------------------------------------- settings

function numberOrNull(id) {
  const value = $(id).value.trim();
  return value === "" ? null : Number(value);
}

$("settings").onsubmit = async event => {
  event.preventDefault();
  if (game && (game.status === "running" || game.status === "paused")
      && !confirm("End the current game and start a new one?")) return;
  const keep = game ? game.species.filter(s => s.submissionId != null).map(s => s.submissionId) : [];
  const settings = {
    rows: numberOrNull("rows"),
    columns: numberOrNull("columns"),
    bugsPerSpecies: numberOrNull("bugs"),
    maxTicks: numberOrNull("max-ticks") || 0,
    seed: numberOrNull("seed"),
    endWhenOneSpeciesLeft: $("one-left").checked,
    infectedBugsActSameTick: $("same-tick").checked,
    ticksPerSecond: Number($("speed").value),
  };
  try {
    game = await admin("POST", "/api/admin/game", settings);
    for (const id of keep) {
      if (!(await addSpecies(id))) break;
    }
    renderGame();
    toast("New game ready");
  } catch (e) {
    toast(e.message, "err");
  }
};

// ----------------------------------------------------------------- results

async function loadResults() {
  let results;
  try {
    results = await admin("GET", "/api/admin/results");
  } catch {
    return;
  }
  $("results").replaceChildren(...results.map(r => {
    const g = r.game;
    const ranked = [...g.species].sort((a, b) => b.current - a.current);
    const reason = { TICK_LIMIT: "tick limit", ONE_SPECIES_LEFT: "one species left", MANUAL: "ended by you" }[g.endReason] || "";
    return el("li", {},
      el("div", {}, el("strong", {}, ranked.length ? `${ranked[0].name} won` : "No species"),
        el("span", { className: "muted small" },
          ` · ${new Date(r.finishedAt).toLocaleString([], { dateStyle: "short", timeStyle: "short" })}`
          + ` · ${g.tick} ticks (${reason}) · seed ${g.settings.seed}`)),
      el("div", { className: "standings" }, ...ranked.map(s => {
        const swatch = el("span", { className: "swatch" });
        swatch.style.background = speciesColor(s.id);
        return el("span", {}, swatch, ` ${s.name} ${s.current}`);
      })));
  }));
  if (!results.length) $("results").replaceChildren(el("li", { className: "muted" }, "No finished games yet."));
}
$("results-box").addEventListener("toggle", () => { if ($("results-box").open) loadResults(); });

// -------------------------------------------------------------------- start

const initial = takeTokenFromAddress() || store.get("bw.token");
if (initial) signIn(initial);
else showLogin();
