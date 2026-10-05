/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.gametest;

import com.chunkworks.villagedeed.Claims;
import com.chunkworks.villagedeed.api.Village;
import com.chunkworks.villagedeed.api.VillageProviders;
import com.chunkworks.villagedeed.domain.Deed;
import com.chunkworks.villagelaw.Docket;
import com.chunkworks.villagelaw.Law;
import com.chunkworks.villagelaw.Officers;
import com.chunkworks.villagelaw.Patrol;
import com.chunkworks.villagelaw.Reports;
import com.chunkworks.villagelaw.Summons;
import com.chunkworks.villagelaw.api.CaseSettledEvent;
import com.chunkworks.villagelaw.api.Cases;
import com.chunkworks.villagelaw.domain.Case;
import com.chunkworks.villagelaw.domain.Chase;
import com.chunkworks.villagelaw.domain.Severity;
import io.github.mortuusars.thief.world.Crime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Real-server partitions, with Thief, Village Deed and Guard Villagers loaded and the hut
 * registered as a protected village.
 * <ul>
 * <li>who saw the crime: an officer (a case, a summons, no attack), only a villager (Thief's
 * reputation hit and no case), nobody who matters because the criminal owns the village (no crime
 * at all);</li>
 * <li>its grade: medium (fined 8), heavy (fined 15, and a grudge of -150 that Guard Villagers would
 * attack on);</li>
 * <li>the answer: pay (from a bag, short of the fine), leave (out in time, back early, back after
 * the banishment; stay past the minute);</li>
 * <li>the player's own violence: hitting a guard;</li>
 * <li>speeds: a guard's chase and its run with a summons, each measured against a sprint.</li>
 * </ul>
 * Time-bound steps hand {@link Patrol#patrol} the hour they test, so two days pass in a tick. */
@GameTestHolder("villagelaw") @PrefixGameTestTemplate(false)
public final class LawGameTests {
    private static final Logger LOG = LoggerFactory.getLogger("Village Law gametests");
    private static final int SETTLE = 5;
    /** A sprint's speed may differ from the measured chase by this share. */
    private static final double SPRINT_TOLERANCE = 0.05;

    private static void chest(GameTestHelper h, BlockPos at) { h.getLevel().setBlock(at, Blocks.CHEST.defaultBlockState(), 3); }
    private static Village village(GameTestHelper h, Huts.Hut hut) { return VillageProviders.at(h.getLevel(), hut.centre()).orElseThrow(); }
    private static Docket.Entry entry(ServerPlayer player, Village village) { return Docket.get(player.server).get(player.getUUID(), village.id()).orElse(null); }
    private static long now(GameTestHelper h) { return h.getLevel().getServer().overworld().getGameTime(); }
    /** effects: a case for one crime of that grade the officer saw, served: the officer is in front
     * of the player with the choice on screen. */
    private static Docket.Entry summoned(ServerPlayer player, Village village, Severity severity, Mob officer) {
        var e = Reports.report(player, village, severity, officer);
        return Law.move(player, e, e.lawCase().served());
    }
    private static void grudge(GossipContainer gossips, UUID player) {
        gossips.add(player, GossipType.MAJOR_NEGATIVE, 20);
        gossips.add(player, GossipType.MINOR_NEGATIVE, 50);
    }

    /** A theft an officer sees opens a WANTED case fined 8 and hands the summons to that officer,
     * who runs the seven blocks to the thief and serves it within reach, never targeting them;
     * a second theft while summoned adds up. Without the mixin, Thief's own handler sets the guard on
     * the thief and the every-tick check fails. */
    @GameTest(template = "arena", timeoutTicks = 300) public void aGuardWhoSeesATheftRunsOverAndSummons(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        chest(h, hut.at(2, 1, 1));
        chest(h, hut.at(1, 1, 2));
        var guard = Huts.guard(h, hut.at(6, 1, 6));
        var thief = Huts.player(h, hut.at(1, 1, 1));
        var v = village(h, hut);
        h.onEachTick(() -> h.assertFalse(guard.getTarget() == thief, "the guard never targets the thief"));
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            h.assertTrue(guard.distanceTo(thief) > 6.0, "the guard stands beyond Thief's always-notice distance: " + guard.distanceTo(thief));
            h.assertTrue(thief.gameMode.destroyBlock(hut.at(2, 1, 1)), "the chest broke");
            var seen = Crimes.by(thief.getUUID());
            h.assertTrue(seen.size() == 1 && seen.get(0).crime() == Crime.MEDIUM, "one medium crime, got " + seen);
            var e = entry(thief, v);
            h.assertTrue(e != null && e.lawCase().equals(Case.opened(Severity.MEDIUM, 8)), "a WANTED case fined 8: " + e);
            h.assertTrue(Summons.officerFor(thief.getUUID(), v.id()).equals(Optional.of(guard.getUUID())), "the guard who saw it carries the summons");
            h.assertTrue(Cases.openHere(thief).equals(Optional.of(v.id())), "the API names the village of the open case: " + Cases.openHere(thief));
        }).thenWaitUntil(() -> {
            var now = entry(thief, v);
            h.assertTrue(now != null && now.lawCase().state() == Case.State.SUMMONED, "served: " + now);
            h.assertTrue(guard.distanceTo(thief) <= 3.5, "within reach: " + guard.distanceTo(thief));
        }).thenExecute(() -> {
            h.assertTrue(thief.gameMode.destroyBlock(hut.at(1, 1, 2)), "the second chest broke");
            var more = entry(thief, v).lawCase();
            h.assertTrue(more.state() == Case.State.SUMMONED && more.fine() == 16 && more.crimes() == 2, "a second theft adds to the fine and keeps the summons: " + more);
        }).thenSucceed();
    }

    /** A heavy crime (a bell) leaves the villager who saw it at -150 with the thief, under Guard
     * Villagers' -100; for a hundred ticks while the case is open neither the guard nor the golem
     * targets the thief. Without the hold-back both would. */
    @GameTest(template = "arena", timeoutTicks = 300) public void aHeavyCrimesGrudgeSetsNoGuardOnWhileTheCaseIsOpen(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        h.getLevel().setBlock(hut.at(2, 1, 1), Blocks.BELL.defaultBlockState(), 3);
        var villager = Huts.villager(h, hut.at(1, 1, 5), VillagerProfession.NONE, 1);
        var guard = Huts.guard(h, hut.at(6, 1, 6));
        var golem = Huts.golem(h, hut.at(6, 1, 1));
        var thief = Huts.player(h, hut.at(1, 1, 1));
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            h.assertTrue(thief.gameMode.destroyBlock(hut.at(2, 1, 1)), "the bell broke");
            h.assertTrue(villager.getPlayerReputation(thief) <= -100, "the villager's grudge is at Guard Villagers' line: " + villager.getPlayerReputation(thief));
            var e = entry(thief, village(h, hut));
            h.assertTrue(e != null && e.lawCase().worst() == Severity.HEAVY && e.lawCase().fine() == 15, "a heavy case fined 15: " + e);
        }).thenExecuteFor(100, () -> {
            h.assertFalse(guard.getTarget() == thief, "the guard is held back");
            h.assertFalse(golem.getTarget() == thief, "the golem is held back");
        }).thenSucceed();
    }

    /** Paying out of a worn bag: the fine is taken (three emeralds and a block for 8, four back),
     * the case closes with a PAID settlement, and the villager and the guard forget this payer's
     * grudge and keep another player's. A payer short of the fine pays nothing and stays summoned. */
    @GameTest(template = "arena", timeoutTicks = 200) public void payingFromABagClosesTheCaseAndForgivesOnlyThePayer(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        var villager = Huts.villager(h, hut.at(3, 1, 3), VillagerProfession.FARMER, 1);
        var guard = Huts.guard(h, hut.at(5, 1, 5));
        var payer = Wallets.bagged(h, hut.at(2, 1, 2), "fine-payer", new ItemStack(Items.EMERALD, 3), new ItemStack(Items.EMERALD_BLOCK, 1), new ItemStack(Items.BREAD, 4));
        var poor = Wallets.bagged(h, hut.at(2, 1, 4), "fine-short", new ItemStack(Items.EMERALD, 5));
        var other = UUID.randomUUID();
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            var v = village(h, hut);
            for (var who : List.of(payer.getUUID(), other)) { grudge(villager.getGossips(), who); grudge(guard.getGossips(), who); }
            var e = summoned(payer, v, Severity.MEDIUM, guard);
            h.assertTrue(e.lawCase().state() == Case.State.SUMMONED && Wallets.worth(payer) == 12, "summoned, carrying 12 in the bag: " + e);
            h.assertTrue(Cases.openHere(payer).equals(Optional.of(v.id())), "open while summoned");
            Summons.answer(payer, new Summons.Answer(v.id().toString(), Summons.Choice.PAY));
            h.assertTrue(entry(payer, v) == null, "the case is closed");
            h.assertTrue(Cases.openHere(payer).isEmpty(), "and the API says no case is open");
            h.assertTrue(Wallets.worth(payer) == 4, "eight taken, four back as change: " + Wallets.worth(payer));
            h.assertTrue(Settlements.of(payer.getUUID()).equals(List.of(new Settlements.Seen(payer.getUUID(), v.id().toString(), CaseSettledEvent.Settlement.PAID, 8))), "settled PAID: " + Settlements.of(payer.getUUID()));
            h.assertTrue(villager.getPlayerReputation(payer) == 0 && guard.getPlayerReputation(payer) == 0, "the payer is forgiven: " + villager.getPlayerReputation(payer) + ", " + guard.getPlayerReputation(payer));
            h.assertTrue(villager.getGossips().getReputation(other, t -> true) == -150 && guard.getGossips().getReputation(other, t -> true) == -150, "the other player's grudge stands");
            var s = summoned(poor, v, Severity.MEDIUM, guard);
            Summons.answer(poor, new Summons.Answer(v.id().toString(), Summons.Choice.PAY));
            h.assertTrue(entry(poor, v).lawCase().equals(s.lawCase()) && Wallets.worth(poor) == 5, "short of 8 with 5: nothing taken, still summoned");
        }).thenSucceed();
    }

    /** Leave, out in time: banished (FLED). Back inside within the two days: the guard attacks.
     * Out again: it stands down. Two days on: a debt; back inside, the guard is held back despite
     * the villager's grudge, a click on it is consumed and opens the debt, and paying clears it. */
    @GameTest(template = "arena", timeoutTicks = 300) public void leavingThenComingBackEarlyAndAfterTheBanishment(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        var villager = Huts.villager(h, hut.at(1, 1, 5), VillagerProfession.NONE, 1);
        var guard = Huts.guard(h, hut.at(5, 1, 5));
        var player = Huts.player(h, hut.at(2, 1, 2), "leaver", new ItemStack(Items.EMERALD, 20));
        BlockPos inside = hut.at(2, 1, 2), far = hut.at(300, 1, 2);
        var v = village(h, hut);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            grudge(villager.getGossips(), player.getUUID());
            summoned(player, v, Severity.MEDIUM, guard);
            Summons.answer(player, new Summons.Answer(v.id().toString(), Summons.Choice.LEAVE));
            long t = now(h);
            h.assertTrue(entry(player, v).lawCase().state() == Case.State.FLEEING && entry(player, v).lawCase().until() == t + 1200, "on a minute's clock: " + entry(player, v));
            player.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
            Patrol.patrol(player, t);
            var banished = entry(player, v).lawCase();
            h.assertTrue(banished.state() == Case.State.BANISHED && banished.until() == t + 48000, "out in time: banished for two days: " + banished);
            h.assertTrue(Settlements.of(player.getUUID()).equals(List.of(new Settlements.Seen(player.getUUID(), v.id().toString(), CaseSettledEvent.Settlement.FLED, 8))), "settled FLED");
            player.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
            Patrol.patrol(player, t + 20);
            h.assertTrue(guard.getTarget() == player, "back within the two days: the guard attacks");
            player.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
            Patrol.patrol(player, t + 40);
            h.assertTrue(guard.getTarget() == null, "out again: it stands down");
            Patrol.patrol(player, t + 48000);
            h.assertTrue(entry(player, v).lawCase().state() == Case.State.DEBT, "two days on: the fine is a debt");
            player.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
        }).thenExecuteFor(60, () -> h.assertFalse(guard.getTarget() == player, "the debtor is held back from")).thenExecute(() -> {
            h.assertTrue(villager.getPlayerReputation(player) <= -100, "the grudge that would set the guard on stands: " + villager.getPlayerReputation(player));
            h.assertTrue(player.interactOn(guard, InteractionHand.MAIN_HAND).consumesAction(), "a click on the guard is the law's");
            var notice = Summons.use(player, guard);
            h.assertTrue(notice.isPresent() && notice.get().kind() == Summons.Kind.DEBT && notice.get().fine() == 8 && notice.get().carrying() == 20, "the debt screen: " + notice);
            Summons.answer(player, new Summons.Answer(v.id().toString(), Summons.Choice.PAY));
            h.assertTrue(entry(player, v) == null && Wallets.worth(player) == 12, "paid: the case is closed");
            h.assertTrue(villager.getPlayerReputation(player) == 0, "and forgiven");
            h.assertTrue(Settlements.of(player.getUUID()).size() == 2 && Settlements.of(player.getUUID()).get(1).settlement() == CaseSettledEvent.Settlement.PAID, "settled FLED, then PAID");
        }).thenSucceed();
    }

    /** Leave, and stay: a tick before the minute is up nothing happens; at it the guard and the golem
     * attack; killed, the player is banished from that moment and the guard stands down. */
    @GameTest(template = "arena", timeoutTicks = 200) public void stayingPastTheMinuteBringsTheGuardsAndGolems(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        var guard = Huts.guard(h, hut.at(5, 1, 5));
        var golem = Huts.golem(h, hut.at(6, 1, 1));
        var player = Huts.player(h, hut.at(2, 1, 2), "stayer");
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            var v = village(h, hut);
            summoned(player, v, Severity.MEDIUM, guard);
            Summons.answer(player, new Summons.Answer(v.id().toString(), Summons.Choice.LEAVE));
            long t = now(h);
            Patrol.patrol(player, t + 1199);
            h.assertTrue(entry(player, v).lawCase().state() == Case.State.FLEEING && guard.getTarget() == null && golem.getTarget() == null, "a tick before the minute is up: nothing");
            Patrol.patrol(player, t + 1200);
            h.assertTrue(entry(player, v).lawCase().state() == Case.State.HOSTILE, "at the minute: hostile");
            h.assertTrue(guard.getTarget() == player && golem.getTarget() == player, "the guard and the golem attack");
            player.die(h.getLevel().damageSources().mobAttack(guard));
            var after = entry(player, v).lawCase();
            h.assertTrue(after.state() == Case.State.BANISHED && after.until() == now(h) + 48000, "killed: banished from that moment: " + after);
            h.assertTrue(guard.getTarget() == null, "the guard stands down");
        }).thenSucceed();
    }

    /** A summoned player who hits the guard is fought: the hold-back has a grace for violence.
     * Without it the guard's retaliation would be cancelled. */
    @GameTest(template = "arena", timeoutTicks = 200) public void hittingTheGuardMakesItFightBack(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        var guard = Huts.guard(h, hut.at(4, 1, 4));
        var player = Huts.player(h, hut.at(2, 1, 2), "hitter");
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            summoned(player, village(h, hut), Severity.MEDIUM, guard);
            guard.hurt(h.getLevel().damageSources().playerAttack(player), 1.0F);
        }).thenWaitUntil(() -> h.assertTrue(guard.getTarget() == player, "the guard fights back")).thenSucceed();
    }

    /** The owner of a bought village takes from it in front of a guard: no Thief crime, no case,
     * no attack (Village Deed's mixin stops the crime first). A stranger in the same hut gets a case. */
    @GameTest(template = "arena", timeoutTicks = 200) public void aDeedOwnerGetsNoCase(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        chest(h, hut.at(2, 1, 1));
        chest(h, hut.at(2, 1, 3));
        var guard = Huts.guard(h, hut.at(6, 1, 6));
        var owner = Huts.player(h, hut.at(1, 1, 1), "owner");
        var stranger = Huts.player(h, hut.at(1, 1, 3), "stranger");
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            var v = village(h, hut);
            h.assertTrue(Claims.get(h.getLevel()).claim(new Claims.Claim(v.id(), v.name(), Deed.of(owner.getUUID()), Map.of(owner.getUUID(), "owner"), v.centre(), 15, now(h))), "the owner holds the deed");
            h.assertTrue(owner.gameMode.destroyBlock(hut.at(2, 1, 1)), "the owner breaks a chest");
            h.assertTrue(Crimes.by(owner.getUUID()).isEmpty() && entry(owner, v) == null && Cases.openHere(owner).isEmpty(), "no crime and no case");
            h.assertTrue(stranger.gameMode.destroyBlock(hut.at(2, 1, 3)), "the stranger breaks a chest");
            h.assertTrue(entry(stranger, v) != null, "the stranger has a case");
        }).thenExecuteFor(40, () -> h.assertFalse(guard.getTarget() == owner, "the guard leaves the owner be")).thenSucceed();
    }

    /** A theft only a villager saw: Thief's crime with its reputation hit, and no case. */
    @GameTest(template = "arena", timeoutTicks = 200) public void seenOnlyByAVillagerOpensNoCase(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        chest(h, hut.at(2, 1, 1));
        var villager = Huts.villager(h, hut.at(1, 1, 5), VillagerProfession.NONE, 1);
        var thief = Huts.player(h, hut.at(1, 1, 1), "petty-thief");
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            h.assertTrue(thief.gameMode.destroyBlock(hut.at(2, 1, 1)), "the chest broke");
            var seen = Crimes.by(thief.getUUID());
            h.assertTrue(seen.size() == 1 && seen.get(0).crime() == Crime.MEDIUM && seen.get(0).witnesses() == 1, "Thief's medium crime, one witness: " + seen);
            h.assertTrue(villager.getPlayerReputation(thief) == -50, "Thief's reputation hit: " + villager.getPlayerReputation(thief));
            h.assertTrue(entry(thief, village(h, hut)) == null, "no case");
            h.assertTrue(Cases.openHere(thief).isEmpty(), "and the API names none");
        }).thenSucceed();
    }

    /** Ticks of position kept for a timed run. */
    private static final int RUN_TICKS = 80;
    /** effects: the runner's settled speed east, from its x on each tick: over twenty ticks starting
     * twelve after the first tick it moved, when it is within 0.1% of its top speed (each tick closes
     * 45% of the gap). Measured from the start of its run, not of the test: a guard's melee goal
     * looks for its target only once every twenty ticks, so a chase sets off up to a second late. */
    private static double settled(GameTestHelper h, double[] xs) {
        int moved = 1;
        while (moved < xs.length && xs[moved] - xs[moved - 1] < 0.01) moved++;
        h.assertTrue(moved + 32 < xs.length, "the runner set off by tick " + (xs.length - 33) + ", at " + moved);
        return (xs[moved + 32] - xs[moved + 12]) / 20.0;
    }
    private static String deltas(double[] xs) {
        var out = new StringBuilder();
        for (int i = 1; i < xs.length; i++) out.append(String.format(" %.4f", xs[i] - xs[i - 1]));
        return out.toString();
    }

    /** A guard chasing a player over flat stone settles at a sprint's 0.281 blocks a tick, within
     * five percent; its base is the one Village Law set on join. Guard Villagers' own 0.5 would
     * measure 23% fast. */
    @GameTest(template = "arena", timeoutTicks = 200) public void aGuardChasesAtASprint(GameTestHelper h) {
        var track = Wallets.strip(h, 1);
        var guard = Huts.guard(h, track.start());
        var quarry = Huts.player(h, track.at(18), "quarry");
        double[] xs = new double[RUN_TICKS];
        int[] tick = { 0 };
        h.assertTrue(Math.abs(guard.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() - Officers.chaseBase()) < 1e-9, "the chase base, set on join");
        guard.setTarget(quarry);
        h.onEachTick(() -> { if (tick[0] < xs.length) xs[tick[0]++] = guard.getX(); });
        h.startSequence().thenIdle(RUN_TICKS + 2).thenExecute(() -> {
            double speed = settled(h, xs), sprint = Chase.sprint();
            LOG.info("villagelaw chase: {} blocks a tick against a sprint's {} ({}%); each tick:{}", speed, sprint, Math.round(100 * speed / sprint), deltas(xs));
            h.assertTrue(Math.abs(speed / sprint - 1) <= SPRINT_TOLERANCE, "the chase is a sprint: " + speed + " against " + sprint);
        }).thenSucceed();
    }

    /** An officer with a summons runs at the same speed, and serves it on arrival. */
    @GameTest(template = "arena", timeoutTicks = 300) public void aSummonsIsServedAtASprint(GameTestHelper h) {
        var hut = Huts.plant(h);
        var track = Wallets.strip(h, 1);
        var guard = Huts.guard(h, track.start());
        var player = Huts.player(h, track.at(18), "summoned");
        var v = village(h, hut);
        double[] xs = new double[RUN_TICKS];
        int[] tick = { -1 };
        h.onEachTick(() -> { if (tick[0] >= 0 && tick[0] < xs.length) xs[tick[0]++] = guard.getX(); });
        h.startSequence().thenIdle(1).thenExecute(() -> {
            h.assertTrue(Reports.report(player, v, Severity.MEDIUM, guard).lawCase().state() == Case.State.WANTED, "wanted, the summons with the guard");
            tick[0] = 0;
        }).thenIdle(RUN_TICKS + 2).thenExecute(() -> {
            double speed = settled(h, xs), sprint = Chase.sprint();
            LOG.info("villagelaw summons run: {} blocks a tick against a sprint's {} ({}%); each tick:{}", speed, sprint, Math.round(100 * speed / sprint), deltas(xs));
            h.assertTrue(Math.abs(speed / sprint - 1) <= SPRINT_TOLERANCE, "the summons run is a sprint: " + speed + " against " + sprint);
        }).thenWaitUntil(() -> h.assertTrue(entry(player, v).lawCase().state() == Case.State.SUMMONED, "served on arrival")).thenSucceed();
    }

    /** The docket saves every field of an entry and reads it back; a cleared case is not kept. */
    @GameTest(template = "arena", timeoutTicks = 100) public void theDocketKeepsCasesAcrossASave(GameTestHelper h) {
        var hut = Huts.plant(h);
        var player = Huts.player(h, hut.at(2, 1, 2), "saved");
        var v = village(h, hut);
        var original = Docket.Entry.open(player, v, new Case(Case.State.BANISHED, 23, Severity.HEAVY, 2, 123456L));
        var docket = Docket.load(new CompoundTag(), h.getLevel().registryAccess());
        docket.put(original);
        var round = Docket.load(docket.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(round.get(player.getUUID(), v.id()).equals(Optional.of(original)), "read back whole: " + round.of(player.getUUID()));
        round.put(original.with(new Case(Case.State.CLEARED, 23, Severity.HEAVY, 2, 0)));
        h.assertTrue(round.of(player.getUUID()).isEmpty() && !round.hasAny(player.getUUID()), "a cleared case leaves the docket");
        h.succeed();
    }
}
