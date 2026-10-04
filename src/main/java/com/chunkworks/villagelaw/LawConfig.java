/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw;

import com.chunkworks.villagelaw.domain.Countdown;
import com.chunkworks.villagelaw.domain.Fines;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side settings, in {@code config/villagelaw-server.toml}. Read through the accessors, which
 * answer the defaults before the file has loaded. */
public final class LawConfig {
    private LawConfig() {}
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue FINE_LIGHT, FINE_MEDIUM, FINE_HEAVY, LEAVE_SECONDS, VICINITY;
    public static final ModConfigSpec.DoubleValue BANISHMENT_DAYS, CHASE_SPEED;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("fines");
        builder.comment("Fines in emeralds, by the grade Thief gives a crime. Only crimes at or over Thief's guard_attack_threshold (MEDIUM on a default server) open a case;",
                "below it, Thief's reputation hit is the whole punishment.");
        FINE_LIGHT = builder.comment("Default: 3").defineInRange("light", Fines.STANDARD.light(), 1, 100000);
        FINE_MEDIUM = builder.comment("Default: 8").defineInRange("medium", Fines.STANDARD.medium(), 1, 100000);
        FINE_HEAVY = builder.comment("Default: 15").defineInRange("heavy", Fines.STANDARD.heavy(), 1, 100000);
        builder.pop();
        builder.push("law");
        LEAVE_SECONDS = builder.comment("How long a player who chooses to leave has to get out of the village's vicinity before its guards attack.", "Default: 60")
                .defineInRange("leave_seconds", 60, 5, 3600);
        BANISHMENT_DAYS = builder.comment("How long a player who left stays banished, in Minecraft days of 20 minutes. The guards attack a banished player who comes back;",
                "after it the fine is still owed, payable to any of the village's guards.", "Default: 2.0").defineInRange("banishment_days", 2.0, 0.01, 365.0);
        VICINITY = builder.comment("How far past a village's bounds its law reaches, in blocks: the line a fleeing player must cross.", "Default: 32")
                .defineInRange("vicinity", 32, 0, 256);
        builder.pop();
        builder.push("guards");
        CHASE_SPEED = builder.comment("A guard's chase speed relative to a sprinting player's. Guard Villagers' own default runs about 1.23.",
                "Applied to every guard as it loads, those already in the world included.", "Default: 1.0").defineInRange("chase_speed", 1.0, 0.25, 3.0);
        builder.pop();
        SPEC = builder.build();
    }

    /** effects: the fines for each grade. */
    public static Fines fines() {
        return SPEC.isLoaded() ? new Fines(FINE_LIGHT.get(), FINE_MEDIUM.get(), FINE_HEAVY.get()) : Fines.STANDARD;
    }
    /** effects: the ticks a player who chose to leave has to get out. */
    public static long leaveTicks() { return (long) (SPEC.isLoaded() ? LEAVE_SECONDS.get() : LEAVE_SECONDS.getDefault()) * Countdown.TICKS_PER_SECOND; }
    /** effects: how long a banishment lasts, in ticks. */
    public static long banishTicks() { return Math.round((SPEC.isLoaded() ? BANISHMENT_DAYS.get() : BANISHMENT_DAYS.getDefault()) * 24000); }
    /** effects: how far past a village's bounds its law reaches. */
    public static int vicinity() { return SPEC.isLoaded() ? VICINITY.get() : VICINITY.getDefault(); }
    /** effects: a guard's chase speed relative to a sprint. */
    public static double chaseSpeed() { return SPEC.isLoaded() ? CHASE_SPEED.get() : CHASE_SPEED.getDefault(); }
}
