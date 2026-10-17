# Plan

The rules we're matching are in [reference-behavior.md](reference-behavior.md). This file covers how we build the web app and in what order.

## Decisions

| Topic | Decision |
|---|---|
| Backend | Java 21 (Gradle toolchain), Gradle 9 Kotlin DSL, multi-module |
| Web server | Javalin 7 + Jackson |
| Frontend | Plain HTML, CSS and JavaScript modules served by the Java server, with **no build step**. The professor needs only Java (no Node, no internet). This replaces the earlier Vite/React plan to make the app as easy as possible to run. |
| Rules | Same as the jar by default (reference-behavior.md §2–7). Its defects are fixed (§10). Every new rule is optional and off by default. |
| Determinism | One seeded RNG per game; the same seed, settings and programs replay the same game |
| Submissions | `.bl` source (main path) and `.bo` upload, like the jar client |
| Art | Our own bug sprites and palette. We don't reuse the jar's `bug*.gif` files. |

## Modules

```
engine/   pure Java, no runtime dependencies
  compiler: Lexer -> Parser (AST + spans) -> Checker -> CodeGen -> ObjectCode
  vm:       BugProgram (precomputed "can still act") + Vm.step -> Action
  sim:      Game (placement, ticks, stats), Snapshot, Event, AsciiRenderer; Simulate CLI
server/   Javalin: Server (routes), GameRunner (game thread), LiveViewers (WebSocket),
          Database (SQLite); static page in resources/public
server/src/main/resources/public/   web pages: / (students upload .bl files), /watch (projector), /admin (professor)
tools/oracle/   scripts that run the OSU jars as a test oracle
samples/  example bugs (also used by the tests)
```

## The tick model

Each tick builds a turn queue holding every bug, shuffled with the game's seeded RNG. Bugs are dequeued one at a time. Each one senses the world as it is at that moment, runs the VM to its next primitive, and applies it right away. When the queue is empty, the tick is over. We publish a snapshot and start a new queue for the next tick.

Two rules make this match the jar:

1. **The queue holds bugs, not instructions.** A bug's instruction is computed when it's dequeued, never when the queue is built. If every bug decided at the start of the tick, decisions would be stale. A bug would "see" an empty cell that another bug had already moved into, and we'd need a conflict-resolution step the jar doesn't have. It would also be much easier to dodge an infection.
2. **A new random order every tick.** Otherwise the bugs at the front of the queue would always act first, which is an unfair advantage.

Two details carry over from the jar, and both are configurable:
- A bug infected earlier in the tick still takes its queued turn later, as the new species, from PC 0 (default **on**, same as the jar).
- The order is shuffled across all bugs, not round-robin by species (default: same as the jar).

The tick runs on a single game thread. Server commands (start, pause, step, add species) are posted to that thread, so the world never needs locks, unlike the jar.

## Phases

### Phase 0: Groundwork ✅
- [x] Gradle multi-module build with wrapper and a JDK 21 toolchain (`./gradlew build`)
- [x] `Opcode` and `ObjectCode` (`.bo` read, write and validate) with tests
- [x] Reference tools that run the jar compiler and engine headlessly
- [x] Golden corpus: 13 valid programs with the reference `.bo`, and 19 error cases with the reference message
- [x] Rules written up from the decompiled jars

### Phase 1: Compiler ✅
Code is in `engine/.../compiler/` and the entry point is `Compiler.compile(source)`. The result holds the object code, a source map (address → BL statement), errors and warnings, and the formatted source.

- [x] Lexer: tokens with line and column, classified exactly like the reference `Tokenizer`.
- [x] Parser: recursive descent that recovers from errors and reports all of them. A stack of open blocks lets it tell an unclosed `IF` apart from `END WHILE` typed by mistake for `END IF`. It explains `not`, `and`, comments, `_` in names, invisible characters, and `THEN`/`DO` mix-ups.
- [x] Keyword-case retry: if a program fails, it's parsed a second time with `end if`, `Then` and so on treated as keywords. That gives one clear error each instead of a cascade, and never changes which programs are accepted.
- [x] Checker: duplicate names, primitive names, undefined calls ("did you mean" suggestions, case hints) and recursion (shows the cycle). These are errors only when reachable from `BEGIN`, like the reference. In instructions that are never called they're warnings.
- [x] Warnings: an unused instruction; a loop that can repeat without acting; a program that can reach its end.
- [x] A limit on code size: 1,000,000 numbers once calls are expanded, since inlining can grow code exponentially.
- [x] Code generation following reference-behavior.md §7.
- [x] Pretty-printer (`CompileResult.formatted()`).
- [x] Tests:
  - the golden valid cases match exactly;
  - each golden error case gets the expected first error (line and code);
  - about 25 tests of the messages for common mistakes;
  - formatting doesn't change the generated code;
  - **`ReferenceOracleTest` compares against the jar's compiler on random and mutated programs.** 3,000 cases run in every build, and 300,000 passed (seeds 1–3). Rerun at that size with `-Dbugsworld.fuzzCases=100000 -Dbugsworld.fuzzSeed=N`.

### Phase 2: VM ✅
Code is in `engine/.../vm/`.

- [x] `Vm.step(program, pc, sees, rng)` returns the action, the next PC and the number of jumps.
- [x] A halted bug stays halted, and so does a program that runs off its end.
- [x] `BugProgram` precomputes, for each of the four things a bug can see, which addresses can still reach an action. A bug in a loop that can never act is reported `STUCK` immediately and loses its turn, instead of freezing the game.
- [x] A jump budget (10,000) is the backstop for loops that only `random` can exit. When it runs out, the result is `OUT_OF_JUMPS` and the bug resumes from there next turn.
- [x] `random` draws exactly one boolean from the game's generator, so seeded runs replay exactly.
- [x] Uploaded `.bo` files may only jump to the start of an instruction.
- [x] Tests:
  - every opcode and condition against every view;
  - HALT, STUCK and budget cases;
  - **`VmReferenceTest`**: the same action sequence as the jar's own `Bug` class for 5,000 random programs (about 16,000 actions compared).

### Phase 3: Simulation engine ✅
Code is in `engine/.../sim/`, plus `Simulate` for the terminal.

- [x] `GameConfig`: the jar's defaults and ranges, plus seed and jump budget. Three switches, defaulting to the jar's behavior: infected bugs act in the same tick, a tick limit, and end when only one species is left.
- [x] `Game`:
  - species can be added and removed during setup (at most 10; a full grid gives a clear error);
  - `step()` runs one tick, and `end()` ends the game;
  - placement and the tick loop follow the queue model above;
  - pause, and opening or closing submissions, belong to the server, which owns the game thread.
- [x] Replays: the same seed, settings and order of adding species replay the same game, so "reset" just builds a new `Game` the same way.
- [x] Immutable `Snapshot`:
  - the tick and phase;
  - every bug's position, facing and PC;
  - per species, the current, minimum and maximum counts and how many bugs were stuck, out of jumps or halted;
  - the events of the last tick (moved, turned, infected).
- [x] `AsciiRenderer` and `./gradlew -q :engine:simulate --args="samples/hunter.bl samples/wanderer.bl [--show N] [--runs N]"` for single games or batches.
- [x] Sample bugs in `samples/` (hunter, wanderer, turret, flocker). They compile with no warnings and are in the standard format.
- [x] Tests:
  - `GameTest`: exact scenarios for walls, moving, infection, same-tick infection, halted and stuck bugs, stats and endings;
  - **`EngineReferenceTest`**: plays the same matchups many times in our engine and in the jar's real `Game` (driven tick by tick), and checks that the average final counts agree. This run uses 300 games; I also checked once at 3,000 and 12,000 games, with every difference within about 1 standard error.

### Phase 4: Server ✅
Code is in `server/`. `Server.java` lists every endpoint.

- [x] `POST /api/compile` returns the errors and warnings (line, column, offset, hint), the object code and the formatted source.
- [x] Submissions:
  - `POST /api/submissions` takes `{student, source}` and stores the program only if it compiles; otherwise it returns 422 with the diagnostics.
  - The instructor can list, view and delete them.
  - Only the source is stored, and it's recompiled when added to a game.
- [x] One current game, as in the jar: new (any settings and seed), add or remove species from submissions, start, pause, step, end, reset (replays from the same seed), and speed (1–100 ticks per second).
  - The jar's open/close step for joining is replaced by the professor adding submissions to the game.
- [x] `GameRunner` runs the game on its own thread, and every command is queued to it.
  - An error during a tick stops the game cleanly with a log message. The jar froze silently.
- [x] `WS /ws/game` sends the full `GameView` (bugs flattened to five numbers each) when a viewer connects, then on every change.
  - At most 20 frames per second, however fast the game ticks.
  - A viewer still receiving the previous frame skips to the next one, so a slow laptop can't hold anyone up.
- [x] SQLite (`data/bugsworld.db`, gitignored) stores submissions and finished-game results, so they survive restarts.
- [x] Access:
  - `/api/admin/*` needs `Authorization: Bearer <BUGSWORLD_INSTRUCTOR_TOKEN>`. If the variable isn't set, a token is generated and printed at startup.
  - If `BUGSWORLD_CLASS_CODE` is set, submitting needs the `X-Class-Code` header.
  - OSU single sign-on can plug in later.
- [x] At startup the server prints the URLs students can use: one per network address.
- [x] A temporary live viewer at `/` (canvas + species table) for testing until Phase 5.
- [x] Tests: 13 end-to-end tests run a real server on a random port and use HTTP and WebSocket. They cover auth, class code, validation, the lifecycle, reset replays, restart persistence, results and live frames.

### Phase 5: Web pages ✅
Pages are in `server/src/main/resources/public/`. Shared modules: `api.js`, `world.js` (canvas), `bl.js` (highlighter), `codeview.js` (read-only code with marks).

The purpose is to check students' code-writing ability. Students write programs on their own and bring them to class, so there is **no editor and no practice game**, and the app is a simulator rather than a game.

- [x] **Students (`/`):**
  - name, then choose or drag in the `.bl` file;
  - it's compiled at once, and the student sees their code read-only with errors and warnings underlined and marked in the margin;
  - each problem shows a hint, and clicking it highlights its line;
  - **Submit** is enabled only when the program compiles.
- [x] **Professor only:** students can upload and submit, nothing else. The simulation's data is `GET /api/admin/game`, and the live WebSocket needs `?token=` (it closes with 1008 otherwise). The projector page asks for the token unless it's opened from the dashboard (`/watch#token=…`) or the browser is already signed in.
- [x] **Projector (`/watch`):**
  - dark theme, a world as large as the screen allows, and a large legend with counts, share bars and min/max;
  - before the start it shows the address to submit at; when finished, every program's final count;
  - <kbd>F</kbd> toggles full screen, and it reconnects automatically.
- [x] **Professor (`/admin`):**
  - the server opens it signed in;
  - the student address with a Copy button;
  - submissions as newest per student, with search and a **⚠ warning count** for each;
  - View shows the code with its warnings explained; Load and Delete;
  - **Load everyone's newest program** in one click;
  - Start/Pause, Step, Rerun and End, with <kbd>Space</kbd> and <kbd>→</kbd> shortcuts;
  - live speed, the live world, the loaded programs with Remove, and a "next step" hint;
  - new-simulation settings that keep the loaded programs;
  - past runs with final counts.
- [x] Server support:
  - `/api/info`;
  - `/api/compile` checks uploads;
  - `/api/admin/info`;
  - submissions report their compiler warnings (cached).
- [x] Tests:
  - server tests for the pages and warnings;
  - `server/src/test/js` highlighter tests, run by `./gradlew check` when Node is installed;
  - **`tools/ui-check/ui-check.mjs`** drives all three pages in headless Chrome. It uploads a broken file, a correct one and one with warnings, then submits, signs in by link, checks the warning badge and View, loads everyone, starts and pauses, finishes, and checks the final counts and past runs. It fails on any JavaScript error and saves screenshots.

### Phase 6: Deploy (partly done)
- [x] `./gradlew :server:distZip` builds `bugsworld.zip` with `bin/bugsworld` and `bugsworld.bat`, and needs only Java 21+.
  - Startup prints a short block: dashboard, token, student addresses, class code, data folder.
  - It opens the dashboard and keeps data in `~/BugsWorld`.
  - A generated token is saved there, so it survives restarts.
- [x] Limits on source size (100,000 characters) and name length. Compiling is bounded by the code-size cap.
- [ ] Rate limiting, if the class network turns out to need it.
- [ ] A Docker image, if it ends up hosted on a server rather than the professor's computer.
- [ ] Hosting and login at OSU: still unknown.

## Still to ask the professor

1. Does he want a winner or score? The jar has none and he ends games by hand.
2. The client has a "tournament in progress" message that's never used. Does he want brackets or batches of games?
3. Keep the "infected bugs act again in the same tick" quirk?
4. Hosting, login, and who maintains it.
5. Can we reuse the BugsWorld name, the bug images and the components library?
