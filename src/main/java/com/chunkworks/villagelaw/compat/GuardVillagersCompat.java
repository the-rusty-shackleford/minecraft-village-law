/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.neoforged.fml.ModList;
import tallestegg.guardvillagers.common.entities.Guard;

/** The one thing of Guard Villagers this mod reads by type: a guard's own gossip, which it keeps
 * apart from the villagers' and attacks on. Every other touch goes through the
 * {@code villagelaw:officers} tag. Load this class only through {@link #gossips}'s guard: it names
 * Guard Villagers' classes, which are absent when the mod is. */
public final class GuardVillagersCompat {
    private GuardVillagersCompat() {}
    /** Whether Guard Villagers is loaded; read once. */
    public static final boolean LOADED = ModList.get().isLoaded("guardvillagers");

    /** effects: the entity's gossip when it is one of Guard Villagers' guards, else null. */
    public static GossipContainer gossips(Entity entity) { return LOADED ? Guards.gossips(entity) : null; }

    /** Holds the references to Guard Villagers' classes, resolved only when the mod is loaded. */
    private static final class Guards {
        static GossipContainer gossips(Entity entity) { return entity instanceof Guard guard ? guard.getGossips() : null; }
    }
}
