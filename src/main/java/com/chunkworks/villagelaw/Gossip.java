/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw;

import com.chunkworks.villagelaw.compat.GuardVillagersCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import java.util.UUID;

/** Forgiving a player who paid (Rusty's call): every villager and guard in the village's vicinity
 * forgets the bad things it has heard about that player, and only that player. Thief's crimes and
 * a hurt villager are remembered as major and minor negative gossip; Guard Villagers' guards keep
 * gossip of their own, picked up from the villagers, and attack on it. */
final class Gossip {
    private Gossip() {}

    /** effects: removes the player's major and minor negative gossip from every villager and guard
     * in the entry's vicinity; returns how many held some. */
    static int forgive(ServerLevel level, Docket.Entry entry, UUID player) {
        if (!level.dimension().equals(entry.dimension())) return 0;
        int forgiven = 0;
        for (var mob : level.getEntitiesOfClass(Mob.class, entry.box())) {
            GossipContainer gossips = mob instanceof Villager villager ? villager.getGossips() : GuardVillagersCompat.gossips(mob);
            if (gossips != null && forget(gossips, player)) forgiven++;
        }
        return forgiven;
    }
    /** effects: removes the player's negative gossip from the container; returns whether it held some. */
    private static boolean forget(GossipContainer gossips, UUID player) {
        boolean held = gossips.getReputation(player, t -> t == GossipType.MAJOR_NEGATIVE || t == GossipType.MINOR_NEGATIVE) != 0;
        gossips.remove(player, GossipType.MAJOR_NEGATIVE);
        gossips.remove(player, GossipType.MINOR_NEGATIVE);
        return held;
    }
}
