// Drives the three web pages in headless Chrome against a running server:
// uploads .bl files (broken, correct, with warnings), submits them, loads them
// into a simulation, runs it, and saves screenshots. Fails on any JavaScript
// error or failed expectation.
//
//   BUGSWORLD_INSTRUCTOR_TOKEN=demo BUGSWORLD_OPEN_BROWSER=false ./gradlew :server:run &
//   node tools/ui-check/ui-check.mjs http://localhost:7070 demo out-dir
//
// Needs Node 22+ (built-in WebSocket) and Google Chrome or Chromium.

import { spawn } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
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

/** Chooses a file in the page's file input, as a student would. */
async function upload(path) {
  const { root } = await cdp("DOM.getDocument");
  const { nodeId } = await cdp("DOM.querySelector", { nodeId: root.nodeId, selector: "#file" });
  await cdp("DOM.setFileInputFiles", { nodeId, files: [path] });
  await sleep(700);
}

try {
  await connect();
  await cdp("Runtime.enable");
  await cdp("Page.enable");
  await cdp("Log.enable");

  // ------------------------------------------------------------ student
  const files = mkdtempSync(join(tmpdir(), "bw-files-"));
  const write = (name, text) => { const path = join(files, name); writeFileSync(path, text); return path; };
  const sample = name => readFileSync(new URL(`../../samples/${name}.bl`, import.meta.url), "utf8");
  const broken = write("oops.bl", "PROGRAM Oops IS\nBEGIN\n    WHILE true DO\n        IF next-is-emtpy THEN\n            mvoe\n        END IF\n    END WHILE\nEND Oops\n");
  const sloppy = write("sloppy.bl", "PROGRAM Sloppy IS\nBEGIN\n    WHILE true DO\n        IF next-is-enemy THEN\n            infect\n        END IF\n    END WHILE\nEND Sloppy\n");

  await open("/");
  await js(`localStorage.clear()`);
  await open("/");
  expect(await js(`document.querySelector("#checked").classList.contains("hidden")`), "nothing shown before a file is chosen");
  expect(await js(`document.querySelector('a[href*="watch"]') === null`), "students get no link to the simulation");
  await shot("student");

  await upload(broken);
  expect(await js(`document.querySelector("#status").textContent`) === "2 errors", "uploaded file is checked at once");
  expect(await js(`document.querySelectorAll("#problems li.err").length`) === 2, "both errors listed");
  expect(await js(`document.querySelectorAll("#code .mark-err").length`) >= 2, "errors underlined in the code");
  expect(await js(`document.querySelector("#code .gutter div.err") !== null`), "margin marks the error lines");
  expect((await js(`document.querySelector("#problems").textContent`)).includes("Did you mean next-is-empty?"), "hint shown");
  expect(await js(`document.querySelector("#submit").disabled`), "can't submit a program with errors");
  await js(`document.querySelector("#problems li button").click()`);
  expect(await js(`document.querySelector("#code .gutter div.flash") !== null`), "clicking a problem highlights its line");
  await shot("student-errors");

  await upload(write("hunter.bl", sample("hunter")));
  expect(await js(`document.querySelector("#status").textContent`) === "✓ Compiles", "a correct file compiles");
  expect(await js(`document.querySelector("#submit").textContent`) === "Submit Hunter", "submit names the program");
  await js(`document.querySelector("#submit").click()`);
  await sleep(300);
  expect((await js(`document.querySelector("#submit-result").textContent`)).startsWith("Enter your name"), "asks for a name first");
  await js(`document.querySelector("#student").value = "Ann Lee"`);
  await js(`document.querySelector("#submit").click()`);
  await sleep(600);
  const submitted = await js(`document.querySelector("#submit-result").textContent`);
  expect(submitted.startsWith("Submitted Hunter for Ann Lee"), `submit confirms (${submitted.slice(0, 40)}…)`);
  await shot("student-submitted");

  for (const [name, path] of [["Bo Chen", write("turret.bl", sample("turret"))],
                              ["Cy Diaz", write("wanderer.bl", sample("wanderer"))],
                              ["Dee Fox", sloppy]]) {
    await upload(path);
    await js(`document.querySelector("#student").value = "${name}"`);
    await js(`document.querySelector("#submit").click()`);
    await sleep(500);
  }
  expect((await js(`document.querySelector("#status").textContent`)).includes("1 warning"), "warnings are shown to the student");
  await shot("student-warning");

  await open("/", 390, 844);
  await upload(broken);
  await shot("student-phone");
  await open("/", 1440, 900, true);
  await upload(sloppy);
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
  expect(await js(`document.querySelectorAll("#submissions li").length`) === 4, "four students listed");
  expect(await js(`document.querySelectorAll("#submissions .badge.warn").length`) === 1, "the sloppy program shows a warning badge");
  await js(`[...document.querySelectorAll("#submissions li")].find(li => li.textContent.includes("Dee Fox")).querySelector("button").click()`);
  await sleep(600);
  expect((await js(`document.querySelector("#source-warnings").textContent`)).includes("can repeat without the bug doing anything"), "View lists the program's warnings");
  await shot("admin-view");
  await js(`document.querySelector("#source-close").click()`);
  await js(`document.querySelector("#add-all").click()`);
  await sleep(1500);
  expect(await js(`document.querySelectorAll("#legend li").length`) === 4, "one click loads all four programs");
  await shot("admin-setup");
  await js(`document.querySelector("#run").click()`);
  await sleep(2500);
  expect(await js(`document.querySelector("#game-status").textContent`) === "Running", "Start runs the simulation");
  await shot("admin-running");
  expect(await js(`document.querySelector("#game-title").textContent`) === "Simulation", "dashboard calls it a simulation");
  await js(`document.dispatchEvent(new KeyboardEvent("keydown", { key: " " }))`);
  await sleep(500);
  expect(await js(`document.querySelector("#game-status").textContent`) === "Paused", "space pauses");
  await js(`document.dispatchEvent(new KeyboardEvent("keydown", { key: " " }))`);

  // ---------------------------------------------------------- projector
  expect((await js(`document.querySelector("#projector").getAttribute("href")`)) === `/watch#token=${token}`,
    "the dashboard's projector button carries the sign-in");
  const savedToken = await js(`localStorage.getItem("bw.token")`);
  await js(`localStorage.clear()`);
  await open("/watch", 1920, 1080);
  expect(!(await js(`document.querySelector("#login").classList.contains("hidden")`)), "projector without the token asks for it");
  expect(await js(`document.querySelectorAll("#legend li").length`) === 0, "and shows no simulation");
  await shot("watch-locked");
  const before = problems.length;
  await js(`document.querySelector("#token").value = "wrong"`);
  await js(`document.querySelector("#login-form").requestSubmit()`);
  await sleep(800);
  // The browser logs the server's 401 for the wrong token; that one is expected.
  problems.splice(before, problems.length - before,
    ...problems.slice(before).filter(p => !p.includes("status of 401")));
  expect((await js(`document.querySelector("#login-error").textContent`)).includes("isn't right"), "a wrong token is refused");
  await js(`localStorage.setItem("bw.token", ${JSON.stringify(savedToken)})`);
  await open(`/watch#token=${token}`, 1920, 1080);
  await sleep(1000);
  expect(await js(`document.querySelectorAll("#legend li").length`) === 4, "projector shows every program");
  await shot("watch-running");

  await fetch(`${base}/api/admin/game/end`, { method: "POST", headers: { Authorization: `Bearer ${token}` } });
  await sleep(800);
  expect((await js(`document.querySelector("#overlay").textContent`)).startsWith("Finished after"), "projector shows the final counts");
  expect(await js(`document.querySelectorAll("#overlay ol.final li").length`) === 4, "every program is in the final list");
  await shot("watch-ended");

  await open(`/admin`);
  await sleep(1000);
  await js(`document.querySelector("#results-box").open = true`);
  await sleep(800);
  expect((await js(`document.querySelector("#results").textContent`)).includes("4 programs"), "past runs are listed");
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
