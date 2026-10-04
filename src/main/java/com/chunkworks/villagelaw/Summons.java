/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw;

import com.chunkworks.carried.api.Carried;
import com.chunkworks.villagedeed.api.VillageId;
import com.chunkworks.villagelaw.domain.Case;
import com.chunkworks.villagelaw.domain.Countdown;
import com.chunkworks.villagelaw.domain.Payment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;
import java.util.function.Consumer;

/** The summons: which officer carries which player's summons, the choice it puts on the player's
 * screen, and the answer. The screen is client code, handed each notice through {@link Client};
 * the server decides everything, and an answer that no longer fits the case changes nothing.
 * <p>Assignments are a running server's memory, not saved: after a restart the patrol hands each
 * WANTED case to the nearest officer again. */
public final class Summons {
    /** A summons in hand: the player and the village it is for. */
    private record Assignment(UUID player, VillageId village) {}
    private static final Map<UUID, Assignment> BY_OFFICER = new HashMap<>();
    private static final Map<Assignment, UUID> BY_CASE = new HashMap<>();
    private Summons() {}

    /** What the player's screen shows: the summons with its choice (pay or leave), or the debt a
     * banishment left (pay or not now). */
    public enum Kind { SUMMONS, DEBT }
    /** The screen's contents. {@code worst} is a {@code Severity} key; {@code carrying} what the
     * player carries in emeralds, blocks counted at nine. */
    public record Notice(String village, String villageName, Kind kind, int fine, String worst, int crimes, int carrying, int leaveSeconds, double banishDays) implements CustomPacketPayload {
        public static final Type<Notice> TYPE = new Type<>(VillageLaw.id("notice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Notice> CODEC = StreamCodec.of((buf, n) -> {
            buf.writeUtf(n.village()); buf.writeUtf(n.villageName()); buf.writeEnum(n.kind()); buf.writeVarInt(n.fine()); buf.writeUtf(n.worst());
            buf.writeVarInt(n.crimes()); buf.writeVarInt(n.carrying()); buf.writeVarInt(n.leaveSeconds()); buf.writeDouble(n.banishDays());
        }, buf -> new Notice(buf.readUtf(), buf.readUtf(), buf.readEnum(Kind.class), buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readDouble()));
        /** effects: whether what the player carries covers the fine. */
        public boolean canPay() { return carrying >= fine; }
        @Override public Type<Notice> type() { return TYPE; }
    }
    /** The player's choice on the screen. DISMISS closes the debt screen and changes nothing. */
    public enum Choice { PAY, LEAVE, DISMISS }
    public record Answer(String village, Choice choice) implements CustomPacketPayload {
        public static final Type<Answer> TYPE = new Type<>(VillageLaw.id("answer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Answer> CODEC = StreamCodec.of(
                (buf, a) -> { buf.writeUtf(a.village()); buf.writeEnum(a.choice()); },
                buf -> new Answer(buf.readUtf(), buf.readEnum(Choice.class)));
        @Override public Type<Answer> type() { return TYPE; }
    }
    /** The countdown of a player leaving a village: the tick they must be out by, or 0 to take the
     * village's countdown down. */
    public record Deadline(String villageName, long endsAt) implements CustomPacketPayload {
        public static final Type<Deadline> TYPE = new Type<>(VillageLaw.id("deadline"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Deadline> CODEC = StreamCodec.of(
                (buf, d) -> { buf.writeUtf(d.villageName()); buf.writeVarLong(d.endsAt()); },
                buf -> new Deadline(buf.readUtf(), buf.readVarLong()));
        @Override public Type<Deadline> type() { return TYPE; }
    }
    /** Where the client takes notices and deadlines: the screens register themselves at client
     * setup, so this common class never names a client class. */
    public static final class Client {
        private static volatile Consumer<Notice> notices = n -> {};
        private static volatile Consumer<Deadline> deadlines = d -> {};
        private Client() {}
        public static void receivers(Consumer<Notice> n, Consumer<Deadline> d) { notices = Objects.requireNonNull(n); deadlines = Objects.requireNonNull(d); }
        public static void accept(Notice notice) { notices.accept(notice); }
        public static void accept(Deadline deadline) { deadlines.accept(deadline); }
    }

    /** effects: registers the payloads. Required, not optional: a client without the screen would
     * be summoned with nothing to answer, so it is refused at login with the mod mismatch. Bump
     * the version with any change to what either side sends. */
    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Notice.TYPE, Notice.CODEC, (n, ctx) -> Client.accept(n));
        registrar.playToClient(Deadline.TYPE, Deadline.CODEC, (d, ctx) -> Client.accept(d));
        registrar.playToServer(Answer.TYPE, Answer.CODEC, (a, ctx) -> { if (ctx.player() instanceof ServerPlayer player) answer(player, a); });
    }

    /** effects: hands the case's summons to the officer, taking it from any officer that held it. */
    static void assign(Mob officer, ServerPlayer player, VillageId village) {
        var key = new Assignment(player.getUUID(), village);
        var previous = BY_CASE.put(key, officer.getUUID());
        if (previous != null && !previous.equals(officer.getUUID())) BY_OFFICER.remove(previous);
        var dropped = BY_OFFICER.put(officer.getUUID(), key);
        if (dropped != null && !dropped.equals(key)) BY_CASE.remove(dropped);
    }
    /** effects: no officer carries the case's summons any more. */
    static void release(UUID player, VillageId village) {
        var officer = BY_CASE.remove(new Assignment(player, village));
        if (officer != null) BY_OFFICER.remove(officer);
    }
    /** effects: the officer carrying the case's summons, if any. */
    public static Optional<UUID> officerFor(UUID player, VillageId village) { return Optional.ofNullable(BY_CASE.get(new Assignment(player, village))); }
    /** effects: forgets every summons the player's cases had out: a session's memory. */
    static void forget(UUID player) {
        BY_CASE.keySet().removeIf(a -> {
            if (!a.player().equals(player)) return false;
            BY_OFFICER.remove(BY_CASE.get(a));
            return true;
        });
    }

    /** effects: the player the officer should go to or wait before: one whose case it carries,
     * online in the officer's level, with the case WANTED or SUMMONED; otherwise null, and a stale
     * assignment is dropped. */
    static ServerPlayer summonee(PathfinderMob officer) {
        var a = BY_OFFICER.get(officer.getUUID());
        if (a == null) return null;
        var server = officer.getServer();
        var player = server == null ? null : server.getPlayerList().getPlayer(a.player());
        var entry = player == null ? Optional.<Docket.Entry>empty() : Docket.get(server).get(a.player(), a.village());
        boolean live = player != null && player.isAlive() && player.level() == officer.level() && entry.isPresent()
                && (entry.get().lawCase().state() == Case.State.WANTED || entry.get().lawCase().state() == Case.State.SUMMONED);
        if (!live) { release(a.player(), a.village()); return null; }
        return player;
    }
    /** effects: the officer stands within reach of the player: a WANTED case is served. */
    static void reached(PathfinderMob officer, ServerPlayer player) {
        var a = BY_OFFICER.get(officer.getUUID());
        if (a == null) return;
        Docket.get(player.server).get(a.player(), a.village()).ifPresent(entry -> {
            if (entry.lawCase().state() == Case.State.WANTED) Law.move(player, entry, entry.lawCase().served());
        });
    }

    /** effects: the choice the entry puts on the player's screen: the summons when SUMMONED, the
     * debt when DEBT; empty in any other state. */
    public static Optional<Notice> notice(ServerPlayer player, Docket.Entry entry) {
        var c = entry.lawCase();
        Kind kind = switch (c.state()) {
            case SUMMONED -> Kind.SUMMONS;
            case DEBT -> Kind.DEBT;
            default -> null;
        };
        if (kind == null) return Optional.empty();
        return Optional.of(new Notice(entry.village().toString(), entry.villageName(), kind, c.fine(), c.worst().key(), c.crimes(), carrying(player),
                (int) (LawConfig.leaveTicks() / 20), LawConfig.banishTicks() / 24000.0));
    }
    /** effects: puts the entry's {@link #notice} on the player's screen, when it has one and the
     * player's connection took the channel; returns the notice. */
    public static Optional<Notice> open(ServerPlayer player, Docket.Entry entry) {
        var notice = notice(player, entry);
        notice.ifPresent(n -> send(player, n));
        return notice;
    }
    /** effects: sends the payload when the player is a real connection that took the channel at
     * login; a machine's fake player has no screen. */
    static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (!(player instanceof FakePlayer) && player.connection != null && player.connection.hasChannel(payload.type())) PacketDistributor.sendToPlayer(player, payload);
    }

    /** effects: applies the player's answer to their case with the village it names: PAY settles a
     * payable case the player can afford, LEAVE puts a SUMMONED player on the clock; anything else
     * changes nothing. */
    public static void answer(ServerPlayer player, Answer answer) {
        VillageId village;
        try { village = VillageId.parse(answer.village()); } catch (IllegalArgumentException e) { return; }
        var found = Docket.get(player.server).get(player.getUUID(), village);
        if (found.isEmpty()) return;
        var entry = found.get();
        switch (answer.choice()) {
            case PAY -> pay(player, entry);
            case LEAVE -> Law.move(player, entry, entry.lawCase().left(player.server.overworld().getGameTime(), LawConfig.leaveTicks()));
            case DISMISS -> {}
        }
    }
    /** effects: when the case is payable and the player carries enough, takes the fine in emeralds
     * then blocks of emerald from everything they carry, bags included, gives the change back, and
     * clears the case; otherwise tells them what they are short and changes nothing. */
    static void pay(ServerPlayer player, Docket.Entry entry) {
        var c = entry.lawCase();
        if (!c.payable()) return;
        int emeralds = Carried.count(player, Items.EMERALD), blocks = Carried.count(player, Items.EMERALD_BLOCK);
        var plan = Payment.plan(c.fine(), emeralds, blocks);
        if (plan.isEmpty()) { short_(player, entry, Payment.worth(emeralds, blocks)); return; }
        // Two takes, each all or nothing; the counts were read in this call, so both succeed, and
        // should the second not, the first is handed back rather than charged.
        if (!Carried.take(player, s -> s.is(Items.EMERALD), plan.get().emeralds(), s -> {})) { short_(player, entry, Payment.worth(emeralds, blocks)); return; }
        if (!Carried.take(player, s -> s.is(Items.EMERALD_BLOCK), plan.get().blocks(), s -> {})) {
            Carried.giveOrDrop(player, new ItemStack(Items.EMERALD, plan.get().emeralds()));
            short_(player, entry, Payment.worth(emeralds, blocks));
            return;
        }
        if (plan.get().change() > 0) Carried.giveOrDrop(player, new ItemStack(Items.EMERALD, plan.get().change()));
        Law.move(player, entry, c.paid());
    }
    private static void short_(ServerPlayer player, Docket.Entry entry, int carrying) {
        player.sendSystemMessage(Component.translatable("message.villagelaw.short", entry.villageName(), entry.lawCase().fine(), carrying).withStyle(ChatFormatting.RED));
    }
    /** effects: what the player carries in emeralds and blocks of emerald, as emeralds. */
    static int carrying(ServerPlayer player) { return Payment.worth(Carried.count(player, Items.EMERALD), Carried.count(player, Items.EMERALD_BLOCK)); }

    /** effects: a click on an officer consumes it when {@link #use} put a choice on screen, so
     * Guard Villagers' own inventory screen does not open over it. */
    static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player) || !Officers.isOfficer(event.getTarget())) return;
        if (use(player, event.getTarget()).isEmpty()) return;
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
    /** effects: the player uses an officer standing in the vicinity of a village they have a case
     * with: a WANTED case is served on the spot, a SUMMONED one put back on screen, a debt offered
     * for payment; returns the choice put on screen. A fleeing player is told the time they have
     * left, and nothing goes on screen. */
    public static Optional<Notice> use(ServerPlayer player, Entity officer) {
        var docket = Docket.get(player.server);
        if (!Officers.isOfficer(officer) || !docket.hasAny(player.getUUID())) return Optional.empty();
        for (var entry : docket.of(player.getUUID())) {
            if (!entry.covers(officer)) continue;
            var c = entry.lawCase();
            switch (c.state()) {
                case WANTED -> {
                    if (officer instanceof Mob mob) assign(mob, player, entry.village());
                    return notice(player, Law.move(player, entry, c.served()));
                }
                case SUMMONED, DEBT -> { return open(player, entry); }
                case FLEEING -> player.displayClientMessage(Component.translatable("message.villagelaw.fleeing_reminder", entry.villageName(),
                        Countdown.format(c.ticksLeft(player.server.overworld().getGameTime()))).withStyle(ChatFormatting.GOLD), true);
                default -> {}
            }
        }
        return Optional.empty();
    }
}
