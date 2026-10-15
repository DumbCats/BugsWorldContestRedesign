# BugsWorldContestRedesign
Created to revamp the outdated BugsWorld contest for Ohio State University's Software 2 course sequence.

BugsWorld runs in a web browser. Students write their bugs in BL and get clear error messages as they type. They test against example bugs and submit with one click. The professor runs the class game from a dashboard and shows it on the projector. You don't need a VPN, ssh, X11 or anything installed on student computers.

## Running a lab (professor)

1. **Install Java 21 or newer** on the computer that will run the game, if it doesn't have it. Any free build works, for example from [adoptium.net](https://adoptium.net).
2. **Unzip `bugsworld.zip`** and run `bugsworld/bin/bugsworld.bat` on Windows or `bugsworld/bin/bugsworld` on macOS and Linux.
   - Your browser opens the dashboard, already signed in.
   - Keep the window that opened.
3. **Tell students the address** shown at the top of the dashboard (for example `192.168.1.133:7070`).
   - Their laptops must be on the same network as yours.
   - The projector view shows the address too, until the game starts.
4. **When submissions are in:**
   - Press **Add everyone's newest bug to the game**.
   - Press **Open projector view**, then **Start**. Use <kbd>Space</kbd> to pause and <kbd>→</kbd> to step one tick.
5. **When a game ends:**
   - **Replay** runs the exact same game again.
   - **New game settings** changes the world size, bug counts, seed or tick limit.
   - Past results are listed under **Past games**.

Submissions and results are saved in a `BugsWorld` folder in your home folder, so restarting loses nothing. Optional settings (environment variables):

| Variable | Effect |
|---|---|
| `BUGSWORLD_CLASS_CODE` | Students must enter this code to submit. This keeps out strangers on a shared network. |
| `BUGSWORLD_INSTRUCTOR_TOKEN` | Sets the dashboard password. Otherwise one is generated and kept in the data folder. |
| `PORT` | The web port (default 7070). |
| `BUGSWORLD_DATA_DIR` | Where to keep submissions and results. |
| `BUGSWORLD_OPEN_BROWSER=false` | Don't open the dashboard at startup. |

## For students

Open the address your professor gives you.
- The editor starts with a working bug. Errors are underlined as you type, with a hint for each one.
- **Practice game** runs your bug against an example bug right on the page.
- **Submit my bug** sends it to the class game. Submit again any time; your newest one is used.

## For developers

```sh
./gradlew build                 # compile and run all tests (Java 21 toolchain)
./gradlew :server:run           # run the server from source
./gradlew :server:distZip       # build server/build/distributions/bugsworld.zip

# watch sample bugs fight in the terminal
./gradlew -q :engine:simulate --args="samples/hunter.bl samples/wanderer.bl --show 100"
```

- [docs/PLAN.md](docs/PLAN.md): architecture, decisions and roadmap
- [docs/reference-behavior.md](docs/reference-behavior.md): the BugsWorld rules we match

The original OSU jars go in `lib/` (not committed). Only the reference tests and `tools/oracle/` use them.
