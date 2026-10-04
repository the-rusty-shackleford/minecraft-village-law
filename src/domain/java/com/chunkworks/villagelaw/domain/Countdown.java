/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.domain;

/** The face of a countdown: ticks left as minutes and seconds, the way a clock shows them. A part
 * second counts as a whole one, so the face reads 0:00 only once the time is up. */
public final class Countdown {
    public static final int TICKS_PER_SECOND = 20;
    private Countdown() {}

    /** effects: the whole seconds in {@code ticks}, a part second rounded up; 0 when ticks &le; 0. */
    public static long secondsLeft(long ticks) { return ticks <= 0 ? 0 : (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND; }
    /** effects: {@link #secondsLeft} as "m:ss": the minutes unpadded and unbounded, the seconds two
     * digits ("0:07", "1:00", "61:05"). */
    public static String format(long ticks) {
        long seconds = secondsLeft(ticks);
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }
}
