# BugsWorldContestRedesign
Created to revamp the outdated BugsWorld contest for Ohio State University's Software 2 course sequence.

BugsWorld is a browser-based simulator for checking students' BL programs.
- Students write their bug on their own, then upload the `.bl` file in class. The compiler checks it at once, with clear error messages.
- The professor runs everyone's programs together in one simulation, shown on the projector, to see how well each one works.

You don't need a VPN, ssh, X11 or anything installed on student computers.

## Running a lab (professor)

1. **Install Java 21 or newer** on the computer that will run the simulation, if it doesn't have it. Any free build works, for example from [adoptium.net](https://adoptium.net).
2. **Unzip `bugsworld.zip`** and run `bugsworld/bin/bugsworld.bat` on Windows or `bugsworld/bin/bugsworld` on macOS and Linux.
   - Your browser opens the dashboard, already signed in.
   - Keep the window that opened.
3. **Tell students the address** shown at the top of the dashboard (for example `192.168.1.133:7070`).
   - Their laptops must be on the same network as yours.
   - The projector view shows the address too, until the simulation starts.
4. **When submissions are in:**
   - Each one shows its compiler warnings (⚠). **View** shows the code with the warnings marked.
   - Press **Load everyone's newest program**.
   - Press **Open projector view**, then **Start**. Use <kbd>Space</kbd> to pause and <kbd>→</kbd> to step one tick.
5. **When a run finishes:**
   - The projector lists every program's final bug count.
   - **Rerun** repeats the exact same run.
   - **New simulation settings** changes the world size, bug counts, seed or tick limit.
   - Earlier runs are listed under **Past runs**.

Submissions and results are saved in a `BugsWorld` folder in your home folder, so restarting loses nothing. Optional settings (environment variables):

| Variable | Effect |
|---|---|
| `BUGSWORLD_CLASS_CODE` | Students must enter this code to submit. This keeps out strangers on a shared network. |
| `BUGSWORLD_INSTRUCTOR_TOKEN` | Sets the dashboard password. Otherwise one is generated and kept in the data folder. |
| `PORT` | The web port (default 7070). |
| `BUGSWORLD_DATA_DIR` | Where to keep submissions and results. |
| `BUGSWORLD_OPEN_BROWSER=false` | Don't open the dashboard at startup. |

## For students

Write your program in any editor and save it as a `.bl` file. In class, open the address your professor gives you, enter your name and choose your file.
- It's checked right away. Errors and warnings are marked in your code, with a hint for each one.
- Once it compiles, press **Submit**.
- To change it, upload a new version; your newest one is used.

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
