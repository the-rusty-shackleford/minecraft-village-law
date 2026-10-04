/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.domain;

/** What each grade of crime costs, in emeralds. Immutable.
 * <p>RI: every fine is at least 1. */
public record Fines(int light, int medium, int heavy) {
    /** The defaults: a heavy crime costs 15, what Serfdom's capture costs; a light one is fined
     * only on a server that lowers Thief's guard threshold to LIGHT. */
    public static final Fines STANDARD = new Fines(3, 8, 15);

    public Fines {
        if (light < 1 || medium < 1 || heavy < 1) throw new IllegalArgumentException("every fine is at least one emerald");
    }
    /** effects: the fine for a crime of that grade. */
    public int of(Severity severity) {
        return switch (severity) {
            case LIGHT -> light;
            case MEDIUM -> medium;
            case HEAVY -> heavy;
        };
    }
}
