/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.domain;

import java.util.Optional;

/** Paying a fine in emeralds and blocks of emerald, nine to the block: every emerald that helps
 * goes first, then the fewest blocks that cover the rest, and the overpayment comes back as change.
 * Village Deed's rule for a purchase, so a village takes money one way whatever it charges for. */
public final class Payment {
    public static final int EMERALDS_PER_BLOCK = 9;
    /** What to take and what to hand back. RI: nothing negative; change is less than a block. */
    public record Plan(int emeralds, int blocks, int change) {
        public Plan {
            if (emeralds < 0 || blocks < 0 || change < 0 || change >= EMERALDS_PER_BLOCK) throw new IllegalArgumentException("plan");
        }
    }
    private Payment() {}
    /** requires: counts &ge; 0; effects: what the emeralds and blocks are worth together, in
     * emeralds, capped at Integer.MAX_VALUE. */
    public static int worth(int emeralds, int blocks) {
        if (emeralds < 0 || blocks < 0) throw new IllegalArgumentException("counts");
        return (int) Math.min(Integer.MAX_VALUE, emeralds + (long) EMERALDS_PER_BLOCK * blocks);
    }
    /** requires: price &gt; 0, counts &ge; 0; effects: empty when the player cannot pay; otherwise
     * the plan: emeralds up to the price, blocks for whatever remains, change for the block that
     * overshoots. */
    public static Optional<Plan> plan(int price, int emeralds, int blocks) {
        if (price <= 0) throw new IllegalArgumentException("price");
        if (worth(emeralds, blocks) < price) return Optional.empty();
        int fromEmeralds = Math.min(emeralds, price);
        int remaining = price - fromEmeralds;
        int fromBlocks = (remaining + EMERALDS_PER_BLOCK - 1) / EMERALDS_PER_BLOCK;
        return Optional.of(new Plan(fromEmeralds, fromBlocks, fromBlocks * EMERALDS_PER_BLOCK - remaining));
    }
}
