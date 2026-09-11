# Lab 3 Starter: RoomReserve

RoomReserve is a small room-reservation service. Callers create, cancel, reschedule,
and list bookings for a room on a day. It ships with a design document, about 300 lines
of code, a green test suite, and CI.

You write no code in this lab. You read the system and critique its design.

**Read `DESIGN.md` first, then read the code.** Your critique goes in `CRITIQUE.md`.

## Build and test

```
mvn test
```

Everything is green. You can also run the demo script:

```
mvn compile
java -cp target/classes edu.cmu.cs214.roomreserve.ReservationApp
```

## Where things are

- Design document: `DESIGN.md`
- Code: `src/main/java/edu/cmu/cs214/roomreserve/`
- Tests: `src/test/java/edu/cmu/cs214/roomreserve/RequestHandlerTest.java`
- Your writeup: `CRITIQUE.md`
- Setup: `SETUP.md`

See the Lab 3 handout on the course page for the three milestones you show a TA.

## Tools and models used

Claude Code (CLI, VS Code extension) with **Claude Opus 5** — used to read `DESIGN.md` and the
four source files, and to verify each claim in the critique by compiling and running the code
rather than by inspection alone. Every consequence cited in `CRITIQUE.md` was reproduced with a
throwaway `jshell`/`javac` harness that was not committed; the reproduction steps are in
`MILESTONES.md`. No production code was modified — this lab is a written critique.

## My writeup

- `CRITIQUE.md` — the graded deliverable, three milestones.
- `MILESTONES.md` — recitation prep: talking notes, line-number cheat sheet, verified live
  demos, and anticipated TA follow-ups.
