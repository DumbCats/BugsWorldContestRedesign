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
  vm:       BugProgram (precomputed "can still act") + Vm.step -> Action
  sim:      Game (placement, ticks, stats), Snapshot, Event, AsciiRenderer; Simulate CLI
server/   Javalin: REST (compile, submissions, games) + WebSocket (live world)
web/      Vite app: student editor, professor dashboard, projector display
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
