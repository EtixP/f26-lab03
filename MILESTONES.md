# Lab 3 — Recitation Prep Sheet

Talking notes for the TA check-off. The graded writeup is [`CRITIQUE.md`](CRITIQUE.md);
this file is the version to actually speak from. Everything below was verified by running
the code, not inferred from reading it.

---

## The one-sentence version

**`BookingPolicy` is dead code.** `DESIGN.md:51` says every request is validated by it before
reaching storage; it is never instantiated and never called. The only rule that runs anywhere
is a copy of the overlap test inlined in `RequestHandler.createBooking:31-36`, so business
hours and max length are enforced nowhere, and `rescheduleBooking` enforces nothing at all.

Proof to say out loud: `grep -rn "BookingPolicy" .` returns one Java line — the class's own
declaration — and three lines of `DESIGN.md`.

---

## Milestone 1 — the map

### Data model (30 seconds)

No `Booking` type exists. A booking is two map entries that nothing ties together:

- `InMemoryStore:13` — `Map<String, List<long[]>> slotsByRoomDate`, key `"room|date"`
- `InMemoryStore:14` — `Map<String, String> bookerBySlot`, key `"room|date|start|end"`

Room, date, and user are `String`. Time is `long` minutes in a bare `long[2]`.

**Must stay in agreement:** every `long[]` needs a matching booker entry under a key built from
*both* endpoints; the composite key is rebuilt inline in seven places
(`InMemoryStore:17,29,34,42,50,58,62`); `slot[0] < slot[1]`; and no two intervals in one list
may overlap.

**The line that matters:** a booking's identity *is* its time. Change the time and you change
the key — which is why a move has to be a delete plus an insert.

### Operations (15 seconds)

Four methods on `RequestHandler`, all `String` in and `String` out: `createBooking:12`,
`cancelBooking:45`, `rescheduleBooking:67`, `listBookings:103`. Success and failure are
distinguished only by inspecting the response text — all 12 assertions in `RequestHandlerTest`
are `assertTrue` over a string (five tests use `startsWith`, `listShowsTheBookings:49` uses
`contains`). No status, no exception, no type.

### Structure (15 seconds)

```
ReservationApp ──► RequestHandler ──► InMemoryStore        BookingPolicy
                     (:10, `new`)                        ◄── nobody
```

- `RequestHandler` owns five jobs: parsing (`:13-25`, `:46-58`, `:69-88` — the same block three
  times), formatting (`minutesToText:123`), the overlap rule (`:31-36`), the two-step reschedule
  (`:98-99`), and response strings.
- `InMemoryStore` owns two maps; `slotsFor:54` hands back the **live** `ArrayList`.
- `BookingPolicy` owns three correct rules that never execute.
- `RequestHandler:10` does `new InMemoryStore()` — no seam for the planned database store.

### ⭐ "Where is no-double-booking enforced?" — the question they said they'd ask

**Answer: in exactly one place, and it is not the class the design document names.**

| # | Location | What it really checks | Runs? |
| --- | --- | --- | --- |
| 1 | `RequestHandler.createBooking:31-36` | true overlap: `start < slot[1] && slot[0] < end` | **yes — the only enforcement** |
| 2 | `InMemoryStore.addSlot:23-27` | `slot[0]==start && slot[1]==end` — exact endpoints, **not** overlap | yes, but it is a de-dup guard |
| 3 | `BookingPolicy.validate:29-33` | true overlap + hours + max length | **no — dead code** |
| 4 | `RequestHandler.rescheduleBooking` | nothing | — |

If pushed on #2: it compares endpoints for *equality*, so it stops an identical booking and
lets a `09:30–10:30` overlapping a `09:00–10:00` straight through. It is not the invariant.

### The reschedule trace — `ReservationApp:21`

`rescheduleBooking("WEH-5302", "2026-09-11", "10:00", "11:30", "13:00", "14:30")`

| Step | Code | What happens |
| --- | --- | --- |
| 1 | `RequestHandler:69-76` | split four time strings on `":"`, check 2 parts each |
| 2 | `:81-88` | parse to minutes — old `600,690`; new `780,870`. No range check |
| 3 | `:89-91` | reject only if `newEnd <= newStart`. **No rule check here or below** |
| 4 | `:93` → `InMemoryStore.bookerFor:58` | `bookerBySlot.get("WEH-5302\|2026-09-11\|600\|690")` → `"bo"`. An existence check, not a rule |
| 5 | `:98` → `removeSlot:33-47` | scans for exact `600,690`, removes from list `:41` and booker map `:42`, returns `true` — **discarded** |
| 6 | `:99` → `addSlot:16-31` | scans for exact `{780,870}`, none, appends `:28`, puts booker `:29`, returns `true` — **discarded** |
| 7 | `:100` | returns `"OK: moved ..."`, unconditionally |

**Punchline:** the new interval is never compared against any other booking. The demo script
hides it because `13:00–14:30` happens to be free.

---

## Milestone 2 — the two problems

### Problem 1 — Misplaced responsibility

**Name it:** the rules live on the request path, not in the rule class. `BookingPolicy` is
documented as the owner; the only rule that runs is a copy inlined into
`RequestHandler.createBooking:30-36`. Each entry point therefore enforces whatever its author
remembered to write, and the reschedule author wrote nothing (`:67-101`).

**Two things already broken today:**

1. **The invariant is violated through reschedule.** amal has `09:00–10:00`, bo has
   `13:00–14:00`; move bo to `09:30–10:30` → `OK: moved`, and the room is double-booked.
2. **Reschedule silently destroys bookings.** `:98-99` removes then adds and discards both
   return values. Move bo's `11:00–12:00` onto amal's existing `09:00–10:00`: `removeSlot`
   deletes bo, `addSlot` hits the exact-duplicate guard and returns `false` without adding,
   and `:100` still says `OK: moved`. **bo's booking is gone and bo was told it moved.**

**The expensive future change:** `DESIGN.md` says per-building business hours is "the change we
expect next," and defends the current design as having "a single place to edit when the rules
change." Someone edits `BookingPolicy`, the suite stays green, and **nothing changes at
runtime** — because business hours are not enforced now.

### Problem 2 — Representational gap

**Name it:** the domain has bookings, rooms, dates, time ranges and users; the code has
`long[2]`, `String`, and pipe-joined keys. Nothing names a booking, so identity *is* the time.

**Where:** `InMemoryStore:13-14` (the split maps), the key rebuilt at
`InMemoryStore:17,29,34,42,50,58,62`, `RequestHandler:32` reading `slot[0]`/`slot[1]`
positionally.

**Costs:**

- **It is why Problem 1's data loss is possible.** Identity = time, so a move *cannot* be an
  update — `:98-99` must delete and re-insert, and a two-step write with no transaction can
  half-succeed. A stable `BookingId` makes reschedule one assignment that cannot lose the row.
- **The two maps drift.** Verified: take the live list from `slotsFor`, clear it →
  `bookingCount()` is 0 but `bookerFor(room, date, 540, 600)` still returns `"amal"`.
- **Two planned features are blocked.** Cross-midnight bookings have `end < start`, which
  silently *inverts* the overlap test at `:32`, and they belong to two day-keys at once.
- **Garbage is accepted today:** `"banana"` is a valid date, `"99:98"` and `"-5:00"` are valid
  times, `"2026-9-11"` ≠ `"2026-09-11"`, and a room id containing `"|"` collides with another
  room's key.

---

## Milestone 3 — the two proposals

### A — give the domain types

`TimeRange` (owns `overlaps`, `duration`, `isWithin` — one copy of the overlap test instead of
two) · `Booking` as an immutable record with a `BookingId` **independent of the time**, so a
move is `withRange(newRange)` · `BookingPolicy` takes a booking plus the day's others and gains
a `BusinessHours` parameter · `BookingRepository` interface keyed by id, returning copies ·
`BookingService` for the four operations · `RequestHandler` shrinks to parse-and-format, and
the triplicated parse block collapses to one.

**Tradeoff:** a rewrite, not a repair. Four classes become ~nine, and the six tests plus
`ReservationApp` all drive the system through the string surface, so that surface must stay
bug-for-bug compatible — error strings included — while everything behind it moves. It also
spends what `DESIGN.md` optimized for: overlap stops being one comparison on primitives.

### B — close the boundary, one writer

`BookingStore` becomes an **interface**; `slotsFor` returns an unmodifiable copy instead of the
live list at `:54`, and gains `replaceSlot(...)` so a move is one call that cannot half-succeed
· `BookingService` is the only class touching a store, and every mutation goes through one
`checkedWrite` that calls `BookingPolicy.validate` first · `BookingPolicy` **unchanged** — it is
already correct, it is simply called · `RequestHandler` loses the inlined overlap loop and takes
the service by constructor.

*Detail worth mentioning if they probe:* reschedule must validate against the day's bookings
**minus the one being moved**, or shifting `09:00–10:00` to `09:30–10:30` collides with itself.

**Tradeoff:** the representational gap survives. `long[2]` and `"room|date"` keys stay, so
recurring and cross-midnight bookings are exactly as blocked as before, and the `"|"` collision
remains. "One writer" is a convention the compiler cannot check — `InMemoryStore` is still
public, and the next store method can leak a live list again.

### Defence — **B**

Every defect I can actually demonstrate is a **routing** failure, not a vocabulary failure.
None of them needs a new type; they need the existing policy to be called and the two-step
write to become one. B is largely wiring up code that is already written and already correct,
and it leaves the string surface the tests depend on untouched. It also unblocks two of the
four planned items by itself: the store interface is what the database store needs, and a
policy that runs is what per-building hours needs. A fixes the same bugs, but only after a
rewrite — and a prototype with two live correctness bugs should stop the bleeding first.

**What flips me to A:** if recurring or cross-midnight bookings are actually next rather than
someday. That is where `long[2]` under a single-date key stops being able to express the
problem at all. Doing B then A means writing the service layer twice — `replaceSlot`'s
signature and the policy's interval arguments both get redesigned — so if either feature is
committed this semester, I would pay for the types now and get the enforcement fix as a
by-product.

---

## Live demos (verified — copy/paste on a laptop)

Run `mvn compile` once first. Each block is a `jshell` script; end with `/exit`.

**1 — reschedule double-books the room**

```
jshell --class-path target/classes -q
import edu.cmu.cs214.roomreserve.*;
var h = new RequestHandler();
h.createBooking("WEH-5302","2026-09-11","09:00","10:00","amal");
h.createBooking("WEH-5302","2026-09-11","13:00","14:00","bo");
h.rescheduleBooking("WEH-5302","2026-09-11","13:00","14:00","09:30","10:30");
System.out.println(h.listBookings("WEH-5302","2026-09-11"));
```

> ```
> bookings for WEH-5302 on 2026-09-11:
>   09:00 to 10:00 (amal)
>   09:30 to 10:30 (bo)
> ```

**2 — reschedule silently destroys a booking**

```
var h = new RequestHandler();
h.createBooking("WEH-5302","2026-09-11","09:00","10:00","amal");
h.createBooking("WEH-5302","2026-09-11","11:00","12:00","bo");
System.out.println(h.rescheduleBooking("WEH-5302","2026-09-11","11:00","12:00","09:00","10:00"));
System.out.println(h.listBookings("WEH-5302","2026-09-11"));
```

> ```
> OK: moved WEH-5302 on 2026-09-11 to 09:00 to 10:00
> bookings for WEH-5302 on 2026-09-11:
>   09:00 to 10:00 (amal)
> ```
>
> bo is gone. bo was told the move succeeded.

**3 — business hours and max length are enforced nowhere**

```
var h = new RequestHandler();
System.out.println(h.createBooking("WEH-5302","2026-09-11","03:00","04:00","amal"));
System.out.println(h.createBooking("WEH-5302","2026-09-11","08:00","20:00","bo"));
```

> ```
> OK: booked WEH-5302 on 2026-09-11 03:00 to 04:00 for amal
> OK: booked WEH-5302 on 2026-09-11 08:00 to 20:00 for bo
> ```
>
> A 3 a.m. booking and a 12-hour booking, against documented rules of 08:00–20:00 and 4 hours.

---

## Line-number cheat sheet

| Claim | Cite |
| --- | --- |
| Store built with `new`, no seam | `RequestHandler:10` |
| The one real overlap check | `RequestHandler:31-36` |
| Parse block, written three times | `RequestHandler:13-25`, `:46-58`, `:69-88` |
| Reschedule: no rule check | `RequestHandler:89-91` straight to `:93` |
| Two-step write, both returns discarded | `RequestHandler:98-99` |
| Unconditional `"OK: moved"` | `RequestHandler:100` |
| The two unsynchronized maps | `InMemoryStore:13-14` |
| Exact-endpoint de-dup, not overlap | `InMemoryStore:23-27` |
| Live internal list handed out | `InMemoryStore:54` |
| Composite key rebuilt inline ×7 | `InMemoryStore:17,29,34,42,50,58,62` |
| Correct rules that never run | `BookingPolicy:19-35` |
| The false claim in the design doc | `DESIGN.md:51` |
| Rules-in-one-place justification | `DESIGN.md` §"Rules and where they are enforced" |
| Planned: recurring, cross-midnight, DB, per-building hours | `DESIGN.md` §"Planned next" |

---

## Anticipated follow-ups

**"Isn't `addSlot`'s check the invariant?"** No — `InMemoryStore:23-27` tests
`slot[0]==start && slot[1]==end`. Equality, not overlap. It stops an identical booking and
passes a partial overlap straight through. Demo 1 proves it.

**"The tests are green, so what's broken?"** The suite never asks the questions the design
document answers. Six tests: none covers business hours, none covers max length, and
`rescheduleMovesABooking:60-67` moves `09:00–10:00` to `14:00–15:00` in an otherwise **empty**
room, so no conflict is possible. Green because the gap is untested, not because it's absent.

**"Why not just add the overlap check to `rescheduleBooking`?"** That is a local fix and it
makes the design worse — a third copy of the same rule, in a third place, still leaving
`BookingPolicy` dead. It also would not fix the discarded `addSlot` return at `:99`, so the
silent data loss survives. Milestone 3 asks for a decomposition, and the point is that no
entry point should be *able* to write without passing the policy.

**"Which problem would you fix first?"** The discarded return value at `:99` — it destroys user
data and reports success. One line to stop the bleeding, then Alternative B to make it
structurally impossible.

**"Is a dead class really a design problem, or just a bug?"** Both, and the design part is the
interesting half: the design document, the class name, and the component table all say the
rules live in `BookingPolicy`, so a maintainer edits there and their change has no effect. The
gap between the documented structure and the executed structure is what makes the next change
expensive — that is the design problem, not the missing `if`.

**"What did you use?"** Claude Code with Claude Opus 5, to read the code and check my claims
by running them; also noted in the README. Every consequence above I ran, rather than reasoned
about — the reschedule overlap, the destroyed booking, the desynced maps, and the key collision.

---

## Before recitation

- [ ] `mvn test` green (it is)
- [ ] Commit and push `CRITIQUE.md` + this file — TAs read the **fork**
- [ ] README line naming tools/models (done)
- [ ] AWS Academy Learner Lab → Modules → Launch AWS Academy Learner Lab → **Start Lab** →
      wait for the green dot → **End Lab**. Separate AWS Academy Canvas, from the invitation
      email — not CMU Canvas.
