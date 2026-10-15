// Unit tests for the browser-side BL highlighter. Run: node --test server/src/test/js
import { test } from "node:test";
import assert from "node:assert/strict";
import { tokenize, classify, highlight, linesWithProblems } from "../../main/resources/public/js/bl.js";

test("tokenizes like the compiler: only space, tab, CR, LF separate", () => {
  const parts = tokenize("IF\tnext-is-empty  THEN\r\nmove\u000bskip");
  assert.deepEqual(parts.filter(p => p.kind !== "space").map(p => [p.text, p.kind]),
    [["IF", "kw"], ["next-is-empty", "cond"], ["THEN", "kw"], ["move\u000bskip", "bad"]]);
});
test("classifies", () => {
  assert.equal(classify("move"), "prim");
  assert.equal(classify("Move"), "ident");
  assert.equal(classify("go_now"), "bad");
  assert.equal(classify("a-1"), "ident");
});
test("escapes HTML and marks errors", () => {
  const html = highlight("PROGRAM <x> IS", [{ offset: 8, length: 3, severity: "error" }]);
  assert.match(html, /<span class="tok-bad mark-err">&lt;x&gt;<\/span>/);
  assert.ok(!html.includes("<x>"));
});
test("zero-length error at end of file marks the last token", () => {
  const src = "PROGRAM P IS\nBEGIN\n";
  const html = highlight(src, [{ offset: src.length, length: 0, severity: "error" }]);
  assert.match(html, /tok-kw mark-err">BEGIN/);
});
test("warning never hides an error on the same token", () => {
  const html = highlight("move", [{ offset: 0, length: 4, severity: "warning" }, { offset: 0, length: 4, severity: "error" }]);
  assert.match(html, /mark-err/);
});
test("lines with problems keep the worst severity", () => {
  const lines = linesWithProblems([{ line: 2, severity: "warning" }, { line: 2, severity: "error" }, { line: 3, severity: "warning" }]);
  assert.equal(lines.get(2), "error");
  assert.equal(lines.get(3), "warning");
});
