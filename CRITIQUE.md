# RoomReserve Critique

Fill in each section. One section per milestone. Keep it short and specific. Point at
files and methods, not adjectives.

---

## Milestone 1: The design as it is

Describe the system as the code actually builds it.

**Data model.** What is a booking, in the code? What types hold it, and what has to stay
in agreement for a booking to make sense?

**Operations.** What can a caller do, and what goes in and out?

**Structure.** What classes exist, what does each own, and who holds a reference to whom?

**The no-double-booking invariant.** Where is it enforced? Name every place a check
happens, say what each one actually checks, and trace one reschedule request through the
code from the entry point to storage.

---

## Milestone 2: Two design problems

Two problems. For each one, fill in all three parts.

### Problem 1

**The problem.** Name it, using the vocabulary from lecture (milestone 2 in the
handout names the three).

**Where in the code.** File and method.

**What it makes expensive.** A concrete future change, or something that already goes
wrong today. What breaks first?

### Problem 2

**The problem.**

**Where in the code.**

**What it makes expensive.**

---

## Milestone 3: Two alternative decompositions

Two different ways to carve up this system. A different split of responsibility, not a
list of local code fixes. Read the handout's appendix before writing this section.

### Alternative A

**The decomposition.** What are the pieces, what does each own, and where do the rules
live?

**One tradeoff.** Something this option actually costs. "No real downside" is not a
tradeoff.

### Alternative B

**The decomposition.**

**One tradeoff.**

### Preference

Which one, and under what conditions? Say what the choice depends on, and what would
make you pick the other one instead.
