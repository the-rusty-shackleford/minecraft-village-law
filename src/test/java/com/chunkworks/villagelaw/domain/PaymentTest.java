/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.domain;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: emeralds alone suffice; blocks needed with change; blocks landing exactly; blocks
 * alone; the price exactly met; too poor; a price that is not positive; negative counts; worth past
 * the int range. */
final class PaymentTest {
    @Test void emeraldsFirst() {
        assertEquals(Optional.of(new Payment.Plan(15, 0, 0)), Payment.plan(15, 20, 3));
        assertEquals(Optional.of(new Payment.Plan(8, 0, 0)), Payment.plan(8, 8, 0), "exactly met");
    }
    @Test void blocksCoverTheRestWithChange() {
        assertEquals(Optional.of(new Payment.Plan(4, 2, 7)), Payment.plan(15, 4, 6));
        assertEquals(Optional.of(new Payment.Plan(0, 2, 0)), Payment.plan(18, 0, 5), "blocks alone, landing exactly");
        assertEquals(Optional.of(new Payment.Plan(0, 1, 1)), Payment.plan(8, 0, 2));
    }
    @Test void tooPoor() {
        assertEquals(Optional.empty(), Payment.plan(15, 5, 1));
        assertEquals(14, Payment.worth(5, 1));
    }
    @Test void worthSaturates() {
        assertEquals(Integer.MAX_VALUE, Payment.worth(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
    @Test void invariants() {
        assertThrows(IllegalArgumentException.class, () -> Payment.plan(0, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> Payment.plan(10, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> Payment.worth(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new Payment.Plan(1, 1, 9));
    }
}
