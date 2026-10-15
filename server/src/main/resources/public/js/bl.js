// BL syntax highlighting. Tokenizes exactly like the compiler: a token is a
// run of characters other than space, tab, CR and LF.

export const KEYWORDS = new Set([
  "PROGRAM", "IS", "INSTRUCTION", "END", "IF", "THEN", "ELSE", "WHILE", "BEGIN", "DO",
]);
export const CONDITIONS = new Set([
  "next-is-empty", "next-is-not-empty", "next-is-wall", "next-is-not-wall",
  "next-is-friend", "next-is-not-friend", "next-is-enemy", "next-is-not-enemy",
  "random", "true",
]);
export const PRIMITIVES = new Set(["move", "turnleft", "turnright", "infect", "skip"]);
const IDENTIFIER = /^[a-zA-Z][a-zA-Z0-9-]*$/;

/** Splits source into {text, start, kind} tokens and {text, start} whitespace runs. */
export function tokenize(source) {
  const parts = [];
  const re = /[ \t\r\n]+|[^ \t\r\n]+/g;
  let m;
  while ((m = re.exec(source)) !== null) {
    const text = m[0];
    const space = /^[ \t\r\n]/.test(text);
    parts.push({ text, start: m.index, kind: space ? "space" : classify(text) });
  }
  return parts;
}

export function classify(text) {
  if (KEYWORDS.has(text)) return "kw";
  if (CONDITIONS.has(text)) return "cond";
  if (PRIMITIVES.has(text)) return "prim";
  if (IDENTIFIER.test(text)) return "ident";
  return "bad";
}

function escape(text) {
  return text.replace(/[&<>]/g, ch => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[ch]));
}

/**
 * HTML for the highlighted source. Tokens inside a diagnostic's span get a
 * squiggly underline (errors win over warnings). A zero-length span (e.g.
 * "the file ended here") marks the token before it.
 */
export function highlight(source, diagnostics = []) {
  const ranges = diagnostics.map(d => ({
    start: d.offset,
    end: d.offset + Math.max(d.length, 1),
    cls: d.severity === "error" ? "mark-err" : "mark-warn",
  }));
  let html = "";
  for (const part of tokenize(source)) {
    if (part.kind === "space") {
      html += escape(part.text);
      continue;
    }
    const start = part.start;
    const end = start + part.text.length;
    let mark = "";
    for (const r of ranges) {
      const overlaps = r.start < end && r.end > start;
      const atEnd = r.start >= source.length && end === source.trimEnd().length;
      if (overlaps || atEnd) {
        if (r.cls === "mark-err") { mark = "mark-err"; break; }
        mark = "mark-warn";
      }
    }
    const cls = `tok-${part.kind}${mark ? " " + mark : ""}`;
    html += `<span class="${cls}">${escape(part.text)}</span>`;
  }
  // A trailing newline needs a character after it to take up a line.
  return html + "\n ";
}

/** Line numbers (1-based) of diagnostics, with the worst severity per line. */
export function linesWithProblems(diagnostics) {
  const lines = new Map();
  for (const d of diagnostics) {
    if (lines.get(d.line) !== "error") lines.set(d.line, d.severity);
  }
  return lines;
}
