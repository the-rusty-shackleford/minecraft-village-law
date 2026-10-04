/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.domain;

/** A box of whole blocks, both corners inside it: a village's bounds, or its vicinity. Immutable.
 * <p>RI: min &le; max on every axis. */
public record Area(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Area {
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("an area's minimum corner lies at or below its maximum");
    }
    /** effects: whether the block at (x, y, z) lies inside, faces included. */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
    /** effects: whether the block holding the point lies inside: each coordinate floored. */
    public boolean contains(double x, double y, double z) {
        return contains((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
    /** requires: by &ge; 0; effects: the area grown {@code by} blocks on every side, capped at
     * the int range. */
    public Area inflate(int by) {
        if (by < 0) throw new IllegalArgumentException("by " + by);
        return new Area(down(minX, by), down(minY, by), down(minZ, by), up(maxX, by), up(maxY, by), up(maxZ, by));
    }
    private static int down(int v, int by) { return (int) Math.max(Integer.MIN_VALUE, (long) v - by); }
    private static int up(int v, int by) { return (int) Math.min(Integer.MAX_VALUE, (long) v + by); }
}
