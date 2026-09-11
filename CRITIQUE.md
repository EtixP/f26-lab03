# RoomReserve Critique

---

## Milestone 1: The design as it is

### Data model

There is no `Booking` type. A booking is assembled from two separate map entries in
`InMemoryStore`, and nothing in the code ties them together:

| Concept | How the code represents it |
| --- | --- |
| Room | `String`, never validated |
| Date | `String`, never validated or parsed (`"banana"` is an accepted date) |
| Time | `long` minutes since midnight, in a bare `long[2]` where `[0]` is start and `[1]` is end |
| Booker | `String`, stored in a *different* map |
| Booking | not a type — a `long[2]` in one map plus a `String` in another |

- `InMemoryStore:13` — `Map<String, List<long[]>> slotsByRoomDate`, keyed `"room|date"`.
- `InMemoryStore:14` — `Map<String, String> bookerBySlot`, keyed `"room|date|start|end"`.

**What has to stay in agreement.** Four things, none of them enforced by a type:

1. Every `long[]` in `slotsByRoomDate` must have a matching entry in `bookerBySlot`, built
   from the same room, date, and *both* endpoints. The composite key is rebuilt by string
   concatenation in seven separate places (`InMemoryStore:17,29,34,42,50,58,62`); there is no
   single key-construction method.
2. A booking's **identity is its own time values**. Changing the time changes the key, so a
   move cannot be an update — it must be a delete plus an insert (`RequestHandler:98-99`).
3. `slot[0] < slot[1]` — held only by whichever caller happened to check.
4. Intervals within one list must not overlap. This is the invariant below.

The `"|"` separator is not escaped, so the key space is ambiguous. Verified: booking room
`"A|2026-09-11"` on date `"X"` is retrievable as room `"A"` on date `"2026-09-11|X"`.

### Operations

All four take and return `String`, all on `RequestHandler`:

| Operation | In | Out |
| --- | --- | --- |
| `createBooking(room, date, start, end, user)` `:12` | 5 strings, times `"HH:MM"` | `"OK: ..."` or `"ERROR: ..."` |
| `cancelBooking(room, date, start, end)` `:45` | 4 strings | `"OK: ..."` or `"ERROR: ..."` |
| `rescheduleBooking(room, date, oldStart, oldEnd, newStart, newEnd)` `:67` | 6 strings | `"OK: ..."` or `"ERROR: ..."` |
| `listBookings(room, date)` `:103` | 2 strings | one multi-line `String` |

Callers cannot distinguish success from failure except by inspecting the response text, which
is what the whole test suite does: all 12 assertions in `RequestHandlerTest` are `assertTrue`
over a string, five of the six tests matching with `startsWith("OK"/"ERROR")` and
`listShowsTheBookings:49` with `contains`. There is no status, no exception, and no type that
says whether the operation happened.

### Structure

```
ReservationApp  ──►  RequestHandler  ──►  InMemoryStore          BookingPolicy
   (:9)                  (:10, `new`)                          (referenced by nothing)
```

- **`RequestHandler`** (128 lines) owns *five* jobs: string parsing (`:13-25`, `:46-58`,
  `:69-88` — the same parse block written out three times), time formatting (`minutesToText:123`),
  **the overlap rule** (`:31-36`), orchestration of the two-step reschedule (`:98-99`), and
  response formatting.
- **`InMemoryStore`** owns two maps and an exact-endpoint de-duplication check (`:23-27`).
  `slotsFor:49` returns the live internal `ArrayList` (`return slots;` at `:54`), not a copy.
- **`BookingPolicy`** owns three correctly-written rules that **never execute**.
- **`ReservationApp`** runs a fixed script.

`RequestHandler:10` constructs `new InMemoryStore()` directly, so there is no seam to
substitute a different store.

**Who holds a reference to whom:** `ReservationApp` → `RequestHandler` → `InMemoryStore`.
Nothing holds a reference to `BookingPolicy`. `grep -rn "BookingPolicy" .` matches one Java
file — its own declaration — and three lines of `DESIGN.md`.

### The no-double-booking invariant

`DESIGN.md:51` says "Every booking request is validated by `BookingPolicy` before it reaches
storage." That is false. `BookingPolicy` is never instantiated and `validate` is never called.

Three places contain overlap-shaped logic. **Exactly one of them runs.**

| # | Location | What it actually checks | Runs? |
| --- | --- | --- | --- |
| 1 | `RequestHandler.createBooking:31-36` | true overlap, `start < slot[1] && slot[0] < end` | **yes — the only real enforcement** |
| 2 | `InMemoryStore.addSlot:23-27` | `slot[0]==start && slot[1]==end` — *exact endpoint equality*, not overlap | yes, but it is a de-dup guard, not the invariant |
| 3 | `BookingPolicy.validate:29-33` | true overlap, plus business hours and max length | **never — dead code** |
| 4 | `RequestHandler.rescheduleBooking:67-101` | **nothing** | — |

So the invariant is enforced on the create path only, by a copy of the rule inlined in the
handler. Business hours (`08:00–20:00`) and max length (4 hours) are enforced **nowhere**:
`createBooking(room, date, "03:00", "04:00", user)` returns `OK`, and so does a 12-hour
`08:00–20:00` booking.

### Trace: one reschedule request, entry point to storage

`ReservationApp:21` → `rescheduleBooking("WEH-5302", "2026-09-11", "10:00", "11:30", "13:00", "14:30")`

1. `RequestHandler:69-76` — split all four time strings on `":"`, confirm each yields 2 parts.
2. `RequestHandler:81-88` — parse to minutes: old `= 600, 690`; new `= 780, 870`. No range
   check, so `"99:98"` and `"-5:00"` would parse and be accepted.
3. `RequestHandler:89-91` — reject only if `newEnd <= newStart`. It is not. **No rule check of
   any kind happens here or anywhere below.**
4. `RequestHandler:93` → `InMemoryStore.bookerFor:58` — `bookerBySlot.get("WEH-5302|2026-09-11|600|690")`
   → `"bo"`. This lookup is the *only* thing standing between the request and the store, and it
   is an existence check, not a rule.
5. `RequestHandler:98` → `InMemoryStore.removeSlot:33-47` — scans the list at key
   `"WEH-5302|2026-09-11"` for exact endpoints `600,690`, removes it from the list (`:41`) and
   removes the booker key (`:42`), returns `true`. **The return value is discarded.**
6. `RequestHandler:99` → `InMemoryStore.addSlot:16-31` — scans for an *exact* `{780, 870}`
   match (`:23-27`), finds none, appends `new long[]{780, 870}` (`:28`) and puts
   `"WEH-5302|2026-09-11|780|870" -> "bo"` (`:29`), returns `true`. **The return value is
   discarded.**
7. `RequestHandler:100` — returns `"OK: moved ..."`, unconditionally.

The new interval is never compared against any other booking. The demo script hides this,
because `13:00–14:30` happens to land in free time.

---

## Milestone 2: Two design problems

### Problem 1

**The problem.** Misplaced responsibility. `BookingPolicy` is named in `DESIGN.md` as the
owner of all three rules, but the only rule that runs is a copy of the overlap test inlined
into `RequestHandler.createBooking`. The rules live on the request path, not in the rule class,
so each entry point enforces whatever its author remembered to write — and the reschedule
author wrote nothing.

**Where in the code.** `RequestHandler.createBooking:30-36` holds the inlined overlap loop.
`RequestHandler.rescheduleBooking:67-101` holds no rule check at all. `BookingPolicy.validate:19`
is unreachable; `grep -rn "BookingPolicy" .` finds no caller.

**What it makes expensive.** Two things are already broken today, both verified by running
the code:

- **The invariant is violated through `rescheduleBooking`.** Book `09:00–10:00` for amal and
  `13:00–14:00` for bo, then move bo to `09:30–10:30`. Response: `OK: moved`.
  `listBookings` then reports both `09:00 to 10:00 (amal)` and `09:30 to 10:30 (bo)` — a
  double-booked room, in the exact system whose one job is preventing that. The same path also
  accepts `02:00–07:00`, which breaks business hours and max length at once.
- **Reschedule can silently destroy a booking.** `RequestHandler:98-99` removes then adds and
  discards both return values. Move bo's `11:00–12:00` onto amal's existing `09:00–10:00`:
  `removeSlot` deletes bo, `addSlot` hits the exact-duplicate guard at `InMemoryStore:23-27`
  and returns `false` without adding, and `:100` still returns `OK: moved`. bo's booking is
  gone and bo was told it moved.

The next planned change makes this concrete: `DESIGN.md` says per-building business hours are
"the change we expect next," and justifies the current design because there is "a single place
to edit when the rules change." A developer edits `BookingPolicy`, the tests stay green, and
**nothing changes at runtime** — because business hours are not enforced anywhere now.

### Problem 2

**The problem.** Representational gap. The domain has bookings, rooms, dates, time ranges,
and users. The code has `long[2]`, `String`, and pipe-joined map keys. Nothing names a
booking, so a booking's identity *is* its start and end time.

**Where in the code.** `InMemoryStore:13-14` — the split `slotsByRoomDate` /
`bookerBySlot` maps; the composite key rebuilt inline at `InMemoryStore:17,29,34,42,50,58,62`;
`RequestHandler:32` reading `slot[0]` and `slot[1]` positionally.

**What it makes expensive.**

- **It is the structural reason Problem 1's data loss exists.** Because identity is the time
  itself, a move cannot be an update — `RequestHandler:98-99` must delete and re-insert, and a
  two-step mutation with no transaction can half-succeed. With a stable booking id, reschedule
  would be one field assignment and could not lose the row.
- **The two maps drift apart.** They are kept in sync only by convention. Verified: obtain the
  live list from `slotsFor` (see below), clear it, and `bookingCount()` reports 0 while
  `bookerFor(room, date, 540, 600)` still returns `"amal"`.
- **Two planned features have nowhere to go.** `DESIGN.md` lists recurring bookings and
  bookings that cross midnight. A `long[2]` under a single-date key cannot express either: a
  booking ending at 01:00 the next day has `end < start`, which inverts the overlap test at
  `RequestHandler:32`, and it belongs to two day-keys at once. Adding either feature means
  changing the shape of the array and every one of the seven inline key constructions.
- **Garbage is accepted today.** Verified: `"banana"` is a valid date, `"99:98"` is a valid
  time, `"-5:00"` is a valid time, `"2026-9-11"` and `"2026-09-11"` are different days, and a
  room id containing `"|"` collides with another room's key.

---

## Milestone 3: Two alternative decompositions

Both alternatives assume the same first move — `RequestHandler` stops calling `new
InMemoryStore()` at `:10` and receives its collaborators — but they split responsibility
differently after that.

### Alternative A

**The decomposition.** Give the domain types and put each rule on the type that owns the data
it needs.

- `TimeRange` — a value type over start/end, owning `overlaps(other)`, `duration()`, and
  `isWithin(open, close)`. The overlap test lives here, in one place, instead of being copied
  into `RequestHandler:32` and `BookingPolicy:46`.
- `Booking` — an immutable record of `BookingId`, `RoomId`, `LocalDate`, `TimeRange`, and
  booker. `BookingId` is generated at creation and is **independent of the time**, so a move is
  `booking.withRange(newRange)`, not a delete plus an insert.
- `BookingPolicy` — takes a `Booking` and the day's other bookings, returns a violation or
  nothing. Same three rules, now actually reachable, and gains a `BusinessHours` parameter so
  the per-building change is a constructor argument.
- `BookingRepository` — an interface keyed by `BookingId`, returning defensive copies.
  `InMemoryRepository` implements it; a SQL one can sit beside it.
- `BookingService` — the four operations, each of which must pass the policy before it writes.
- `RequestHandler` — shrinks to parsing `"HH:MM"` into a `TimeRange` at the edge and formatting
  the result. The triplicated parse block at `:13-25`, `:46-58`, `:69-88` collapses to one.

**One tradeoff.** This is a rewrite, not a repair: four classes become about nine, and the six
existing tests and `ReservationApp` all drive the system through `RequestHandler`'s string
surface, so that surface has to stay bug-for-bug compatible while everything behind it is
rebuilt — including the error strings the tests prefix-match on. It also spends the thing
`DESIGN.md` explicitly optimized for: the overlap check stops being "a single comparison" on
primitives and becomes calls through value objects, and the store stops being inspectable as
raw numbers in a debugger. For a prototype whose stated point is "the request path, not
durability," that is a lot of scaffolding to carry before the first real user.

### Alternative B

**The decomposition.** Leave the data shape alone. Close the boundary and make one component
the only writer.

- `BookingStore` becomes an **interface**. `slotsFor` returns an unmodifiable copy instead of
  the live list it hands out at `InMemoryStore:54`, and it gains one new operation,
  `replaceSlot(room, date, old, new, user)`, which removes and adds under a single call so a
  move cannot half-succeed. `InMemoryStore` implements it; a `SqlStore` can be added without
  touching anything above.
- `BookingService` becomes the only class that touches a store. It owns create, cancel,
  reschedule, and list, and every mutation routes through one private `checkedWrite` that calls
  `BookingPolicy.validate` first. Reschedule calls `replaceSlot`, and validates the new interval
  against the day's bookings *minus the one being moved* — otherwise shifting `09:00–10:00` to
  `09:30–10:30` would collide with itself.
- `BookingPolicy` is unchanged. Its logic is already written and already correct; it is simply
  called.
- `RequestHandler` keeps string parsing and formatting, loses the inlined overlap loop at
  `:31-36`, and takes a `BookingService` through its constructor.

**One tradeoff.** The representational gap survives untouched. `long[2]` and `"room|date"` keys
stay, so both of the planned features that depend on richer types — recurring bookings and
bookings that cross midnight — are exactly as blocked afterwards as before, and the unescaped
`"|"` collision is still there. "The service is the only writer" is also a convention rather
than something the compiler checks: `InMemoryStore` is still a public class anyone can
instantiate, and the next person to add a store method can leak a live list again. B buys
enforcement without buying vocabulary, so whoever needs the vocabulary pays for it later, on a
deadline, on top of a service layer built around the old shape.

### Preference

**B, under current conditions.** Every defect I can actually demonstrate — the reschedule
overlap, the silently destroyed booking, business hours and max length never running — is a
routing failure, not a vocabulary failure. Not one of them needs a new type to fix; they need
the existing `BookingPolicy` to be called and the two-step write to become one. B is mostly
wiring up code that has already been written and is already correct, and it does not disturb
the string surface the six tests and `ReservationApp` depend on. It also unblocks two of the
four planned items on its own: the store interface is what a database-backed store needs, and a
policy that actually runs is what per-building hours needs. A fixes the same bugs but only
after a rewrite that must hold the string surface still while it moves everything behind it,
and a prototype with two live correctness bugs should stop the bleeding first.

**What would flip me to A:** if recurring bookings or cross-midnight bookings are next on the
schedule rather than someday items. Those are precisely where `long[2]` under a single-date key
stops being able to express the problem at all — an interval ending after midnight has
`end < start`, which silently inverts the overlap comparison rather than failing loudly, and it
belongs to two day-keys at once. Building B first and then A means designing the service layer
around the flat shape and then redesigning it around `Booking` and `TimeRange`, so the
`replaceSlot` signature and the policy's interval arguments get written twice. If either
feature is committed for this semester, I would pay for the types now and get the enforcement
fix as a consequence of doing A properly.
