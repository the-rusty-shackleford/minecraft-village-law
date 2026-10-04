/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.client;

import com.chunkworks.villagelaw.Summons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;

/** The guard's question, a plain panel in the trust screen's family: the village, the crime and the
 * fine, and two buttons, below the crosshair over the lightly dimmed world, so the guard stands in
 * view. The summons offers [Pay] and [Leave]; the debt offers [Pay] and [Not now].
 * [Pay] is greyed, with what the player carries under the text, when they cannot pay. Closing the
 * screen any other way (Escape, a screen opened over it) counts as Leave on a summons, as walking
 * away does, and as Not now on a debt. The screen sends one choice and closes; the server decides
 * what it comes to. */
public final class SummonsScreen extends Screen {
    private static final int WIDTH = 236, PAD = 8, INNER = WIDTH - 2 * PAD, BUTTON_W = 104, BUTTON_H = 20, LINE = 10, BELOW_CENTRE = 28;
    private static final int TITLE = 0xFFD37F, TEXT = 0xE0E0E0, SHORT = 0xFF7F7F;
    private final Summons.Notice notice;
    private List<FormattedCharSequence> body = List.of();
    private Button pay, other;
    private int left, top, panelHeight;
    private boolean answered;

    public SummonsScreen(Summons.Notice notice) {
        super(Component.literal(notice.villageName()));
        this.notice = notice;
    }
    /** effects: shows the notice, replacing any summons screen already open (a fine that grew while
     * the player read it) without answering it. */
    public static void accept(Summons.Notice notice) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof SummonsScreen open) open.answered = true;
        mc.setScreen(new SummonsScreen(notice));
    }
    /** effects: the notice on show. */
    public Summons.Notice notice() { return notice; }
    /** effects: the pay button. */
    public Button payButton() { return pay; }
    /** effects: the second button: Leave on a summons, Not now on a debt. */
    public Button otherButton() { return other; }

    private Component crime() {
        var grade = Component.translatable("screen.villagelaw.crime." + notice.worst());
        return notice.crimes() == 1 ? Component.translatable("screen.villagelaw.one_crime", grade) : Component.translatable("screen.villagelaw.crimes", notice.crimes(), grade);
    }
    private Component message() {
        if (notice.kind() == Summons.Kind.DEBT) return Component.translatable("screen.villagelaw.debt.body", notice.fine(), crime());
        return Component.translatable("screen.villagelaw.summons.body", crime(), notice.fine(), notice.leaveSeconds(), days());
    }
    private Component days() {
        double d = notice.banishDays();
        if (d == 2) return Component.translatable("screen.villagelaw.two_days");
        return Component.translatable("screen.villagelaw.days", d == Math.rint(d) ? Long.toString((long) d) : String.format("%.1f", d));
    }
    private Summons.Choice unanswered() { return notice.kind() == Summons.Kind.DEBT ? Summons.Choice.DISMISS : Summons.Choice.LEAVE; }

    @Override protected void init() {
        body = font.split(message(), INNER);
        int lines = body.size() + (notice.canPay() ? 0 : 1);
        panelHeight = PAD + 16 + lines * LINE + 8 + BUTTON_H + PAD;
        left = (width - WIDTH) / 2;
        // Below the crosshair, so the guard who stopped the player stands in view above the panel.
        top = Math.max(4, Math.min(height / 2 + BELOW_CENTRE, height - panelHeight - 4));
        int buttonsTop = top + panelHeight - PAD - BUTTON_H;
        pay = addRenderableWidget(Button.builder(Component.translatable("screen.villagelaw.pay", notice.fine()), b -> answer(Summons.Choice.PAY))
                .bounds(left + PAD, buttonsTop, BUTTON_W, BUTTON_H).build());
        pay.active = notice.canPay();
        if (!pay.active) pay.setTooltip(Tooltip.create(Component.translatable("screen.villagelaw.carrying", notice.carrying(), notice.fine())));
        var otherLabel = Component.translatable(notice.kind() == Summons.Kind.DEBT ? "screen.villagelaw.not_now" : "screen.villagelaw.leave");
        other = addRenderableWidget(Button.builder(otherLabel, b -> answer(unanswered())).bounds(left + WIDTH - PAD - BUTTON_W, buttonsTop, BUTTON_W, BUTTON_H).build());
    }
    @Override public boolean isPauseScreen() { return false; }
    /** effects: the world lightly dimmed, not blurred as menus are, so the guard stays clear; then
     * the panel, so the buttons draw over it. */
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x50000000);
        g.fill(left, top, left + WIDTH, top + panelHeight, 0xFF3F3F3F);
        g.fill(left + 1, top + 1, left + WIDTH - 1, top + panelHeight - 1, 0xF0181818);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, font.plainSubstrByWidth(notice.villageName(), INNER), left + PAD, top + PAD, TITLE, false);
        int y = top + PAD + 16;
        for (var line : body) { g.drawString(font, line, left + PAD, y, TEXT, false); y += LINE; }
        if (!notice.canPay()) g.drawString(font, Component.translatable("screen.villagelaw.carrying", notice.carrying(), notice.fine()), left + PAD, y, SHORT, false);
    }
    /** effects: sends the choice and closes the screen. */
    private void answer(Summons.Choice choice) {
        send(choice);
        if (minecraft != null) minecraft.setScreen(null);
    }
    @Override public void removed() { send(unanswered()); }
    /** effects: sends the first choice made, once, while connected. */
    private void send(Summons.Choice choice) {
        if (answered) return;
        answered = true;
        if (Minecraft.getInstance().getConnection() != null) PacketDistributor.sendToServer(new Summons.Answer(notice.village(), choice));
    }
}
