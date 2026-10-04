/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.gametest;

import io.github.mortuusars.thief.neoforge.api.event.CrimeCommitedEvent;
import io.github.mortuusars.thief.world.Crime;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Every crime Thief punished since the last clear, as Thief reports it after the punishment:
 * the tests' view of whether a deed exempted a player. */
@EventBusSubscriber(modid = "villagelaw_gametest")
public final class Crimes {
    public record Seen(UUID criminal, Crime crime, int witnesses) {}
    private static final List<Seen> SEEN = new ArrayList<>();
    private Crimes() {}
    @SubscribeEvent public static void onCrime(CrimeCommitedEvent event) {
        synchronized (SEEN) { SEEN.add(new Seen(event.criminal.getUUID(), event.crime, event.witnesses.size())); }
    }
    public static void clear() { synchronized (SEEN) { SEEN.clear(); } }
    /** effects: the crimes punished on that player, oldest first. */
    public static List<Seen> by(UUID criminal) {
        synchronized (SEEN) {
            var out = new ArrayList<Seen>();
            for (var s : SEEN) if (s.criminal().equals(criminal)) out.add(s);
            return out;
        }
    }
}
