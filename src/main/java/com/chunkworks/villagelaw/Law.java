/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw;

import com.chunkworks.villagelaw.api.CaseSettledEvent;
import com.chunkworks.villagelaw.domain.Case;
import com.chunkworks.villagelaw.domain.Case.State;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;

/** Moving a case from one state to the next, and everything a move sets off: the docket written,
 * the player told, the countdown shown or taken down, the summons released, the village's
 * enforcers stood down, its gossip about the player forgiven, and the settlement posted. Reports,
 * Summons and the Patrol all move cases through here, so each reaction lives in one place. */
public final class Law {
    private Law() {}

    /** requires: next is the entry's case after one transition; effects: records it and carries out
     * what the change from the entry's case to {@code next} means; returns the entry as recorded. */
    public static Docket.Entry move(ServerPlayer player, Docket.Entry entry, Case next) {
        var before = entry.lawCase();
        if (next.equals(before)) return entry;
        var after = entry.with(next);
        Docket.get(player.server).put(after);
        if (next.state() == before.state()) {
            // More crimes before the matter is settled: a summons on screen shows the new total.
            if (next.state() == State.SUMMONED) Summons.open(player, after);
            return after;
        }
        VillageLaw.LOGGER.info("{}'s case with {} ({}): {} -> {}, {} emeralds", player.getScoreboardName(), entry.villageName(), entry.village(), before.state(), next.state(), next.fine());
        if (before.state() == State.FLEEING) Summons.send(player, new Summons.Deadline(entry.villageName(), 0));
        switch (next.state()) {
            case SUMMONED -> Summons.open(player, after);
            case FLEEING -> {
                Summons.release(player.getUUID(), entry.village());
                Summons.send(player, new Summons.Deadline(entry.villageName(), next.until()));
                tell(player, Component.translatable("message.villagelaw.leave", entry.villageName(), LawConfig.leaveTicks() / 20, days(LawConfig.banishTicks())), ChatFormatting.GOLD);
            }
            case HOSTILE -> {
                tell(player, Component.translatable("message.villagelaw.hostile", entry.villageName()), ChatFormatting.RED);
                Officers.rally(player, after);
            }
            case BANISHED -> {
                Summons.release(player.getUUID(), entry.village());
                Officers.standDown(player, after);
                tell(player, Component.translatable("message.villagelaw.banished", entry.villageName(), days(next.until() - player.server.overworld().getGameTime()), next.fine()), ChatFormatting.RED);
                NeoForge.EVENT_BUS.post(new CaseSettledEvent(player, entry.village(), CaseSettledEvent.Settlement.FLED, next.fine()));
            }
            case DEBT -> tell(player, Component.translatable("message.villagelaw.debt", entry.villageName(), next.fine()), ChatFormatting.GOLD);
            case CLEARED -> {
                Summons.release(player.getUUID(), entry.village());
                Officers.standDown(player, after);
                int forgiven = Gossip.forgive(player.serverLevel(), after, player.getUUID());
                tell(player, Component.translatable("message.villagelaw.paid", next.fine(), entry.villageName()), ChatFormatting.GREEN);
                player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
                VillageLaw.LOGGER.info("{} paid {} emeralds to {}; {} villagers and guards forgave them", player.getScoreboardName(), next.fine(), entry.villageName(), forgiven);
                NeoForge.EVENT_BUS.post(new CaseSettledEvent(player, entry.village(), CaseSettledEvent.Settlement.PAID, next.fine()));
            }
            case WANTED -> {}
        }
        return after;
    }
    private static void tell(ServerPlayer player, Component message, ChatFormatting colour) { player.sendSystemMessage(message.copy().withStyle(colour)); }
    /** effects: ticks as Minecraft days, rounded to a tenth, for a message ("2", "0.5"). */
    static String days(long ticks) {
        double d = Math.round(Math.max(0, ticks) / 2400.0) / 10.0;
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }
}
