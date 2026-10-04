/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw;

import com.chunkworks.villagelaw.domain.Chase;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The village's enforcers: officers (the {@code villagelaw:officers} tag, Guard Villagers' guards)
 * who serve summonses and chase at a sprint, and iron golems, who only fight. Here: an officer's
 * speed and summons goal as it loads, the hold-back that keeps enforcers off a player whose case is
 * open and peaceful, rallying them against a hostile one and standing them down after.
 * <p>The hold-back answers {@code LivingChangeTargetEvent}, fired by every {@code Mob.setTarget},
 * so it reaches Guard Villagers' reputation goal, the golem's village defence, Thief's own handler
 * and any other goal alike. It lets a target through when the player has hurt a villager, an officer
 * or a golem in the last {@link #GRACE_TICKS}: a fight a player starts is still a fight. */
public final class Officers {
    public static final TagKey<EntityType<?>> OFFICERS = TagKey.create(Registries.ENTITY_TYPE, VillageLaw.id("officers"));
    /** How long hurting a villager or an enforcer lets the enforcers fight back: five seconds. */
    public static final long GRACE_TICKS = 100;
    /** The game time each player last hurt a villager, an officer or a golem. */
    private static final Map<UUID, Long> LAST_AGGRESSION = new HashMap<>();
    private Officers() {}

    /** effects: whether the entity is an officer. */
    public static boolean isOfficer(Entity entity) { return entity.getType().is(OFFICERS); }
    /** effects: whether the entity enforces the law: an officer or an iron golem. */
    public static boolean isEnforcer(Entity entity) { return entity instanceof IronGolem || isOfficer(entity); }

    /** effects: an officer joining a server level gets the movement-speed base whose chase is the
     * configured share of a sprint (its saved base is overwritten, so guards already in the world
     * are corrected too) and, once, the summons goal. */
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof PathfinderMob officer) || !isOfficer(officer)) return;
        var speed = officer.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(chaseBase());
        if (officer.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof SummonGoal)) officer.goalSelector.addGoal(SummonGoal.PRIORITY, new SummonGoal(officer));
    }
    /** effects: the movement-speed base an officer runs at. */
    public static double chaseBase() { return Chase.baseFor(LawConfig.chaseSpeed(), Chase.GUARD_CHASE_MODIFIER); }

    /** effects: cancels an enforcer's targeting of a player its village holds back. */
    static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && isEnforcer(event.getEntity()) && heldBack(event.getEntity(), player)) event.setCanceled(true);
    }
    /** effects: whether the enforcer may not target the player: the player has an open case with
     * the village whose vicinity the enforcer stands in, the case is not hostile, and the player has
     * hurt no villager or enforcer within the grace. */
    public static boolean heldBack(LivingEntity enforcer, ServerPlayer player) {
        var docket = Docket.get(player.server);
        if (!docket.hasAny(player.getUUID()) || aggressive(player)) return false;
        for (var entry : docket.of(player.getUUID())) if (entry.covers(enforcer)) return entry.lawCase().holdsBack(entry.covers(player));
        return false;
    }
    /** effects: records a player hurting a villager or an enforcer. */
    static void onHurt(LivingIncomingDamageEvent event) {
        var victim = event.getEntity();
        if (event.getSource().getEntity() instanceof ServerPlayer player && (victim instanceof Villager || isEnforcer(victim)))
            LAST_AGGRESSION.put(player.getUUID(), player.server.overworld().getGameTime());
    }
    /** effects: whether the player hurt a villager or an enforcer within the grace. */
    public static boolean aggressive(ServerPlayer player) {
        var last = LAST_AGGRESSION.get(player.getUUID());
        return last != null && player.server.overworld().getGameTime() - last <= GRACE_TICKS;
    }
    /** effects: forgets the player's aggression; a session's memory. */
    static void forget(UUID player) { LAST_AGGRESSION.remove(player); }

    /** How far past the vicinity a stand-down reaches: a guard chasing a player over the line is
     * just past it. */
    private static final int STAND_DOWN_MARGIN = 16;

    /** effects: the enforcers in the player's level within {@code margin} blocks of the entry's
     * village vicinity. */
    static List<Mob> enforcers(ServerPlayer player, Docket.Entry entry, int margin) {
        if (!player.level().dimension().equals(entry.dimension())) return List.of();
        return player.serverLevel().getEntitiesOfClass(Mob.class, entry.box().inflate(margin), m -> m.isAlive() && isEnforcer(m));
    }
    /** effects: every enforcer in the village's vicinity not already fighting turns on the player. */
    static void rally(ServerPlayer player, Docket.Entry entry) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator()) return;
        for (var mob : enforcers(player, entry, 0)) if (mob.getTarget() == null) mob.setTarget(player);
    }
    /** effects: every enforcer of the village fighting the player drops them and its anger at
     * them, so a golem's or guard's grudge does not re-target the player the next tick. */
    static void standDown(ServerPlayer player, Docket.Entry entry) {
        for (var mob : enforcers(player, entry, STAND_DOWN_MARGIN)) {
            boolean angry = mob instanceof NeutralMob neutral && player.getUUID().equals(neutral.getPersistentAngerTarget());
            if (angry) ((NeutralMob) mob).stopBeingAngry();
            else if (mob.getTarget() == player) mob.setTarget(null);
        }
    }
}
