# Village Law

A [Thief](https://modrinth.com/mod/thief) and [Guard Villagers](https://modrinth.com/mod/guard-villagers)
addon for **Minecraft 1.21.1 / NeoForge 21.1.248**. Village guards keep the law the way
Skyrim's do. A guard who sees you commit a crime doesn't attack. He runs over and stops you with
a choice: **pay the fine**, or **leave within 60 seconds and stay away for two days**.

By Rusty Shackleford and nfx, AGPL-3.0-or-later.

## What happens

1. **A crime.** Thief decides what a crime is and who saw it: opening a village chest, breaking
   its bell, killing its animals. When a crime at or over Thief's `guard_attack_threshold`
   (MEDIUM by default) is seen by a guard, a case opens between you and that village. A guard is
   anything in the `villagelaw:officers` tag; Guard Villagers' guard is the one listed.
   - The fine goes by the crime's grade: 3 for light, 8 for medium, 15 for heavy.
   - More crimes before the case is settled add to the fine.
   - A crime only villagers saw costs Thief's reputation hit and nothing more, as before.
2. **The summons.** The guard who saw it runs to you at a sprinting player's speed. Within three
   blocks he stops you, and a screen opens: the village, the crime, the fine, **[Pay 15]** and
   **[Leave]**. When you can't pay, [Pay] is greyed out and the screen says what you carry.
   Escape counts as Leave, and so does walking away before he reaches you. While the case is
   open and peaceful, the village's guards and golems will not attack you, whatever the
   villagers think of you.
3. **Pay.** The fine is taken from everything you carry, bags included. Emeralds go first, then
   blocks of emerald (nine each), and you get the change. The case closes, and every villager
   and guard in the village forgets the bad things it heard about *you*. What it thinks of other
   players stays.
4. **Leave.** A counter appears at the top of the screen: "Leave Taiga Village 0:42". Get out of
   the village's vicinity (its bounds plus 32 blocks) before it runs out, and you are banished
   for two Minecraft days (40 minutes), still owing the fine.
5. **Stay**, and at 0:00 every guard and golem of the village attacks until you leave or die.
   The banishment runs from that moment.
6. **Banished.** Come back within the two days and the guards attack while you are inside.
   Once the two days are up they leave you be. Using (right-clicking) any of the village's
   guards opens your debt: pay it, and the matter is closed and forgiven, as in step 3.

Hitting a guard is still a fight. The hold-back has a five-second exception: if you have hurt a
villager, a guard or a golem in the last five seconds, they may fight you whatever your case.

Deed owners and the players they trust (Village Deed), a Hero of the Village, and creative players
commit no Thief crime, so they never get a case.

## How it works

- **Which village.** Village Deed's village protocol (`VillageProviders`) answers which village a
  crime is in and gives it an id, a name and bounds. A case keeps the name and bounds it opened
  with, so the patrol can tell whether you're inside without loading the village's chunks.
- **Thief's own guard attack** is switched off by one mixin, `CrimeMixin` on
  `Crime.shouldGuardsAttack`, and only where the law takes the crime: a player, in a village, seen
  by an officer. Everywhere else Thief's attack stands.
- **The hold-back.** NeoForge fires `LivingChangeTargetEvent` from every `Mob.setTarget`, and the
  mod cancels a guard's or golem's targeting of a player whose case with that village is open
  and not hostile. Without it, Guard Villagers' own rule would set guards and golems on anyone a
  nearby villager rates at −100 or below, and one witnessed heavy crime is −150.
- **Guard speed.** A mob's flat-ground speed goes with the square of its movement-speed base times
  its goal's modifier. Guard Villagers' 0.5 base under its 0.8 melee goal runs 23% faster than a
  sprinting player. Each guard's base is set as it loads to the value whose chase equals the
  configured share of a sprint: 0.4507 at the default 1.0. Bases are saved with each mob, so
  guards already in the world are corrected too. `domain/Chase` has the derivation.
- **The patrol** runs once a second for each online player with an open case. It checks whether
  they are inside, whether a deadline has passed, and whether the village is hostile.

The state machine is `domain/Case`. `Law.move` is the one place a case changes state, and every
reaction to a change happens there: messages, the countdown, the stand-down, the forgiving and
the settlement event.

## Server config

`config/villagelaw-server.toml`:

| Key | Default | Meaning |
|---|---|---|
| `fines.light` / `medium` / `heavy` | 3 / 8 / 15 | fines in emeralds by Thief's grade |
| `law.leave_seconds` | 60 | time to get out after choosing Leave |
| `law.banishment_days` | 2.0 | banishment, in Minecraft days of 20 minutes |
| `law.vicinity` | 32 | how far past a village's bounds its law reaches |
| `guards.chase_speed` | 1.0 | a guard's chase relative to a sprint (Guard Villagers' own is about 1.23) |

Which crimes open a case is Thief's `guard_attack_threshold` in `thief-server.toml`.

## For other mods

`com.chunkworks.villagelaw.api.CaseSettledEvent` is posted on the game bus when a case settles:
`PAID`, or `FLED` at the moment a banishment begins. One case can settle `FLED` and then `PAID`.
Serfdom uses it to free a captured villager.

## Diagnosing

- Every state change is logged: `Village Law: <player>'s case with <village> (<id>): WANTED ->
  SUMMONED, 8 emeralds`, and each crime and payment has its own line.
- Open cases live in the overworld's saved data, `data/villagelaw_docket.dat`: player, village
  id, name, dimension, bounds, state, fine, worst grade, crimes, deadline.
- The officer carrying a summons is a running server's memory. After a restart the patrol hands
  each waiting case to the nearest guard again.
- A player logging out with the summons on screen goes back to waiting: a guard comes again
  when they return.
- To check a guard's speed: `/attribute <guard> minecraft:generic.movement_speed base get` reads
  0.4507 at the default `chase_speed`.

## Network

One required channel, version "1": the summons and debt notice and the countdown (to the client),
and the answer (to the server). A client without the mod is refused at login.

## Building

`./gradlew clean build` runs the JUnit domain tests, the GameTests on a real server with Thief,
Village Deed, Guard Villagers and Backpacks+ loaded, and the photo booth. It needs:

- Carried in the local Maven repository (`./gradlew publishToMavenLocal` in `minecraft-carried`);
- `../minecraft-village-deed/build/libs/villagedeed-2.2.0.jar` (built at its `v2.2.0` tag);
- `../minecraft-backpacks-plus/build/libs/backpacksplus-0.7.0.jar`;
- a display for the booth: Xephyr `:7` with software rendering, or the GPU through
  `tools/booth/run_iconified.sh`.

The jar nests Carried. Thief, Village Deed and Guard Villagers come from the pack.
