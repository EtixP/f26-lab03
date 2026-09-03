# RoomReserve: Design

Version 0.4. Maintained by the RoomReserve team.

## What it does

RoomReserve books meeting rooms. A caller names a room, a date, a
start time, and an end time, and gets back a one-line response. The service also
cancels a booking, moves an existing booking to a new time, and lists everything
booked for a room on a given day.

The prototype is deliberately small. Requests arrive as strings and responses leave as
strings, so the same operations work from the demo app today and from an HTTP front end
later with no change to the core.

## Components

| Component | Responsibility |
| --- | --- |
| `ReservationApp` | Demo entry point. Runs a short script of requests and prints the responses. |
| `RequestHandler` | The public surface. Accepts request strings, converts times, calls the policy, and formats responses. |
| `BookingPolicy` | The rules. Business hours, maximum booking length, and no double booking. |
| `InMemoryStore` | Storage. Keeps the day's slots and who booked them. |

Requests move in one direction through the stack:

```
        caller (ReservationApp, or a future HTTP layer)
                        |
                        v
             +----------------------+
             |    RequestHandler    |   parse request strings, format responses
             +----------------------+
                        |
                        v
             +----------------------+
             |    BookingPolicy     |   business hours, max length, overlap
             +----------------------+
                        |
                        v
             +----------------------+
             |    InMemoryStore     |   slots per room and day
             +----------------------+
```

Nothing skips a level. Storage is only ever reached from the handler, and only after
the policy has approved the request.

## Rules and where they are enforced

Every booking request is validated by `BookingPolicy` before it reaches storage.
The policy enforces three rules:

1. Bookings fall inside business hours, 08:00 to 20:00.
2. No booking runs longer than 4 hours.
3. A booking may not overlap another booking for the same room on the same day.

A request that breaks a rule is rejected with a message naming the rule, and the store
is never touched. Keeping all three rules in one class means there is a single place to
read when someone asks what the service allows, and a single place to edit when the
rules change (a per-building closing time is the change we expect next).

## Data model

Times are integer minutes since midnight, so `09:30` is stored as `570`. Minutes
compare and subtract cleanly, which keeps the overlap test to a single comparison and
avoids date-library overhead in the request path.

Bookings are stored per room and per day for fast lookup. The key is the room and the
date, and the value is the list of booked intervals for exactly that room on exactly
that day:

```
"WEH-5302|2026-09-11" -> [ {540, 600}, {600, 690} ]
```

Answering "what is booked here today" and checking a new request against the existing
ones are both a single map lookup followed by a walk over one short list. No booking
for another room or another day is ever scanned. The lists stay small (a day has room
for a couple dozen bookings), so this stays fast as the number of rooms grows.

## Operations

| Operation | Behavior |
| --- | --- |
| `createBooking(room, date, start, end, user)` | Books the interval, or returns the rule it broke. |
| `cancelBooking(room, date, start, end)` | Removes a booking identified by its exact interval. |
| `rescheduleBooking(room, date, oldStart, oldEnd, newStart, newEnd)` | Moves an existing booking to a new interval, keeping the original booker. |
| `listBookings(room, date)` | Returns the day's bookings for one room, in the order they were made. |

Times arrive as `"HH:MM"` and are converted at the edge, so the rest of the code works
in minutes only.

## Persistence

Storage is in memory and lives as long as the process. Nothing is written to disk, and
a restart clears every booking. That is acceptable for the prototype. The point of this
version is the request path, not durability.

## Planned next

- Recurring bookings (weekly seminars are the driver).
- Bookings that cross midnight.
- A database-backed store so bookings survive a restart.
- Per-building business hours instead of one 08:00 to 20:00 window.
