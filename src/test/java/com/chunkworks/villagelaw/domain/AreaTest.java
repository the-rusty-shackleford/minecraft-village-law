/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: a block inside, on each face, one past each face; a point with a fraction, negative
 * coordinates flooring down; inflated by zero and by more; inflating at the int range's edge; the
 * rep invariant. */
final class AreaTest {
    private static final Area HUT = new Area(-8, 60, 0, 7, 65, 7);

    @Test void blocksInsideOnTheFacesAndPastThem() {
        assertTrue(HUT.contains(0, 62, 3));
        assertTrue(HUT.contains(-8, 60, 0), "the minimum corner");
        assertTrue(HUT.contains(7, 65, 7), "the maximum corner");
        assertFalse(HUT.contains(-9, 62, 3));
        assertFalse(HUT.contains(8, 62, 3));
        assertFalse(HUT.contains(0, 59, 3));
        assertFalse(HUT.contains(0, 66, 3));
        assertFalse(HUT.contains(0, 62, -1));
        assertFalse(HUT.contains(0, 62, 8));
    }
    @Test void pointsFloorToTheirBlock() {
        assertTrue(HUT.contains(7.99, 65.5, 0.0));
        assertTrue(HUT.contains(-7.5, 60.0, 0.1), "-7.5 floors to -8, inside");
        assertFalse(HUT.contains(-8.01, 60.0, 0.1), "-8.01 floors to -9, outside");
    }
    @Test void inflating() {
        assertEquals(HUT, HUT.inflate(0));
        var vicinity = HUT.inflate(32);
        assertEquals(new Area(-40, 28, -32, 39, 97, 39), vicinity);
        assertTrue(vicinity.contains(39, 62, 3));
        assertFalse(vicinity.contains(40, 62, 3));
        var edge = new Area(Integer.MIN_VALUE + 1, 0, 0, Integer.MAX_VALUE - 1, 0, 0).inflate(5);
        assertEquals(Integer.MIN_VALUE, edge.minX());
        assertEquals(Integer.MAX_VALUE, edge.maxX());
        assertThrows(IllegalArgumentException.class, () -> HUT.inflate(-1));
    }
    @Test void repInvariant() {
        assertThrows(IllegalArgumentException.class, () -> new Area(1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Area(0, 1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Area(0, 0, 1, 0, 0, 0));
        assertDoesNotThrow(() -> new Area(0, 0, 0, 0, 0, 0), "a single block");
    }
}
