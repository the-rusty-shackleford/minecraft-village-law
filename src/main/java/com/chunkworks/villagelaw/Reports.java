/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw;

import com.chunkworks.villagedeed.api.Village;
import com.chunkworks.villagedeed.api.VillageProviders;
import com.chunkworks.villagelaw.domain.Case;
import com.chunkworks.villagelaw.domain.Severity;
import io.github.mortuusars.thief.neoforge.api.event.CrimeCommitedEvent;
import io.github.mortuusars.thief.world.Witness;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/** Thief's crimes, read as reports to the law. A crime at or over Thief's guard threshold, by a
 * player, in a village the protocol knows, seen by an officer, opens a case with the village or adds
 * to the open one, and the nearest officer that saw it sets off with the summons. Anything less is
 * Thief's alone: a crime only villagers saw costs reputation and nothing more. Deed owners, the
 * players they trust, a Hero of the Village and creative players commit no Thief crime, so they
 * never reach here. */
public final class Reports {
    /** How far from a village a crime may be and still be its: the criminal stands within reach
     * of a protected block, which may lie at the village's edge. */
    static final int NEAR = 16;
    private static UUID askedFor;
    private static long askedAt = Long.MIN_VALUE;
    private static boolean answer;
    private Reports() {}

    /** effects: the village the position lies in, else the nearest within {@link #NEAR}. Public for
     * {@link com.chunkworks.villagelaw.api.Cases}. */
    public static Optional<Village> villageAround(ServerLevel level, BlockPos pos) {
        return VillageProviders.at(level, pos).or(() -> VillageProviders.near(level, pos, NEAR));
    }
    /** effects: the nearest live officer among the witnesses, or null. */
    static Mob nearestOfficer(LivingEntity criminal, Collection<? extends LivingEntity> witnesses) {
        return witnesses.stream().filter(w -> w instanceof Mob && w.isAlive() && Officers.isOfficer(w)).map(w -> (Mob) w)
                .min(Comparator.comparingDouble(w -> w.distanceToSqr(criminal))).orElse(null);
    }

    /** effects: the mixin's question: whether the law takes the criminal's crime here, so Thief's
     * guards hold their attack: a player, in a village, seen by an officer. Thief asks once for each
     * guard that saw the crime, so the answer is kept for the rest of the tick. */
    public static boolean policed(ServerLevel level, LivingEntity criminal) {
        long now = level.getGameTime();
        if (criminal.getUUID().equals(askedFor) && now == askedAt) return answer;
        askedFor = criminal.getUUID();
        askedAt = now;
        answer = criminal instanceof ServerPlayer && villageAround(level, criminal.blockPosition()).isPresent()
                && nearestOfficer(criminal, Witness.getWitnesses(criminal)) != null;
        return answer;
    }

    /** effects: opens or adds to the criminal's case with the village when an officer saw a crime
     * over Thief's guard threshold, and sends the summons. */
    static void onCrime(CrimeCommitedEvent event) {
        if (!(event.criminal instanceof ServerPlayer player) || !event.crime.isOverGuardAttackThreshold()) return;
        var level = player.serverLevel();
        var village = villageAround(level, player.blockPosition());
        var officer = nearestOfficer(player, event.witnesses);
        if (village.isEmpty() || officer == null) return;
        report(player, village.get(), Severity.valueOf(event.crime.name()), officer);
    }
    /** effects: the case after a crime of that grade the officer saw: a new WANTED case, or the open
     * one with the crime added; a WANTED case's summons goes to the officer. */
    public static Docket.Entry report(ServerPlayer player, Village village, Severity severity, Mob officer) {
        var docket = Docket.get(player.server);
        int fine = LawConfig.fines().of(severity);
        var existing = docket.get(player.getUUID(), village.id());
        Docket.Entry entry;
        if (existing.isEmpty()) {
            entry = Docket.Entry.open(player, village, Case.opened(severity, fine));
            docket.put(entry);
            VillageLaw.LOGGER.info("{} committed a {} crime in {} ({}), seen by an officer: fined {}", player.getScoreboardName(), severity.key(), village.name(), village.id(), fine);
        } else {
            entry = Law.move(player, existing.get(), existing.get().lawCase().withCrime(severity, fine));
            VillageLaw.LOGGER.info("{} committed another {} crime in {}: the fine stands at {}", player.getScoreboardName(), severity.key(), village.name(), entry.lawCase().fine());
        }
        player.sendSystemMessage(Component.translatable("message.villagelaw.reported", village.name(), entry.lawCase().fine()).withStyle(ChatFormatting.GOLD));
        if (entry.lawCase().state() == Case.State.WANTED) Summons.assign(officer, player, village.id());
        return entry;
    }
}
