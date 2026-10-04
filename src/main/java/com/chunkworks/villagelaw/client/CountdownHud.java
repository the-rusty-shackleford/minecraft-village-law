/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.client;

import com.chunkworks.villagelaw.Summons;
import com.chunkworks.villagelaw.domain.Countdown;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The counter a player leaving a village watches: "Leave Taiga Village 0:42" at the top of the
 * screen, above where boss bars start, gold and then red for the last ten seconds. One line per
 * village being left, the soonest first; a line stays at 0:00 until the server takes it down. */
public final class CountdownHud {
    private static final Map<String, Long> ENDS = new LinkedHashMap<>();
    private static final int GOLD = 0xFFD37F, RED = 0xFF5555;
    private CountdownHud() {}

    /** effects: puts up or takes down a village's countdown. */
    public static void accept(Summons.Deadline deadline) {
        if (deadline.endsAt() == 0) ENDS.remove(deadline.villageName()); else ENDS.put(deadline.villageName(), deadline.endsAt());
    }
    /** effects: forgets every countdown: the player left the world. */
    public static void clear() { ENDS.clear(); }
    /** effects: the counters on show, soonest first, as their text. */
    public static List<String> lines() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return List.of();
        long now = mc.level.getGameTime();
        return ENDS.entrySet().stream().sorted(Map.Entry.comparingByValue())
                .map(e -> Component.translatable("hud.villagelaw.leave", e.getKey(), Countdown.format(e.getValue() - now)).getString()).toList();
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (ENDS.isEmpty() || mc.level == null || mc.options.hideGui) return;
        long now = mc.level.getGameTime();
        int y = 2;
        for (var e : ENDS.entrySet().stream().sorted(Map.Entry.comparingByValue()).toList()) {
            long left = e.getValue() - now;
            var text = Component.translatable("hud.villagelaw.leave", e.getKey(), Countdown.format(left));
            int w = mc.font.width(text), x = (g.guiWidth() - w) / 2;
            g.fill(x - 3, y - 1, x + w + 3, y + 9, 0x90000000);
            g.drawString(mc.font, text, x, y, Countdown.secondsLeft(left) <= 10 ? RED : GOLD, true);
            y += 11;
        }
    }
}
