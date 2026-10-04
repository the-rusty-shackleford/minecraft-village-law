/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.domain;

/** Running on flat ground, as the game moves anything that walks, and the movement-speed base that
 * makes a guard's chase as fast as a sprinting player.
 * <p>Each tick on a block of ordinary slipperiness (0.6) a walker keeps 0.6 &times; 0.91 = 0.546 of
 * its velocity and gains its speed times its forward input, the input first damped by 0.98; so it
 * settles at {@code gain / (1 - 0.546)} blocks a tick. A sprinting player's speed is
 * 0.1 &times; 1.3 = 0.13 at a forward input of 1. A mob's move control sets its speed and its
 * forward input both to attribute &times; goal modifier, so a mob gains 0.98 (base &middot;
 * modifier)&sup2;: speed goes with the square of the attribute. */
public final class Chase {
    /** The share of its velocity a walker keeps each tick on ordinary ground: 0.6 &times; 0.91. */
    public static final double GROUND_KEEP = 0.6 * 0.91;
    public static final double INPUT_DAMPING = 0.98;
    /** A player's sprinting speed: the base 0.1 and sprinting's 30%. */
    public static final double SPRINT_SPEED = 0.13;
    /** Guard Villagers' melee goal's speed modifier: a guard chases at its base times this. */
    public static final double GUARD_CHASE_MODIFIER = 0.8;
    private Chase() {}

    /** requires: gain &ge; 0; effects: the speed, in blocks a tick, a walker settles at on flat
     * ground gaining {@code gain} a tick. */
    public static double terminal(double gain) {
        if (gain < 0) throw new IllegalArgumentException("gain " + gain);
        return gain / (1 - GROUND_KEEP);
    }
    /** effects: a sprinting player's speed on flat ground, about 0.281 blocks a tick. */
    public static double sprint() { return terminal(SPRINT_SPEED * INPUT_DAMPING); }
    /** requires: base &ge; 0, modifier &ge; 0; effects: the speed on flat ground of a mob with
     * that movement-speed base, moving under a goal with that modifier. */
    public static double mob(double base, double modifier) {
        if (base < 0 || modifier < 0) throw new IllegalArgumentException("base " + base + ", modifier " + modifier);
        double speed = base * modifier;
        return terminal(INPUT_DAMPING * speed * speed);
    }
    /** requires: ratio &gt; 0, modifier &gt; 0; effects: the movement-speed base b with
     * {@code mob(b, modifier) == ratio * sprint()}: about 0.4507 for a guard's chase at a
     * sprint (ratio 1, modifier 0.8). */
    public static double baseFor(double ratio, double modifier) {
        if (!(ratio > 0) || !(modifier > 0)) throw new IllegalArgumentException("ratio " + ratio + ", modifier " + modifier);
        return Math.sqrt(ratio * SPRINT_SPEED) / modifier;
    }
}
