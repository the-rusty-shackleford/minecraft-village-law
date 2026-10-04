# Release verification — 1.0.0

2026-10-04. Part B of the approved plan (`~/.claude/plans/the-following-requests-were-witty-church.md`);
D-0001 has the design and Rusty's calls.

The gate was a full `./gradlew clean build` on Xephyr `:7` with software rendering. It passed:

- **38 JUnit tests** on the pure domain: `CaseTest` (15: every state against every event, before,
  at and after a deadline, inside and out, and the rep invariant), `ChaseTest` (6), `PaymentTest`
  (5), `AreaTest`, `CountdownTest` and `FinesTest` (4 each).
- **11 GameTests** on a real server with Thief 1.2.4, Village Deed 2.2.0, Guard Villagers 2.4.11
  and Backpacks+ 0.7.0 loaded:
  - a guard who sees a theft runs seven blocks and serves the summons, never targeting the thief;
    a second theft while summoned adds to the fine;
  - a heavy crime's −150 grudge (asserted at or below Guard Villagers' −100) sets neither the
    guard nor the golem on the thief over 100 ticks;
  - paying from a worn bag takes 8 (three emeralds and a block, four back), closes the case
    (PAID), and forgives the payer only; a payer short of the fine pays nothing;
  - leaving in time is banished (FLED); back early, attacked; out again, stood down; two days
    on, a debt; back inside, held back despite the grudge; a click on the guard is consumed and
    opens the debt; paying clears it;
  - staying: nothing a tick before the minute, the guard and the golem at it; killed, banished
    from that moment;
  - hitting the guard: it fights back;
  - a deed owner commits no crime and gets no case, while a stranger in the same hut does;
  - a theft only a villager saw: Thief's −50, no case;
  - **the measured chase**: 0.280610 blocks a tick against a sprint's 0.280617, settled after the
    guard sets off (its melee goal looks for a target every 20 ticks, so it starts at tick 21);
  - **the measured summons run**: 0.280610, the same speed, and served on arrival;
  - the docket's save and load.
- **19 booth checks**, with 5 photographs judged by eye: the summons (the guard in view above the
  panel), the counter at 1:00 and red at 0:09, the debt with [Pay] greyed and the shortfall in
  red, and the paid line in chat.

## Mutations

Each rule was broken once to prove a test catches it (`scratchpad/mutate.py`, not kept). Every
mutation failed the tests aimed at it:

| Mutation | Caught by |
|---|---|
| M1 the mixin no longer stops Thief's guard attack | the summons test, the heavy-grudge test |
| M2 no hold-back | the heavy-grudge test, the leaving test |
| M3 no grace for violence | the hit-the-guard test |
| M4 paying forgives nobody | the bag test, the leaving test |
| M5 guards keep their saved speed | both speed tests |
| M6 a banished player out again is still chased | the leaving test |
| M7 any witness counts as an officer | the villager-only test |
| M8 hostility rallies nobody | the leaving test, the staying test |

## Artifact

`villagelaw-1.0.0.jar`, 105836 bytes, SHA-1 `88dc3310336efad05a49d3ae65bf52cada4cdbe4`. It nests
Carried 1.0.0 and holds no test classes.

## Not verified

- Real villages on the box: a Guard Villagers guard spotting a crime in a CTOV or Terralith
  village, and the walk through streets and doors. The GameTests use an 8×8 hut on open ground.
- A real client answering over the network beside a running guard. The booth drives the screen
  over the network, but its guard has no AI. The GameTests' mock players have no client.
- Two players with cases in one village at once, and one player with cases in two villages.
