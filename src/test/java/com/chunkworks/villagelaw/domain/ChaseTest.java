/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: a sprint's speed; a chase at a sprint (ratio 1) under the guard's modifier and under
 * 1; ratios below and above 1; the box's base 0.5 against a sprint; the square law (doubling the
 * base quadruples the gain); a standing walker; arguments out of range. */
final class ChaseTest {
    private static final double EPS = 1e-9;

    @Test void aSprintIsAboutPointTwoEightBlocksATick() {
        assertEquals(0.13 * 0.98 / (1 - 0.546), Chase.sprint(), EPS);
        assertEquals(0.2806, Chase.sprint(), 1e-4);
    }
    @Test void aGuardAtTheFoundBaseChasesAtASprint() {
        double base = Chase.baseFor(1.0, Chase.GUARD_CHASE_MODIFIER);
        assertEquals(0.4507, base, 1e-4, "the plan's measured base, about 0.45");
        assertEquals(Chase.sprint(), Chase.mob(base, Chase.GUARD_CHASE_MODIFIER), EPS);
        assertEquals(Math.sqrt(0.13), Chase.baseFor(1.0, 1.0), EPS, "under no modifier, the base whose square is the sprint speed");
    }
    @Test void otherRatiosScaleTheSpeedNotTheBase() {
        for (double ratio : new double[] { 0.5, 1.25, 2.0 }) {
            double base = Chase.baseFor(ratio, Chase.GUARD_CHASE_MODIFIER);
            assertEquals(ratio * Chase.sprint(), Chase.mob(base, Chase.GUARD_CHASE_MODIFIER), EPS, "ratio " + ratio);
        }
    }
    @Test void theBoxBaseOutrunsASprint() {
        double ratio = Chase.mob(0.5, Chase.GUARD_CHASE_MODIFIER) / Chase.sprint();
        assertEquals(0.16 / 0.13, ratio, EPS, "Guard Villagers' 0.5 runs 23% faster than a sprinting player");
    }
    @Test void speedGoesWithTheSquareOfTheBase() {
        assertEquals(4 * Chase.mob(0.2, 0.8), Chase.mob(0.4, 0.8), EPS);
        assertEquals(0, Chase.mob(0, 0.8), EPS, "a standing walker");
    }
    @Test void argumentsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> Chase.baseFor(0, 0.8));
        assertThrows(IllegalArgumentException.class, () -> Chase.baseFor(1, 0));
        assertThrows(IllegalArgumentException.class, () -> Chase.baseFor(Double.NaN, 0.8));
        assertThrows(IllegalArgumentException.class, () -> Chase.mob(-0.1, 0.8));
        assertThrows(IllegalArgumentException.class, () -> Chase.terminal(-1));
    }
}
