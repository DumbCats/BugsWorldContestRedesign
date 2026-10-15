# Plan

The rules we're matching are in [reference-behavior.md](reference-behavior.md). This file covers how we build the web app and in what order.

## Decisions

| Topic | Decision |
|---|---|
| Backend | Java 21 (Gradle toolchain), Gradle 9 Kotlin DSL, multi-module |
| Web server | Javalin 7 + Jackson |
| Frontend | Vite + React + TypeScript, CodeMirror 6 editor, `<canvas>` world view (Phase 5) |
| Rules | Same as the jar by default (reference-behavior.md §2–7). Its defects are fixed (§10). Every new rule is optional and off by default. |
| Determinism | One seeded RNG per game; the same seed, settings and programs replay the same game |
| Submissions | `.bl` source (main path) and `.bo` upload, like the jar client |
| Art | Our own bug sprites and palette. We don't reuse the jar's `bug*.gif` files. |

## Modules

```
engine/   pure Java, no runtime dependencies
  compiler: Lexer -> Parser (AST + spans) -> Checker -> CodeGen -> ObjectCode
  vm:       run to the next primitive, with a jump budget
  sim:      World, Bug, Species, Game (lifecycle), tick loop, Snapshot, events
server/   Javalin: REST (compile, submissions, games) + WebSocket (live world)
web/      Vite app: student editor, professor dashboard, projector display
tools/oracle/   scripts that run the OSU jars as a test oracle
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

### Phase 2: VM
- [ ] `Vm.nextPrimitive(code, pc, sees, rng, budget)` returns the primitive and the new PC. Halted bugs stay halted.
- [ ] Jump budget (default: program length × 2). When it runs out, the bug skips its turn, and the engine records an event the dashboard can show.
- [ ] Tests for every opcode, for jumping when the condition is false versus falling through, for the HALT and budget cases, and for seeded `random`.

### Phase 3: Simulation engine
- [ ] `GameConfig`: rows and columns (1–100), bugs per species (1–300), up to 10 species, seed, ticks per second, jump budget, same-tick infection on/off. Optional extras, all off by default: a tick limit and stop when only one species is left.
- [ ] Lifecycle: create → open (add species) → close → run ⇄ pause → end, plus step-one-tick and reset-with-same-seed.
- [ ] Placement and the tick loop as above. Settings that don't fit the grid fail with a clear error.
- [ ] Immutable `Snapshot` (tick, bugs, per-species min/current/max) and an event stream (move, turn, infect, budget exhausted).
- [ ] Headless batch runner and ASCII view, for tests and for tournament batches.
- [ ] Check against the jar: run both engines many times on the same matchups with `run-reference-game.sh` and compare the distribution of final counts.

### Phase 4: Server
- [ ] `POST /api/compile` returns errors and warnings, object code and pretty-printed source.
- [ ] Submissions: create, list, fetch.
- [ ] Games: create, add species, open/close/start/pause/step/end/reset.
- [ ] `WS /ws/games/{id}` sends a full snapshot when a viewer connects, then a compact frame each tick.
- [ ] Storage: SQLite (xerial `sqlite-jdbc`) for submissions and game results.
- [ ] Auth: an instructor token from an environment variable, plus a class code and display name for students. OSU single sign-on can plug in later.

### Phase 5: Web frontend
- [ ] Student page: BL editor with syntax highlighting and inline errors, compile, download `.bo`, and a solo test run against built-in sample bugs.
- [ ] Professor dashboard: submission list, pick species, settings, the lifecycle controls, a speed slider, and results.
- [ ] Projector view: full-screen canvas, species legend and live min/current/max bars like the jar's stats panel.

### Phase 6: Deploy
- [ ] One Docker image (server + built frontend).
- [ ] Limits on source size, compile time and request rate.
- [ ] Hosting and login at OSU: still unknown.

## Still to ask the professor

1. Does he want a winner or score? The jar has none and he ends games by hand.
2. The client has a "tournament in progress" message that's never used. Does he want brackets or batches of games?
3. Keep the "infected bugs act again in the same tick" quirk?
4. Hosting, login, and who maintains it.
5. Can we reuse the BugsWorld name, the bug images and the components library?
