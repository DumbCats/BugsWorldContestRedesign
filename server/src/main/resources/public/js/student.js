// Student page: upload a .bl file, see whether it compiles, submit it.

import { api, ApiError, el, store } from "./api.js";
import { renderCode, revealLine } from "./codeview.js";

const MAX_LENGTH = 100_000; // same limit as the server
const $ = id => document.getElementById(id);
let source = null;
let result = null;

// ------------------------------------------------------------------- file

async function load(file) {
  if (!file) return;
  $("submit-result").classList.add("hidden");
  $("file-name").textContent = file.name;
  $("checked").classList.remove("hidden");
  if (file.size > MAX_LENGTH) {
    showProblem(`That file is too large (${Math.round(file.size / 1000)} KB). A BL program should be a small text file.`);
    return;
  }
  source = await file.text();
  try {
    show(await api("POST", "/api/compile", { source }));
  } catch (e) {
    showProblem(e.message);
  }
}

function showProblem(message) {
  result = null;
  setStatus("err", "Can't check this file");
  $("code").replaceChildren();
  $("problems").replaceChildren(el("li", { className: "err" }, el("button", { type: "button" }, message)));
  $("submit").disabled = true;
}

function setStatus(kind, text) {
  $("status").className = `pill ${kind}`;
  $("status").textContent = text;
}

function show(r) {
  result = r;
  const errors = r.diagnostics.filter(d => d.severity === "error").length;
  const warnings = r.diagnostics.length - errors;
  if (errors) {
    setStatus("err", errors === 1 ? "1 error" : `${errors} errors`);
  } else {
    setStatus(warnings ? "warn" : "ok",
      `✓ Compiles${warnings ? ` · ${warnings} warning${warnings === 1 ? "" : "s"}` : ""}`);
  }
  renderCode($("code"), source, r.diagnostics);
  $("problems").replaceChildren(...r.diagnostics.map(d => el("li", { className: d.severity === "error" ? "err" : "warn" },
    el("button", { type: "button", onclick: () => revealLine($("code"), d.line) },
      el("span", { className: "where" }, `Line ${d.line}:`),
      d.message,
      d.hint ? el("span", { className: "hint" }, d.hint) : null))));
  $("submit").disabled = !r.ok;
  $("submit").textContent = r.ok ? `Submit ${r.programName}` : "Fix the errors, then upload the file again";
}

$("file").onchange = event => {
  load(event.target.files[0]);
  event.target.value = ""; // choosing the same file again re-checks it
};
const drop = $("drop");
drop.addEventListener("dragover", event => {
  event.preventDefault();
  drop.classList.add("over");
});
drop.addEventListener("dragleave", () => drop.classList.remove("over"));
drop.addEventListener("drop", event => {
  event.preventDefault();
  drop.classList.remove("over");
  load(event.dataTransfer.files[0]);
});

// ----------------------------------------------------------------- submit

$("student").value = store.get("bw.name", "");
$("class-code").value = store.get("bw.classCode", "");

$("submit").onclick = async () => {
  const notice = $("submit-result");
  const student = $("student").value.trim();
  if (!student) {
    say(notice, "err", "Enter your name above so your professor knows whose program this is.");
    $("student").focus();
    return;
  }
  store.set("bw.name", student);
  const classCode = $("class-code").value.trim();
  store.set("bw.classCode", classCode);
  $("submit").disabled = true;
  try {
    const r = await api("POST", "/api/submissions", { student, source },
      classCode ? { "X-Class-Code": classCode } : {});
    const time = new Date().toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
    say(notice, "ok", `Submitted ${r.programName} for ${r.student} at ${time}. `
      + "To change it, upload a new version and submit again; your newest one is used.");
  } catch (e) {
    if (e instanceof ApiError && e.status === 422 && e.data) {
      show(e.data);
    } else if (e instanceof ApiError && e.status === 403) {
      $("code-field").classList.remove("hidden");
      say(notice, "err", "That class code isn't right. Ask your professor for it.");
      $("class-code").focus();
    } else {
      say(notice, "err", e.message);
    }
  } finally {
    $("submit").disabled = !(result && result.ok);
  }
};

function say(node, kind, text) {
  node.className = `notice ${kind}`;
  node.textContent = text;
}

// ------------------------------------------------------------------ start

api("GET", "/api/info")
  .then(info => $("code-field").classList.toggle("hidden", !info.requiresClassCode))
  .catch(() => {});
