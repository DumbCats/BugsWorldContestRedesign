// Read-only view of a BL program: line numbers, syntax colors, and
// errors/warnings underlined and marked in the margin.

import { highlight, linesWithProblems } from "./bl.js";

export function renderCode(container, source, diagnostics = []) {
  container.classList.add("codeview");
  const problems = linesWithProblems(diagnostics);
  const lineCount = source.replace(/\n$/, "").split("\n").length;
  const gutter = document.createElement("div");
  gutter.className = "gutter";
  gutter.setAttribute("aria-hidden", "true");
  for (let line = 1; line <= lineCount; line++) {
    const row = document.createElement("div");
    const severity = problems.get(line);
    if (severity) row.className = severity === "error" ? "err" : "warn";
    row.textContent = line;
    gutter.append(row);
  }
  const pre = document.createElement("pre");
  pre.innerHTML = highlight(source.replace(/\n$/, ""), diagnostics);
  container.replaceChildren(gutter, pre);
}

/** Scrolls a code view so `line` is visible, and briefly highlights it. */
export function revealLine(container, line) {
  const row = container.querySelector(`.gutter div:nth-child(${line})`);
  if (!row) return;
  container.scrollTop = Math.max(0, row.offsetTop - container.clientHeight / 3);
  row.classList.add("flash");
  setTimeout(() => row.classList.remove("flash"), 1200);
}
