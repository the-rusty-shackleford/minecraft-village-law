/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw;

import com.chunkworks.villagedeed.api.Village;
import com.chunkworks.villagedeed.api.VillageId;
import com.chunkworks.villagelaw.domain.Area;
import com.chunkworks.villagelaw.domain.Case;
import com.chunkworks.villagelaw.domain.Severity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Every open case, by player and by village: the overworld's saved data {@code villagelaw_docket},
 * so one docket covers villages in every dimension.
 * <p>Each entry keeps the village's name, dimension and bounds as they were when the case opened,
 * so the patrol can tell whether a player is inside a village whose chunks are not loaded.
 * <p>AF: {@code cases.get(p).get(v)} is player p's case with village v; a player with no entry has
 * no case anywhere.
 * <p>RI: every stored entry's case is open; no player's map is empty; each entry sits under its own
 * player and village. */
public final class Docket extends SavedData {
    private static final SavedData.Factory<Docket> FACTORY = new SavedData.Factory<>(Docket::new, Docket::load);
    private final Map<UUID, Map<VillageId, Entry>> cases = new HashMap<>();

    /** A player's case with one village, and what the docket remembers of both. Immutable. */
    public record Entry(UUID player, String playerName, VillageId village, String villageName, ResourceKey<Level> dimension, Area bounds, Case lawCase) {
        /** effects: a new entry for the player's first crime in the village. */
        public static Entry open(ServerPlayer player, Village village, Case lawCase) {
            var b = village.bounds();
            return new Entry(player.getUUID(), player.getScoreboardName(), village.id(), village.name(), player.level().dimension(),
                    new Area(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()), lawCase);
        }
        /** effects: this entry with another case. */
        public Entry with(Case next) { return new Entry(player, playerName, village, villageName, dimension, bounds, next); }
        /** effects: the village's bounds grown by the configured vicinity. */
        public Area vicinity() { return bounds.inflate(LawConfig.vicinity()); }
        /** effects: whether the entity stands in this village's vicinity, in its dimension. */
        public boolean covers(Entity entity) {
            return entity.level().dimension().equals(dimension) && vicinity().contains(entity.getX(), entity.getY(), entity.getZ());
        }
        /** effects: the vicinity as a box for entity searches. */
        public AABB box() {
            var v = vicinity();
            return new AABB(v.minX(), v.minY(), v.minZ(), v.maxX() + 1.0, v.maxY() + 1.0, v.maxZ() + 1.0);
        }
    }

    private Docket() {}
    /** effects: the server's docket. */
    public static Docket get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, VillageLaw.ID + "_docket");
    }
    /** effects: the docket the tag holds; an entry that does not parse is skipped and logged.
     * Public so a test can round-trip one; the game reads through the factory. */
    public static Docket load(CompoundTag tag, HolderLookup.Provider registries) {
        var docket = new Docket();
        var list = tag.getList("Cases", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var c = list.getCompound(i);
            try {
                var b = c.getIntArray("Bounds");
                var lawCase = new Case(Case.State.valueOf(c.getString("State")), c.getInt("Fine"), Severity.valueOf(c.getString("Worst")), c.getInt("Crimes"), c.getLong("Until"));
                var entry = new Entry(c.getUUID("Player"), c.getString("PlayerName"), VillageId.parse(c.getString("Village")), c.getString("VillageName"),
                        ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(c.getString("Dimension"))), new Area(b[0], b[1], b[2], b[3], b[4], b[5]), lawCase);
                docket.put(entry);
            } catch (RuntimeException e) {
                VillageLaw.LOGGER.warn("skipped a case the docket could not read: {}", c, e);
            }
        }
        docket.setDirty(false);
        return docket;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var byVillage : cases.values()) for (var e : byVillage.values()) {
            var c = new CompoundTag();
            c.putUUID("Player", e.player());
            c.putString("PlayerName", e.playerName());
            c.putString("Village", e.village().toString());
            c.putString("VillageName", e.villageName());
            c.putString("Dimension", e.dimension().location().toString());
            var b = e.bounds();
            c.putIntArray("Bounds", new int[] { b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ() });
            var k = e.lawCase();
            c.putString("State", k.state().name());
            c.putInt("Fine", k.fine());
            c.putString("Worst", k.worst().name());
            c.putInt("Crimes", k.crimes());
            c.putLong("Until", k.until());
            list.add(c);
        }
        tag.put("Cases", list);
        return tag;
    }

    /** effects: the player's open cases, in no order. */
    public List<Entry> of(UUID player) {
        var byVillage = cases.get(player);
        return byVillage == null ? List.of() : List.copyOf(byVillage.values());
    }
    /** effects: whether the player has an open case anywhere. */
    public boolean hasAny(UUID player) { return cases.containsKey(player); }
    /** effects: the player's case with the village, if open. */
    public Optional<Entry> get(UUID player, VillageId village) {
        var byVillage = cases.get(player);
        return byVillage == null ? Optional.empty() : Optional.ofNullable(byVillage.get(village));
    }
    /** effects: every player with an open case. */
    public Set<UUID> players() { return Set.copyOf(cases.keySet()); }
    /** effects: records the entry over any earlier one for its player and village; a cleared case
     * is removed instead. */
    public void put(Entry entry) {
        if (!entry.lawCase().open()) { remove(entry.player(), entry.village()); return; }
        cases.computeIfAbsent(entry.player(), p -> new HashMap<>()).put(entry.village(), entry);
        setDirty();
    }
    /** effects: drops the player's case with the village, if any. */
    public void remove(UUID player, VillageId village) {
        var byVillage = cases.get(player);
        if (byVillage == null || byVillage.remove(village) == null) return;
        if (byVillage.isEmpty()) cases.remove(player);
        setDirty();
    }
}
