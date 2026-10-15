// Student page: write BL with live error checking, practice, submit.

import { api, ApiError, el, followLiveGame, store, toast } from "./api.js";
import { createEditor } from "./editor.js";
import { drawWorld, renderLegend } from "./world.js";

const STARTER = `PROGRAM MyBug IS

BEGIN
    WHILE true DO
        IF next-is-enemy THEN
            infect
        ELSE
            IF next-is-empty THEN
                move
            ELSE
                turnleft
            END IF
        END IF
    END WHILE
END MyBug
`;

const $ = id => document.getElementById(id);
let latest = null;      // last compile response
let compileTimer = 0;
let compileRun = 0;
let samples = [];

// ------------------------------------------------------------------ editor

const editor = createEditor($("editor"), {
  value: store.get("bw.source") ?? STARTER,
  label: "Your BL program",
  onChange(text) {
    store.set("bw.source", text);
    clearTimeout(compileTimer);
    compileTimer = setTimeout(compile, 300);
  },
});

async function compile() {
  const run = ++compileRun;
  try {
    const result = await api("POST", "/api/compile", { source: editor.value });
    if (run === compileRun) show(result);
  } catch (e) {
    if (run === compileRun) setStatus("err", e.message);
  }
}

function setStatus(kind, text) {
  const pill = $("status");
  pill.className = `pill ${kind}`;
  pill.textContent = text;
}

function show(result) {
  latest = result;
  editor.setDiagnostics(result.diagnostics);
  const errors = result.diagnostics.filter(d => d.severity === "error");
  const warnings = result.diagnostics.filter(d => d.severity === "warning");
  if (errors.length) {
    setStatus("err", errors.length === 1 ? "1 error" : `${errors.length} errors`);
  } else if (warnings.length) {
    setStatus("warn", `Compiles · ${warnings.length === 1 ? "1 warning" : warnings.length + " warnings"}`);
  } else {
    setStatus("ok", "✓ Compiles");
  }
  $("format").disabled = !result.ok;
  $("practice").disabled = !result.ok;

  $("problems").replaceChildren(...result.diagnostics.map(d => {
    const button = el("button", { type: "button", onclick: () => editor.reveal(d.offset, d.length) },
      el("span", { className: "where" }, `Line ${d.line}:`),
      d.message,
      d.hint ? el("span", { className: "hint" }, d.hint) : null);
    return el("li", { className: d.severity === "error" ? "err" : "warn" }, button);
  }));
}

$("format").onclick = () => {
  if (latest && latest.ok && latest.formatted !== editor.value) {
    editor.value = latest.formatted;
  }
};

// Examples ------------------------------------------------------------------

function setProgram(text, what) {
  const current = editor.value.trim();
  if (current && current !== STARTER.trim() && current !== text.trim()
      && !confirm(`Replace your program with ${what}? (Save it first if you want to keep it.)`)) {
    return false;
  }
  editor.value = text;
  return true;
}

$("examples").onchange = event => {
  const sample = samples.find(s => s.name === event.target.value);
  event.target.value = "";
  if (sample) setProgram(sample.source, `the ${sample.name} example`);
};

// Files ---------------------------------------------------------------------

$("open").onclick = () => $("file").click();
$("file").onchange = async event => {
  const file = event.target.files[0];
  event.target.value = "";
  if (file) setProgram(await file.text(), file.name);
};
$("editor").addEventListener("dragover", event => event.preventDefault());
$("editor").addEventListener("drop", async event => {
  const file = event.dataTransfer.files[0];
  if (file) {
    event.preventDefault();
    setProgram(await file.text(), file.name);
  }
});
$("save").onclick = () => {
  const name = (latest && latest.programName) || "my-bug";
  const link = el("a", {
    href: URL.createObjectURL(new Blob([editor.value], { type: "text/plain" })),
    download: `${name}.bl`,
  });
  link.click();
  setTimeout(() => URL.revokeObjectURL(link.href), 1000);
};

// ------------------------------------------------------------------ submit

$("student").value = store.get("bw.name", "");
$("class-code").value = store.get("bw.classCode", "");

$("submit-form").onsubmit = async event => {
  event.preventDefault();
  const result = $("submit-result");
  const student = $("student").value.trim();
  if (!student) {
    notice(result, "err", "Enter your name so your professor knows whose bug this is.");
    $("student").focus();
    return;
  }
  store.set("bw.name", student);
  const classCode = $("class-code").value.trim();
  store.set("bw.classCode", classCode);
  $("submit").disabled = true;
  try {
    const r = await api("POST", "/api/submissions", { student, source: editor.value },
      classCode ? { "X-Class-Code": classCode } : {});
    const time = new Date().toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
    notice(result, "ok", `Submitted ${r.programName} at ${time}. Your professor can now add it to the `
      + "class game. Changed something? Submit again; the newest one is used."
      + (r.warnings.length ? ` (It has ${r.warnings.length} warning(s); see the list on the left.)` : ""));
  } catch (e) {
    if (e instanceof ApiError && e.status === 422 && e.data) {
      show(e.data);
      notice(result, "err", "Your program has errors. Fix the ones listed on the left, then submit again.");
    } else if (e instanceof ApiError && e.status === 403) {
      $("code-field").classList.remove("hidden");
      notice(result, "err", "That class code isn't right. Ask your professor for it.");
      $("class-code").focus();
    } else {
      notice(result, "err", e.message);
    }
  } finally {
    $("submit").disabled = false;
  }
};

function notice(node, kind, text) {
  node.className = `notice ${kind}`;
  node.textContent = text;
}

// ---------------------------------------------------------------- practice

let practice = null;   // { rows, columns, species, frames }
let frameIndex = 0;
let playing = false;
let lastStep = 0;

$("practice").onclick = async () => {
  $("practice").disabled = true;
  try {
    practice = await api("POST", "/api/practice", { source: editor.value, opponent: $("opponent").value });
    $("practice-view").classList.remove("hidden");
    $("practice-hint").classList.add("hidden");
    $("scrub").max = practice.frames.length - 1;
    frameIndex = 0;
    playing = true;
    explainProblems(practice.result.species);
    drawPractice();
    requestAnimationFrame(animate);
  } catch (e) {
    if (e instanceof ApiError && e.status === 422 && e.data) show(e.data);
    toast(e.message, "err");
  } finally {
    $("practice").disabled = !(latest && latest.ok);
  }
};

function explainProblems(species) {
  const mine = species[0];
  const note = $("practice-note");
  const parts = [];
  if (mine.stuck) {
    parts.push("Some of your bugs got stuck: they were in a loop where no path does an action "
      + "(move, turn, infect or skip), so they lost their turn. Check the warnings on the left.");
  }
  if (mine.halted) {
    parts.push("Some of your bugs reached the end of the program and stopped for good. "
      + "Wrap your main statements in WHILE true DO … END WHILE.");
  }
  note.textContent = parts.join(" ");
  note.classList.toggle("hidden", parts.length === 0);
}

function countsAt(frame) {
  const counts = practice.species.map(() => 0);
  for (let i = 0; i < frame.length; i += 4) counts[frame[i]]++;
  return counts;
}

function drawPractice() {
  const frame = practice.frames[frameIndex];
  const wrap = $("practice-canvas").parentElement;
  drawWorld($("practice-canvas"), { rows: practice.rows, columns: practice.columns, bugs: frame, stride: 4, offset: 0 },
    wrap.clientWidth, 360);
  const counts = countsAt(frame);
  renderLegend($("practice-legend"),
    practice.species.map((name, id) => ({ id, name, current: counts[id] })), { details: false });
  $("scrub").value = frameIndex;
  $("tick").textContent = `tick ${frameIndex} / ${practice.frames.length - 1}`;
  $("play").textContent = playing ? "❚❚" : "▶";
  $("play").setAttribute("aria-label", playing ? "Pause" : "Play");
}

function animate(time) {
  if (!practice || !playing) return;
  const perSecond = Number($("speed").value);
  if (time - lastStep >= 1000 / perSecond) {
    lastStep = time;
    if (frameIndex < practice.frames.length - 1) {
      frameIndex++;
    } else {
      playing = false;
    }
    drawPractice();
  }
  requestAnimationFrame(animate);
}

$("play").onclick = () => {
  if (!practice) return;
  if (!playing && frameIndex >= practice.frames.length - 1) frameIndex = 0;
  playing = !playing;
  drawPractice();
  if (playing) requestAnimationFrame(animate);
};
$("scrub").oninput = event => {
  if (!practice) return;
  playing = false;
  frameIndex = Number(event.target.value);
  drawPractice();
};
window.addEventListener("resize", () => practice && drawPractice());

// ------------------------------------------------------------------ start

async function start() {
  compile();
  try {
    const info = await api("GET", "/api/info");
    $("code-field").classList.toggle("hidden", !info.requiresClassCode);
  } catch { /* the submit form will report problems */ }
  try {
    samples = await api("GET", "/api/samples");
  } catch {
    samples = [];
  }
  for (const s of samples) $("examples").append(el("option", { value: s.name }, s.name));
  const opponents = samples.map(s => el("option", { value: s.name, selected: s.name === "hunter" }, `vs ${s.name}`));
  $("opponent").replaceChildren(...opponents, el("option", { value: "none" }, "alone"));

  followLiveGame(game => {
    const live = $("live");
    if (game.status === "running") {
      live.textContent = "Class game is live: watch";
      live.className = "button small pill live";
    } else {
      live.textContent = "Watch the class game";
      live.className = "button small";
    }
  });
}

start();
