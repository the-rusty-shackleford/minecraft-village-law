# Village Law

**1.0.0, built and verified 2026-10-04; not yet released.** Rusty's word on 2026-10-04 was to ship
it together with Ranged Weapons Mod 2.12.0 as pack 1.72.0 once it is done. Gate:
[release verification](../devtools/verification/release-1.0.0.md) (38 JUnit, 11 GameTests,
8 mutations caught, 19 booth checks, a guard's chase measured at a sprint).

Minecraft 1.21.1, NeoForge 21.1.248, Java 21. `com.chunkworks.villagelaw`, AGPL-3.0-or-later,
headers "Rusty Shackleford and nfx". Requires Thief 1.2.x and Village Deed 2.2+. Guard Villagers
is optional: its guards are the officers. Nests Carried.

Skyrim's guards over Thief's ([D-0001](decisions/D-0001.md)). A crime an officer sees opens a
case. The officer runs over and offers pay or leave. Paying forgives the payer. Leaving gives a
minute to get out, then a two-day banishment and a debt. Staying brings the village down on the
player. Guards and golems are held back while a case is peaceful, and guards chase at a sprint.

## Shape

- `src/domain` (JDK only): `Case` (the state machine; pure transitions on an event and the game
  time), `Severity`, `Fines`, `Payment` (Village Deed's rule), `Countdown` (m:ss), `Chase` (the
  speed derivation), `Area`.
- `src/main`:
  - `VillageLaw` (entry), `LawConfig`, and `Docket` (overworld saved data `villagelaw_docket`).
  - `Reports` (Thief's event becomes a case; the mixin's question) and `mixin/CrimeMixin`.
  - `Law.move`: every state change and its reactions.
  - `Officers` (speed and summons goal on join, the hold-back, rally and stand-down) and
    `SummonGoal`.
  - `Summons` (assignments, payloads, answers, payment, using an officer), `Patrol` (once a
    second, plus death, logout and login) and `Gossip` (forgiving the payer).
  - `api/CaseSettledEvent`, `compat/GuardVillagersCompat` (a guard's gossip), and
    `client/SummonsScreen`, `CountdownHud` and `ClientSetup`.
- `src/gametest`: Village Deed's hut fixture (`Huts`, now with guards and golems), `Crimes`,
  `Settlements`, `Wallets` (a bagged fake player, the running track), `LawGameTests` and
  `LawBooth`.

## Gotchas met

- **GameTest scheduling:** a task scheduled from inside a scheduled task (`onEachTick` or
  `runAfterDelay` called from a `runAfterDelay` callback) changes the framework's tick map
  while it is iterating, and the server crashes with an NPE in `Object2LongOpenHashMap`. Build
  each test as one `startSequence()` chain, with `thenExecuteFor` for "never during" windows,
  and register `onEachTick` only at the top of the test.
- **A guard's chase sets off up to 20 ticks late.** `MeleeAttackGoal.canUse` looks for a target
  only once every 20 ticks. Time a run from the guard's first moving tick, not from the test's
  start. A window starting at tick 20 measured 94%.
- **The booth's counter can read 1:00 a second after Leave:** the client's clock trails the
  server's.
- Thief's darkness modifier floors visibility at 0.25, so with the default 32-block notice
  distance a guard within 8 blocks in line of sight always witnesses, day or night.

## Next

- Release with Ranged Weapons Mod 2.12.0 as pack 1.72.0 (needs a GitHub repo:
  `the-rusty-shackleford/minecraft-village-law`).
- After the deploy, read a guard's speed base on the box with `/attribute` (0.4507), and watch
  the log for the first case.
- Serfdom (part C of the plan) listens to `CaseSettledEvent`.
