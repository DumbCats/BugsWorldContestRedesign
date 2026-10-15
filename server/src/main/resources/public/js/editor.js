// A small code editor for BL: a textarea over a highlighted copy of the text,
// with line numbers, error markers, Tab indentation and auto-indent.

import { highlight, linesWithProblems } from "./bl.js";

const INDENT = "    ";
const OPENERS = /\b(THEN|DO|IS|ELSE|BEGIN)\s*$/;

export function createEditor(container, { value = "", onChange = () => {}, label = "Program" } = {}) {
  container.classList.add("editor");
  const gutter = document.createElement("div");
  gutter.className = "gutter";
  gutter.setAttribute("aria-hidden", "true");
  const code = document.createElement("div");
  code.className = "code";
  const pre = document.createElement("pre");
  pre.setAttribute("aria-hidden", "true");
  const textarea = document.createElement("textarea");
  Object.assign(textarea, { spellcheck: false, value });
  textarea.setAttribute("autocapitalize", "off");
  textarea.setAttribute("autocomplete", "off");
  textarea.setAttribute("autocorrect", "off");
  textarea.setAttribute("wrap", "off");
  textarea.setAttribute("aria-label", label);
  code.append(pre, textarea);
  container.replaceChildren(gutter, code);

  let diagnostics = [];
  let tabMovesFocus = false;

  function render() {
    pre.innerHTML = highlight(textarea.value, diagnostics);
    const problems = linesWithProblems(diagnostics);
    const count = textarea.value.split("\n").length;
    const rows = [];
    for (let line = 1; line <= count; line++) {
      const severity = problems.get(line);
      const cls = severity === "error" ? "err" : severity ? "warn" : "";
      rows.push(`<div class="${cls}">${line}</div>`);
    }
    gutter.innerHTML = rows.join("");
    syncScroll();
  }

  function syncScroll() {
    pre.scrollTop = textarea.scrollTop;
    pre.scrollLeft = textarea.scrollLeft;
    gutter.scrollTop = textarea.scrollTop;
  }

  /** Inserts text at the cursor, keeping the browser's undo history where possible. */
  function insert(text) {
    if (!document.execCommand || !document.execCommand("insertText", false, text)) {
      textarea.setRangeText(text, textarea.selectionStart, textarea.selectionEnd, "end");
      textarea.dispatchEvent(new Event("input"));
    }
  }

  textarea.addEventListener("input", () => {
    render();
    onChange(textarea.value);
  });
  textarea.addEventListener("scroll", syncScroll);
  textarea.addEventListener("keydown", event => {
    if (event.key === "Escape") {
      tabMovesFocus = true; // keyboard users can leave the editor with Esc, then Tab
      return;
    }
    if (event.key === "Tab" && !tabMovesFocus && !event.ctrlKey && !event.metaKey) {
      event.preventDefault();
      if (event.shiftKey) {
        outdentLine();
      } else {
        insert(INDENT);
      }
      return;
    }
    if (event.key === "Enter" && !event.shiftKey && !event.ctrlKey && !event.metaKey) {
      event.preventDefault();
      const before = textarea.value.slice(0, textarea.selectionStart);
      const line = before.slice(before.lastIndexOf("\n") + 1);
      let indent = line.match(/^[ \t]*/)[0];
      if (OPENERS.test(line)) indent += INDENT;
      insert("\n" + indent);
      return;
    }
    tabMovesFocus = false;
  });
  textarea.addEventListener("blur", () => { tabMovesFocus = false; });

  function outdentLine() {
    const start = textarea.value.lastIndexOf("\n", textarea.selectionStart - 1) + 1;
    const line = textarea.value.slice(start);
    const remove = line.startsWith(INDENT) ? INDENT.length : (line.match(/^ {1,3}/) || [""])[0].length;
    if (remove > 0) {
      const cursor = textarea.selectionStart;
      textarea.setSelectionRange(start, start + remove);
      insert("");
      textarea.setSelectionRange(Math.max(start, cursor - remove), Math.max(start, cursor - remove));
    }
  }

  render();

  return {
    get value() { return textarea.value; },
    set value(text) {
      textarea.value = text;
      render();
      onChange(text);
    },
    setDiagnostics(list) {
      diagnostics = list;
      render();
    },
    /** Puts the cursor on a diagnostic's text and scrolls it into view. */
    reveal(offset, length) {
      textarea.focus();
      textarea.setSelectionRange(offset, offset + Math.max(length, 0));
      const lineHeight = parseFloat(getComputedStyle(textarea).lineHeight) || 22;
      const line = textarea.value.slice(0, offset).split("\n").length - 1;
      textarea.scrollTop = Math.max(0, line * lineHeight - textarea.clientHeight / 3);
      syncScroll();
    },
    focus() { textarea.focus(); },
  };
}
