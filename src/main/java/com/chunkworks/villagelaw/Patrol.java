/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw;

import com.chunkworks.villagelaw.domain.Case;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.Comparator;

/** Once a second, for each player online with an open case: is the player inside the village's
 * vicinity, has a deadline passed, does the case need an officer, and is the village hostile? The
 * clock is passed in, so a test can look at a player two days on without waiting for them. Also
 * the player's death, logout and login. */
public final class Patrol {
    private static final int PERIOD = 20;
    private Patrol() {}

    static void onTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % PERIOD != 0) return;
        var docket = Docket.get(server);
        long now = server.overworld().getGameTime();
        for (var id : docket.players()) {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) patrol(player, now);
        }
    }

    /** effects: moves each of the player's cases on for where they stand at {@code now}; a WANTED
     * case without a live officer on the way gets the nearest one in its village; the enforcers of
     * a village hostile to the player turn on them, and stand down once a banished player is out
     * again: they attack a banished player only while the player is inside. */
    public static void patrol(ServerPlayer player, long now) {
        for (var entry : Docket.get(player.server).of(player.getUUID())) {
            boolean inside = player.isAlive() && entry.covers(player);
            var after = Law.move(player, entry, entry.lawCase().ticked(now, inside, LawConfig.banishTicks()));
            var c = after.lawCase();
            if (!c.open()) continue;
            if (c.state() == Case.State.WANTED) dispatch(player, after);
            if (c.hostile(inside)) Officers.rally(player, after);
            else if (c.state() == Case.State.BANISHED && !Officers.aggressive(player)) Officers.standDown(player, after);
        }
    }
    /** effects: when no live officer in the player's level carries the WANTED case's summons, hands
     * it to the officer nearest the player in the village's vicinity, if there is one. */
    static void dispatch(ServerPlayer player, Docket.Entry entry) {
        var held = Summons.officerFor(player.getUUID(), entry.village()).map(id -> player.serverLevel().getEntity(id));
        if (held.isPresent() && held.get() instanceof PathfinderMob officer && officer.isAlive()) return;
        Officers.enforcers(player, entry, 0).stream().filter(m -> m instanceof PathfinderMob && Officers.isOfficer(m))
                .min(Comparator.comparingDouble(m -> m.distanceToSqr(player)))
                .ifPresent(officer -> Summons.assign((Mob) officer, player, entry.village()));
    }

    /** effects: a player killed while their village was hostile is banished from that moment. */
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.server.overworld().getGameTime();
        for (var entry : Docket.get(player.server).of(player.getUUID())) Law.move(player, entry, entry.lawCase().died(now, LawConfig.banishTicks()));
    }
    /** effects: a summons left unanswered on screen at logout lapses, so an officer comes again; the
     * session's summonses and grace are forgotten. */
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (var entry : Docket.get(player.server).of(player.getUUID())) Law.move(player, entry, entry.lawCase().lapsed());
        Summons.forget(player.getUUID());
        Officers.forget(player.getUUID());
    }
    /** effects: a player logging in while leaving a village gets its countdown back. */
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (var entry : Docket.get(player.server).of(player.getUUID()))
            if (entry.lawCase().state() == Case.State.FLEEING) Summons.send(player, new Summons.Deadline(entry.villageName(), entry.lawCase().until()));
    }
}
