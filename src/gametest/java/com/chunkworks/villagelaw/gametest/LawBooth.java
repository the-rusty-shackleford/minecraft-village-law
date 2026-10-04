/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.gametest;

import com.chunkworks.carried.api.Carried;
import com.chunkworks.villagedeed.api.VillageId;
import com.chunkworks.villagelaw.Docket;
import com.chunkworks.villagelaw.Summons;
import com.chunkworks.villagelaw.client.CountdownHud;
import com.chunkworks.villagelaw.client.SummonsScreen;
import com.chunkworks.villagelaw.domain.Area;
import com.chunkworks.villagelaw.domain.Case;
import com.chunkworks.villagelaw.domain.Severity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tallestegg.guardvillagers.GuardEntityType;
import java.util.function.Consumer;

/** Hardware-client gate for the summons screen and the countdown, silent. A guard stands in front
 * of the booth player, who has a case with "Taiga Village" for a heavy crime: the summons opens
 * (photographed), a click through the screen's own mouse path on [Leave] puts the player on the
 * clock and the counter appears (photographed, then again red in its last ten seconds); a debt the
 * player cannot cover opens with [Pay] greyed and the shortfall under the text (photographed); with
 * the money in hand, a click on [Pay] clears the case and takes the fine (photographed with the
 * chat line). Screenshots need a human eye; this fixture never ships. */
@EventBusSubscriber(modid = "villagelaw_gametest", value = Dist.CLIENT)
public final class LawBooth {
    private static final Logger LOG = LoggerFactory.getLogger("Village Law booth");
    private static final VillageId TAIGA = VillageId.parse("structure:9001");
    private static int tick;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("villagelaw.booth")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        mc.getToasts().clear();
        try {
            switch (++tick) {
                case 20 -> server(mc, p -> {
                    var l = p.serverLevel();
                    l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, l.getServer());
                    l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, l.getServer());
                    l.setDayTime(6000); l.setWeatherParameters(6000, 0, false, false);
                    p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent();
                    p.getInventory().add(new ItemStack(Items.EMERALD, 20));
                    p.teleportTo(l, p.getX(), p.getY(), p.getZ(), 0F, 8F);
                    var guard = GuardEntityType.GUARD.get().create(l);
                    guard.moveTo(p.getX(), p.getY(), p.getZ() + 3.0, 180F, 0F);
                    guard.setYHeadRot(180F);
                    guard.setNoAi(true);
                    guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                    l.addFreshEntity(guard);
                    var entry = entry(p, new Case(Case.State.SUMMONED, 15, Severity.HEAVY, 1, 0));
                    Docket.get(p.server).put(entry);
                    check(Summons.open(p, entry).isPresent(), "the summons is put on screen");
                });
                case 40 -> {
                    var screen = screen(mc, "the summons opens");
                    var n = screen.notice();
                    check(n.kind() == Summons.Kind.SUMMONS && n.fine() == 15 && n.canPay() && n.villageName().equals("Taiga Village"), "a heavy crime's summons, fined 15, payable: " + n);
                    check(screen.payButton().active && screen.otherButton().getMessage().getString().equals("Leave"), "[Pay 15] live, [Leave] beside it");
                    photo(mc, "01-summons");
                    click(screen, screen.otherButton());
                }
                case 60 -> {
                    check(mc.screen == null, "the screen closed on Leave");
                    server(mc, p -> check(Docket.get(p.server).get(p.getUUID(), TAIGA).map(e -> e.lawCase().state()).orElse(null) == Case.State.FLEEING, "the server put the player on the clock"));
                    var lines = CountdownHud.lines();
                    // A second after the click, give or take the client's clock trailing the server's.
                    check(lines.size() == 1 && lines.get(0).matches("Leave Taiga Village  (1:00|0:5[0-9])"), "the counter is up: " + lines);
                    photo(mc, "02-countdown");
                    // Nine seconds left, on both sides: the server's case and the client's counter.
                    long until = mc.level.getGameTime() + 9 * 20;
                    server(mc, p -> {
                        var e = Docket.get(p.server).get(p.getUUID(), TAIGA).orElseThrow();
                        Docket.get(p.server).put(e.with(new Case(Case.State.FLEEING, 15, Severity.HEAVY, 1, until)));
                    });
                    Summons.Client.accept(new Summons.Deadline("Taiga Village", until));
                }
                case 70 -> {
                    check(CountdownHud.lines().get(0).matches("Leave Taiga Village  0:0[0-9]"), "under ten seconds: " + CountdownHud.lines());
                    photo(mc, "03-countdown-red");
                    Summons.Client.accept(new Summons.Deadline("Taiga Village", 0));
                    server(mc, p -> {
                        p.getInventory().clearContent();
                        p.getInventory().add(new ItemStack(Items.EMERALD, 7));
                        var debt = entry(p, new Case(Case.State.DEBT, 15, Severity.HEAVY, 1, 0));
                        Docket.get(p.server).put(debt);
                        Summons.open(p, debt);
                    });
                }
                case 90 -> {
                    var screen = screen(mc, "the debt opens");
                    var n = screen.notice();
                    check(n.kind() == Summons.Kind.DEBT && !n.canPay() && n.carrying() == 7, "a debt of 15 the player cannot cover with 7: " + n);
                    check(!screen.payButton().active && screen.otherButton().getMessage().getString().equals("Not now"), "[Pay] greyed, [Not now] beside it");
                    check(CountdownHud.lines().isEmpty(), "the counter is down");
                    photo(mc, "04-debt-short");
                    server(mc, p -> {
                        p.getInventory().add(new ItemStack(Items.EMERALD_BLOCK, 1));
                        Summons.open(p, Docket.get(p.server).get(p.getUUID(), TAIGA).orElseThrow());
                    });
                }
                case 110 -> {
                    var screen = screen(mc, "the debt reopens");
                    check(screen.notice().canPay() && screen.payButton().active, "seven emeralds and a block cover 15: [Pay] is live");
                    click(screen, screen.payButton());
                }
                case 130 -> {
                    check(mc.screen == null, "the screen closed on Pay");
                    server(mc, p -> {
                        check(Docket.get(p.server).get(p.getUUID(), TAIGA).isEmpty(), "the case is closed");
                        check(Carried.count(p, Items.EMERALD) == 1 && Carried.count(p, Items.EMERALD_BLOCK) == 0, "seven and a block taken for 15, one back");
                    });
                    photo(mc, "05-paid");
                }
                case 140 -> {
                    LOG.info("villagelaw booth: COMPLETE");
                    mc.stop();
                }
            }
        } catch (Throwable failure) { LOG.error("villagelaw booth: FAIL", failure); mc.stop(); }
    }
    /** effects: an entry for the booth player with Taiga Village, whose bounds hold the player. */
    private static Docket.Entry entry(ServerPlayer p, Case lawCase) {
        int x = p.getBlockX(), y = p.getBlockY(), z = p.getBlockZ();
        return new Docket.Entry(p.getUUID(), p.getScoreboardName(), TAIGA, "Taiga Village", Level.OVERWORLD, new Area(x - 8, y - 4, z - 8, x + 8, y + 8, z + 8), lawCase);
    }
    private static SummonsScreen screen(Minecraft mc, String what) {
        check(mc.screen instanceof SummonsScreen, what);
        return (SummonsScreen) mc.screen;
    }
    /** effects: a left click at the button's centre, through the screen's own mouse path. */
    private static void click(SummonsScreen screen, Button button) {
        check(screen.mouseClicked(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0, 0), "the click lands on " + button.getMessage().getString());
    }
    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        server.execute(() -> { try { action.accept(server.getPlayerList().getPlayer(id)); } catch (Throwable failure) { LOG.error("villagelaw booth: FAIL", failure); mc.execute(mc::stop); } });
    }
    private static void photo(Minecraft mc, String name) {
        mc.getToasts().clear();
        Screenshot.grab(mc.gameDirectory, "villagelaw-" + name + ".png", mc.getMainRenderTarget(), m -> LOG.info("villagelaw booth: {}", m.getString()));
    }
    private static void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); LOG.info("villagelaw booth: PASS {}", message); }
}
