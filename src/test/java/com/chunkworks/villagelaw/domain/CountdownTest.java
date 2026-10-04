/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: ticks below zero, zero; a part second (1 to 19 ticks); a second's boundary (20, 21);
 * under ten seconds and over; a whole minute; past an hour. */
final class CountdownTest {
    @Test void timeUpReadsZero() {
        assertEquals("0:00", Countdown.format(0));
        assertEquals("0:00", Countdown.format(-40));
        assertEquals(0, Countdown.secondsLeft(-1));
    }
    @Test void aPartSecondCountsWhole() {
        assertEquals("0:01", Countdown.format(1));
        assertEquals("0:01", Countdown.format(19));
        assertEquals("0:01", Countdown.format(20));
        assertEquals("0:02", Countdown.format(21));
    }
    @Test void secondsAreTwoDigits() {
        assertEquals("0:09", Countdown.format(9 * 20));
        assertEquals("0:42", Countdown.format(42 * 20));
        assertEquals("1:00", Countdown.format(60 * 20));
        assertEquals("1:00", Countdown.format(59 * 20 + 1), "59 seconds and a tick rounds up to the minute");
    }
    @Test void minutesAreUnbounded() {
        assertEquals("61:05", Countdown.format((61 * 60 + 5) * 20));
        assertEquals(3665, Countdown.secondsLeft((61 * 60 + 5) * 20L));
    }
}
