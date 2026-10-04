/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw;

import com.chunkworks.villagelaw.domain.Chase;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;

/** An officer's run to a player it holds a summons for, at its chase speed, and its wait in front
 * of them while they choose. Reaching {@link #REACH} blocks serves the summons. The goal yields to
 * any target: an officer in a fight fights. */
final class SummonGoal extends Goal {
    /** After Guard Villagers' eating and shield goals (0 and 1), before its fighting (3) and its
     * patrols and strolls (4 and up). */
    static final int PRIORITY = 2;
    /** How near an officer comes before it speaks: three blocks. */
    static final double REACH = 3.0;
    private static final int REPATH_TICKS = 10;
    private final PathfinderMob officer;
    private ServerPlayer player;
    private int repath;

    SummonGoal(PathfinderMob officer) {
        this.officer = officer;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    @Override public boolean canUse() {
        if (officer.getTarget() != null) return false;
        player = Summons.summonee(officer);
        return player != null;
    }
    @Override public boolean canContinueToUse() { return officer.getTarget() == null && player != null && Summons.summonee(officer) == player; }
    @Override public void start() { repath = 0; }
    @Override public void stop() { officer.getNavigation().stop(); player = null; }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() {
        officer.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (officer.distanceToSqr(player) <= REACH * REACH) {
            officer.getNavigation().stop();
            Summons.reached(officer, player);
        } else if (--repath <= 0) {
            repath = REPATH_TICKS;
            officer.getNavigation().moveTo(player, Chase.GUARD_CHASE_MODIFIER);
        }
    }
}
