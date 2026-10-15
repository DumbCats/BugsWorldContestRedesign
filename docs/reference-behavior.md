# Reference behavior of the existing BugsWorld lab

This is our spec for the BugsWorld rules. It's based on the two jars in `lib/` (`BWServer.jar`, `BWClient.jar`), which the professor allowed us to decompile (2026-10-14). It also uses black-box runs of their engine and compiler with the tools in `tools/oracle/`.

The decompiled source is **not** in this repo. This file describes the behavior in our own words.

Labels: **CONFIRMED** means we read it in the code *and* checked it by running the jar. **READ** means we read it in the code but haven't run it. **OURS** means a deliberate change in the web app.

## 1. The jar architecture

- `BWServer.jar` and `BWClient.jar` contain the same `bugsworld` package plus the OSU `components` library. Only `Main-Class` differs. They're built for Java 8.
- **The server runs every bug.** The client only loads a program (`.bl` compiled with the bundled `Program1`, or a `.bo` file) and uploads the integers once. After that, nothing runs on the client. The old `bw-display` is gone; the server window draws the world.
- So the "client" is really a submission uploader. In the web app, that's an upload form.

## 2. Game lifecycle (READ; the GUI buttons map onto it)

```
New Game ──> Open <──> Closed ──> Running <──> Paused
                                     │            │
                                     └──> Ended <─┘
```

- **New Game** sets rows, columns and bugs per player, then builds the world.
- **Open** accepts players. Each player is placed as soon as it joins.
- **Close** stops accepting players.
- **Start** begins ticking. **Pause** and **Start** alternate.
- **End** disconnects all players and removes the game.
- There is **no automatic end**: no win check, extinction check or tick limit. The professor decides when it's over.
- There is no score either. A stats panel shows each species' `min:current:max` bug count and a bar for its share of all bugs.

## 3. Settings (READ)

| Setting | Default | Range |
|---|---|---|
| Rows | 15 | 1–100 |
| Columns | 15 | 1–100 |
| Bugs per player | 50 | 1–300 |
| Tick period | 100 ms (10 ticks/s) | fixed |
| Max species | 10 | fixed |

- The playable grid is `rows × columns`, with a ring of wall cells around it. A bug at the edge sees `WALL`, and there are no interior walls.
- Each joining player gets a random unused species ID from 0–9. The ID selects that species' bug image (`bug0.gif`–`bug9.gif`) and stats color.

## 4. Placement (READ)

When a player joins, `bugsPerPlayer` bugs go on random unoccupied cells. Each bug gets a random facing and starts with PC 0.

## 5. Ticks (CONFIRMED)

Each tick:

1. Shuffle **all** bugs into a random order.
2. For each bug, in that order:
   1. Look at the cell ahead *now*: wall, empty, friend (same species) or enemy. This reflects everything earlier bugs did in this same tick.
   2. Run the VM from the bug's PC to the next primitive (§6).
   3. Apply that primitive immediately:
      - `move`: moves forward only if the cell ahead is empty.
      - `turnleft` / `turnright`: rotates 90°.
      - `infect`: works only if the cell ahead holds an enemy. The target switches to the infector's species and program, and its PC resets to 0. It keeps its position and facing.
      - `skip` and `halt`: do nothing.

So bugs act one at a time, not all at once. Two bugs can never conflict over the same cell, because the second one sees the first one's result.

**Infected bugs act again in the same tick.** If a bug is infected before its turn in the shuffled order, it still gets that turn later in the tick, as its new species, starting at PC 0.

Bugs are never created or destroyed. Infection only moves bugs between species. A species is extinct when its count reaches 0.

## 6. VM (CONFIRMED)

- Opcodes are as in `Opcode.java` (codes 0–16).
- A conditional jump jumps to its operand when the condition is **false** and otherwise moves PC forward by 2. `JUMP` always jumps. `random` is a 50% coin flip from an unseeded `java.util.Random` per bug.
- The VM runs jumps until it reaches a primitive (codes 0–5), returns that primitive, and moves PC forward by 1.

## 7. Object code and the `.bo` format (CONFIRMED)

- Object code is a flat list of integers with no header. Primitives use one slot. Jumps use two: the opcode, then an absolute target address.
- Code generation patterns:

| Construct | Generated code |
|---|---|
| primitive | `op` |
| `IF c THEN s END IF` | `JIN_c L; s; L:` |
| `IF c THEN s ELSE t END IF` | `JIN_c E; s; JUMP L; E: t; L:` |
| `WHILE c DO s END WHILE` | `T: JIN_c L; s; JUMP T; L:` |
| call `foo` | the body of `foo`, copied in at that point |
| program | body, then `HALT` |

  Here `JIN_c` is the "jump if not c" opcode for condition `c`. Instructions that are never called produce no code.
- The `.bo` file format is the length on the first line, then one integer per line.
- The upload format is the integers on one line, separated by `", "`.

## 8. Compiler (CONFIRMED with the bundled `components.program.Program1`, read and fuzzed)

**Tokens.** A token is a maximal run of characters other than space, tab, CR and LF. Any other character, including a form feed or a non-breaking space, is part of a token.
- Keywords and conditions are fixed lists.
- An identifier matches `[a-zA-Z][a-zA-Z0-9-]*` and isn't a keyword or condition. Primitives such as `move` are identifiers, so calling one is an ordinary call.
- Matching is case-sensitive: `Move` and `end` are valid instruction names.
- BL has no comment syntax.

**Parsing.** It stops at the first error and gives no line or column. Messages look like `Keyword "X" expected, found: "Y"`. The order of checks for an instruction is:
1. its name is an identifier;
2. its name isn't a primitive;
3. `IS`, body, `END`, and a name that matches;
4. the name isn't already defined (checked only after the body is parsed).

The program must end with `END name`, and nothing may follow it.

**Calls.** Calls to instructions defined *later* in the file are fine. Code is generated by inlining, starting from the main body. An undefined call or recursion is therefore an error **only if it's reachable from `BEGIN`**. An instruction that is never called can contain both, and the program still compiles.

**Size.** There is no limit on code size, and nested calls can make code grow exponentially.

See `engine/src/test/resources/golden/errors/*.err` for the exact messages. Our compiler accepts exactly the same programs (fuzz-tested; see PLAN.md Phase 1). Its only differences are the two OURS rows in §10.

## 9. Upload handshake (READ; for reference only, we won't reimplement it)

1. The client connects to the server's game port and sends three lines: its own host, its own port, and `true`.
2. The server opens a second connection back to the client and sends the species ID. The ID is negative when the server refuses the client. The client code knows `-1` (too many) and `-2` (tournament in progress), but this server never sends either.
3. The client sends its program on one line, integers separated by `", "`.
4. The server confirms with `Read bug: [...]`. After that, the only traffic is the server closing both connections when the game ends.

## 10. Defects in the jar, and what we do instead

| Jar behavior | Status | OURS |
|---|---|---|
| A bug that reaches `HALT` steps past the end of its program on its next turn. The exception kills the tick task and **the whole game freezes silently** while the clock keeps running. | CONFIRMED | A halted bug stays on `HALT` and does nothing forever. |
| A jump cycle with no primitive (e.g. `WHILE true DO END WHILE`) spins forever on the tick thread, and **the game freezes**. This also catches a common pattern: `WHILE true DO IF next-is-enemy THEN infect END IF END WHILE` freezes the game as soon as such a bug faces anything but an enemy. | CONFIRMED (both examples) | Per-turn jump budget. When it runs out, the bug does nothing for that turn (configurable). The compiler warns about any loop that can repeat without acting (`silent-loop`) and any program that can reach its end (`program-can-halt`). |
| A leading byte-order mark (some Windows editors add one) makes the reference reject the file. | CONFIRMED | Ignored. |
| No limit on object-code size. | READ | Compile error above 1,000,000 numbers. |
| More bugs than free cells crashes while placing bugs, leaving a partially placed player. | READ | Check the settings when the game is created and when each player is added, and show an error. |
| An 11th player crashes on accept. | READ | Limit games to 10 species, with a clear message. |
| Everything is unseeded (shuffle, placement, facing, `random`). | READ | One seeded random number generator per game. The same seed, settings and programs replay the same game. |
| The game clock busy-loops, using a full CPU core while running. | READ | Count ticks and keep wall-clock time separately. |
| Two threads read and change the bug list with no locking, so the display may see half-finished ticks. | READ | The engine produces an immutable snapshot after each tick, and the display reads only snapshots. |

## 11. Tools

- `tools/oracle/regen-golden.sh` recompiles every `.bl` under `engine/src/test/resources/golden/` with the reference compiler and saves `.bo` (success) or `.err` (error) next to it.
- `tools/oracle/run-reference-game.sh rows cols bugs seconds "prog1" "prog2" ...` runs the real jar engine headlessly and prints per-species counts. Use it for statistical comparisons with our engine, never tick for tick, because the jar isn't seeded.

Both need `lib/BWClient.jar` / `lib/BWServer.jar` (gitignored) and Java 21+.
