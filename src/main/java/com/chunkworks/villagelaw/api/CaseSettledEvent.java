/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.api;

import com.chunkworks.villagedeed.api.VillageId;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Posted on the game bus when a player's case with a village is settled: PAID when they paid the
 * fine (on the spot or the debt after a banishment), FLED when they left the village's vicinity
 * owing it, which is the moment their banishment begins. One case can settle twice: FLED, then
 * PAID once the debt is paid. A mod whose crime needs a remedy listens here; Serfdom frees a
 * captured villager and takes the cuffs. Server side only. */
public final class CaseSettledEvent extends Event {
    public enum Settlement { PAID, FLED }
    private final ServerPlayer player;
    private final VillageId village;
    private final Settlement settlement;
    private final int fine;

    public CaseSettledEvent(ServerPlayer player, VillageId village, Settlement settlement, int fine) {
        this.player = player;
        this.village = village;
        this.settlement = settlement;
        this.fine = fine;
    }
    public ServerPlayer player() { return player; }
    public VillageId village() { return village; }
    public Settlement settlement() { return settlement; }
    /** effects: the fine the case came to, paid or owed. */
    public int fine() { return fine; }
}
