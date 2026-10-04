/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.gametest;

import com.chunkworks.villagelaw.api.CaseSettledEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Every settlement Village Law posted, as a listener on the game bus sees it: the tests' view of
 * the event Serfdom will listen to. */
@EventBusSubscriber(modid = "villagelaw_gametest")
public final class Settlements {
    public record Seen(UUID player, String village, CaseSettledEvent.Settlement settlement, int fine) {}
    private static final List<Seen> SEEN = new ArrayList<>();
    private Settlements() {}
    @SubscribeEvent public static void onSettled(CaseSettledEvent event) {
        synchronized (SEEN) { SEEN.add(new Seen(event.player().getUUID(), event.village().toString(), event.settlement(), event.fine())); }
    }
    /** effects: the settlements of that player's cases, oldest first. */
    public static List<Seen> of(UUID player) {
        synchronized (SEEN) { return SEEN.stream().filter(s -> s.player().equals(player)).toList(); }
    }
}
