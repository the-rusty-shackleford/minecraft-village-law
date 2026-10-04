/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: each grade; the standard table; a fine at one and below it; the grades' order and
 * their keys. */
final class FinesTest {
    @Test void eachGradeHasItsFine() {
        var fines = new Fines(1, 2, 3);
        assertEquals(1, fines.of(Severity.LIGHT));
        assertEquals(2, fines.of(Severity.MEDIUM));
        assertEquals(3, fines.of(Severity.HEAVY));
    }
    @Test void theStandardTable() {
        assertEquals(new Fines(3, 8, 15), Fines.STANDARD, "a heavy crime costs what Serfdom's capture costs");
    }
    @Test void everyFineIsAtLeastOne() {
        assertThrows(IllegalArgumentException.class, () -> new Fines(0, 8, 15));
        assertThrows(IllegalArgumentException.class, () -> new Fines(3, 0, 15));
        assertThrows(IllegalArgumentException.class, () -> new Fines(3, 8, -1));
        assertDoesNotThrow(() -> new Fines(1, 1, 1));
    }
    @Test void gradesOrderAndKeys() {
        assertEquals(Severity.HEAVY, Severity.MEDIUM.max(Severity.HEAVY));
        assertEquals(Severity.MEDIUM, Severity.MEDIUM.max(Severity.LIGHT));
        assertEquals(Severity.LIGHT, Severity.LIGHT.max(Severity.LIGHT));
        assertEquals("heavy", Severity.HEAVY.key());
    }
}
