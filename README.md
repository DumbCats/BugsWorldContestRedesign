# BugsWorldContestRedesign
Created to revamp the outdated BugsWorld contest for Ohio State University's Software 2 course sequence.

A web app that replaces the Swing/coLinux BugsWorld lab: students submit BL programs in the browser and get useful compile errors, and the professor runs and projects the simulation.

## Getting started

```sh
./gradlew build          # compile + test (uses a JDK 21 toolchain)
./gradlew :server:run    # prints the URL for students and the instructor token

# watch sample bugs fight in the terminal
./gradlew -q :engine:simulate --args="samples/hunter.bl samples/wanderer.bl --show 100"
```

- [docs/PLAN.md](docs/PLAN.md): architecture, decisions and roadmap
- [docs/reference-behavior.md](docs/reference-behavior.md): the BugsWorld rules we match

The original OSU jars go in `lib/` (not committed). They are only needed by `tools/oracle/`.
