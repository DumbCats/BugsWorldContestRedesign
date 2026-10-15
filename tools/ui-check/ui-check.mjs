// Drives the three web pages in headless Chrome against a running server:
// types errors, runs a practice game, submits, starts a class game, and saves
// screenshots. Fails on any JavaScript error or failed expectation.
//
//   BUGSWORLD_INSTRUCTOR_TOKEN=demo BUGSWORLD_OPEN_BROWSER=false ./gradlew :server:run &
//   node tools/ui-check/ui-check.mjs http://localhost:7070 demo out-dir
//
// Needs Node 22+ (built-in WebSocket) and Google Chrome or Chromium.

import { spawn } from "node:child_process";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const [base = "http://localhost:7070", token = "demo", outDir = "ui-check-out"] = process.argv.slice(2);
mkdirSync(outDir, { recursive: true });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const problems = [];

const chromeBin = process.env.CHROME || "google-chrome";
const port = 9333 + Math.floor(Math.random() * 500);
const chrome = spawn(chromeBin, [
  "--headless=new", `--remote-debugging-port=${port}`, "--no-first-run", "--no-default-browser-check",
  `--user-data-dir=${mkdtempSync(join(tmpdir(), "bw-chrome-"))}`, "about:blank",
], { stdio: "ignore" });

let ws;
let nextId = 1;
const pending = new Map();

async function connect() {
  for (let i = 0; i < 50; i++) {
    try {
      const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
      const page = targets.find(t => t.type === "page");
      if (page) {
        ws = new WebSocket(page.webSocketDebuggerUrl);
        await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
        ws.onmessage = event => {
          const msg = JSON.parse(event.data);
          if (msg.id && pending.has(msg.id)) {
            const { resolve, reject } = pending.get(msg.id);
            pending.delete(msg.id);
            msg.error ? reject(new Error(msg.error.message)) : resolve(msg.result);
          } else if (msg.method === "Page.javascriptDialogOpening") {
            // confirm() would block the page forever in headless mode: say OK.
            ws.send(JSON.stringify({ id: nextId++, method: "Page.handleJavaScriptDialog", params: { accept: true } }));
          } else if (msg.method === "Runtime.exceptionThrown") {
            problems.push(`JS exception: ${msg.params.exceptionDetails.exception?.description || msg.params.exceptionDetails.text}`);
          } else if (msg.method === "Runtime.consoleAPICalled" && msg.params.type === "error") {
            problems.push(`console.error: ${msg.params.args.map(a => a.value ?? a.description).join(" ")}`);
          } else if (msg.method === "Log.entryAdded" && msg.params.entry.level === "error") {
            problems.push(`browser: ${msg.params.entry.text} ${msg.params.entry.url || ""}`);
          }
        };
        return;
      }
    } catch { /* not up yet */ }
    await sleep(200);
  }
  throw new Error("Chrome did not start");
}

function cdp(method, params = {}) {
  const id = nextId++;
  ws.send(JSON.stringify({ id, method, params }));
  return new Promise((resolve, reject) => pending.set(id, { resolve, reject }));
}

async function js(expression) {
  const r = await cdp("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
  if (r.exceptionDetails) throw new Error(`evaluate failed: ${r.exceptionDetails.exception?.description || r.exceptionDetails.text}\n${expression}`);
  return r.result.value;
}

async function open(path, width = 1440, height = 900, dark = false) {
  await cdp("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 1, mobile: width < 600 });
  await cdp("Emulation.setEmulatedMedia", { features: [{ name: "prefers-color-scheme", value: dark ? "dark" : "light" }] });
  await cdp("Page.navigate", { url: base + path });
  await sleep(1500);
}

async function shot(name) {
  const { data } = await cdp("Page.captureScreenshot", { format: "png" });
  writeFileSync(join(outDir, `${name}.png`), Buffer.from(data, "base64"));
}

function expect(condition, message) {
  if (!condition) problems.push(`expectation failed: ${message}`);
  console.log(`${condition ? "ok  " : "FAIL"} ${message}`);
}

/** Replaces the editor's text the way typing would (fires input). */
const setSource = text => js(`(() => {
  const t = document.querySelector("#editor textarea");
  t.value = ${JSON.stringify(text)};
  t.dispatchEvent(new Event("input"));
})()`);

try {
  await connect();
  await cdp("Runtime.enable");
  await cdp("Page.enable");
  await cdp("Log.enable");

  // ------------------------------------------------------------ student
  await open("/");
  await js(`localStorage.clear()`);
  await open("/");
  expect(await js(`document.querySelector("#status").textContent`) === "✓ Compiles", "starter program compiles");
  expect(await js(`document.querySelectorAll("#examples option").length`) >= 5, "examples are listed");
  await shot("student");

  await setSource("PROGRAM Oops IS\nBEGIN\n    WHILE true DO\n        IF next-is-emtpy THEN\n            mvoe\n        END IF\n    END WHILE\nEND Oops\n");
  await sleep(900);
  const errorCount = await js(`document.querySelectorAll("#problems li.err").length`);
  expect(errorCount === 2, `two errors listed (got ${errorCount})`);
  expect(await js(`document.querySelectorAll(".editor .mark-err").length`) >= 2, "errors are underlined in the editor");
  expect(await js(`document.querySelector(".editor .gutter div.err") !== null`), "gutter marks the error lines");
  expect((await js(`document.querySelector("#problems").textContent`)).includes("Did you mean next-is-empty?"), "hint shown");
  expect(await js(`document.querySelector("#practice").disabled`), "practice disabled while there are errors");
  await shot("student-errors");

  await js(`document.querySelector("#problems li button").click()`);
  const selected = await js(`(() => { const t = document.querySelector("#editor textarea"); return t.value.slice(t.selectionStart, t.selectionEnd); })()`);
  expect(selected === "next-is-emtpy", `clicking a problem selects it in the editor (got "${selected}")`);

  await js(`(() => { const s = document.querySelector("#examples"); s.value = "hunter"; s.dispatchEvent(new Event("change")); })()`);
  await sleep(900);
  expect((await js(`document.querySelector("#editor textarea").value`)).startsWith("PROGRAM Hunter"), "loading an example replaces the program (after confirm)");
  expect(await js(`document.querySelector("#status").textContent`) === "✓ Compiles", "example compiles");

  await js(`document.querySelector("#practice").click()`);
  await sleep(2500);
  expect(!(await js(`document.querySelector("#practice-view").classList.contains("hidden")`)), "practice view appears");
  expect(await js(`document.querySelectorAll("#practice-legend li").length`) === 2, "practice legend has both species");
  const tick = await js(`document.querySelector("#tick").textContent`);
  expect(/tick [1-9]/.test(tick), `practice animates (${tick})`);
  await shot("student-practice");

  await js(`document.querySelector("#student").value = "Ann Lee"`);
  await js(`document.querySelector("#submit").click()`);
  await sleep(800);
  const submitted = await js(`document.querySelector("#submit-result").textContent`);
  expect(submitted.startsWith("Submitted Hunter"), `submit confirms (${submitted.slice(0, 40)}…)`);
  await shot("student-submitted");

  // two more students
  for (const [name, sample] of [["Bo Chen", "turret"], ["Cy Diaz", "wanderer"]]) {
    await js(`(() => { const s = document.querySelector("#examples"); s.value = "${sample}"; s.dispatchEvent(new Event("change")); })()`);
    await sleep(600);
    await js(`document.querySelector("#student").value = "${name}"`);
    await js(`document.querySelector("#submit").click()`);
    await sleep(600);
  }

  await open("/", 390, 844);
  await shot("student-phone");
  await open("/", 1440, 900, true);
  await shot("student-dark");

  // -------------------------------------------------------------- admin
  await open("/admin");
  await js(`localStorage.removeItem("bw.token")`);
  await open("/admin");
  expect(!(await js(`document.querySelector("#login").classList.contains("hidden")`)), "dashboard asks for the token");
  await shot("admin-login");

  await open(`/admin#token=${token}`);
  await sleep(1000);
  expect(await js(`location.hash`) === "", "token removed from the address bar");
  expect(await js(`document.querySelector("#dashboard").classList.contains("hidden")`) === false, "token in the link signs in");
  expect(await js(`document.querySelectorAll("#submissions li").length`) === 3, "three students listed");
  await js(`document.querySelector("#add-all").click()`);
  await sleep(1200);
  expect(await js(`document.querySelectorAll("#legend li").length`) === 3, "add-all puts all three in the game");
  await shot("admin-setup");
  await js(`document.querySelector("#run").click()`);
  await sleep(2500);
  expect(await js(`document.querySelector("#game-status").textContent`) === "Running", "start runs the game");
  await shot("admin-running");
  await js(`document.dispatchEvent(new KeyboardEvent("keydown", { key: " " }))`);
  await sleep(500);
  expect(await js(`document.querySelector("#game-status").textContent`) === "Paused", "space pauses");
  await js(`document.dispatchEvent(new KeyboardEvent("keydown", { key: " " }))`);

  // ---------------------------------------------------------- projector
  await open("/watch", 1920, 1080);
  await sleep(1000);
  expect(await js(`document.querySelectorAll("#legend li").length`) === 3, "projector shows the species");
  await shot("watch-running");

  await fetch(`${base}/api/admin/game/end`, { method: "POST", headers: { Authorization: `Bearer ${token}` } });
  await sleep(800);
  expect((await js(`document.querySelector("#overlay").textContent`)).toLowerCase().includes("winner")
      || (await js(`document.querySelector("#overlay").textContent`)).includes("tie"), "projector shows the winner");
  await shot("watch-ended");

  await open(`/admin`);
  await sleep(1000);
  await js(`document.querySelector("#results-box").open = true`);
  await sleep(800);
  expect((await js(`document.querySelector("#results").textContent`)).includes("won"), "past games list the winner");
  await shot("admin-results");
} catch (e) {
  problems.push(String(e.stack || e));
} finally {
  ws?.close();
  chrome.kill();
}

if (problems.length) {
  console.log("\nPROBLEMS:\n" + problems.map(p => " - " + p).join("\n"));
  process.exit(1);
}
console.log(`\nAll UI checks passed. Screenshots in ${outDir}/`);
