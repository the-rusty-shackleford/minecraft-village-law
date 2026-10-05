/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.api;

import com.chunkworks.villagedeed.api.Village;
import com.chunkworks.villagedeed.api.VillageId;
import com.chunkworks.villagelaw.Docket;
import com.chunkworks.villagelaw.Reports;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** Read-only questions about the law's cases, for a mod whose crime needs a remedy when its case
 * settles (see {@link CaseSettledEvent}). Serfdom asks right after it commits a capture's crime,
 * so the captive is owed to the case that crime opened or joined, and to no other. Server side
 * only. */
public final class Cases {
    private Cases() {}

    /** effects: the village the law places {@code player}'s crimes in where they stand now (the
     * village there, else the nearest within 16 blocks, as for every crime the law hears of), when
     * they have an open case with it; empty when there is no such village or no open case. A case
     * is open from the crime an officer saw until it is paid. */
    public static Optional<VillageId> openHere(ServerPlayer player) {
        var docket = Docket.get(player.server);
        return Reports.villageAround(player.serverLevel(), player.blockPosition()).map(Village::id)
                .filter(id -> docket.get(player.getUUID(), id).isPresent());
    }
}
