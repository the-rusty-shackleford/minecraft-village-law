/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.gametest;

import com.chunkworks.villagedeed.village.StructureVillages;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import java.util.UUID;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Sheep;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.common.entities.Guard;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** A village for the tests: the {@code villagelaw_gametest:hut} structure, an 8x5x8 box the
 * gametest datapack tags as a village and as Thief-protected, generated and registered inside the
 * test's arena the way worldgen registers a structure, then furnished by the test. */
public final class Huts {
    public static final ResourceKey<Structure> HUT = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath("villagelaw_gametest", "hut"));
    /** The planted hut: the structure's box, in world coordinates. */
    public record Hut(StructureStart start, BoundingBox box) {
        /** effects: the world position at an offset from the box's minimum corner. */
        public BlockPos at(int dx, int dy, int dz) { return new BlockPos(box.minX() + dx, box.minY() + dy, box.minZ() + dz); }
        public BlockPos centre() { return box.getCenter(); }
    }
    private Huts() {}

    /** effects: generates the hut in a chunk that lies whole inside the test's arena and records
     * its start and its references in the chunks it covers, as the chunk generator would, so the
     * structure manager (and through it Thief) finds it; asserts the box lies inside the arena. */
    public static Hut plant(GameTestHelper h) { return plant(h, 0); }
    /** effects: as {@link #plant(GameTestHelper)}, in the chunk {@code dx} chunks east of the
     * arena's middle one, so a test can hold two villages. */
    public static Hut plant(GameTestHelper h, int dx) {
        var level = h.getLevel();
        var bounds = h.getBounds();
        int minCx = (int) Math.ceil(bounds.minX / 16.0), maxCx = (int) Math.floor((bounds.maxX + 1) / 16.0) - 1;
        int minCz = (int) Math.ceil(bounds.minZ / 16.0), maxCz = (int) Math.floor((bounds.maxZ + 1) / 16.0) - 1;
        h.assertTrue(minCx + 1 <= maxCx - 1 && minCz + 1 <= maxCz - 1, "the arena holds a chunk with whole chunks around it: " + bounds);
        int cx = (minCx + maxCx) / 2 + dx, cz = (minCz + maxCz) / 2;
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(HUT);
        var generator = level.getChunkSource().getGenerator();
        var start = structure.generate(level.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
                level.getStructureManager(), level.getSeed(), new ChunkPos(cx, cz), 0, level, biome -> true);
        h.assertTrue(start.isValid(), "the hut structure generates");
        var box = start.getBoundingBox();
        h.assertTrue(bounds.contains(box.minX(), box.minY(), box.minZ()) && bounds.contains(box.maxX(), box.maxY(), box.maxZ()),
                "the hut lies inside the arena: " + box + " in " + bounds);
        var home = level.getChunk(cx, cz);
        level.structureManager().setStartForStructure(SectionPos.bottomOf(home), structure, start, home);
        long reference = new ChunkPos(cx, cz).toLong();
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
            var chunk = level.getChunk(x, z);
            level.structureManager().addReferenceForStructure(SectionPos.bottomOf(chunk), structure, reference, chunk);
        }
        h.assertTrue(level.structureManager().getStructureWithPieceAt(box.getCenter(), StructureVillages.VILLAGES).isValid(), "the hut is a village");
        return new Hut(start, box);
    }
    /** effects: a stone floor at the box's bottom layer and stone walls one block high around the
     * floor's edge; the inside is left open above the floor. */
    public static void build(GameTestHelper h, Hut hut) {
        var level = h.getLevel();
        var box = hut.box();
        for (int x = box.minX(); x <= box.maxX(); x++) for (int z = box.minZ(); z <= box.maxZ(); z++) {
            level.setBlock(new BlockPos(x, box.minY(), z), Blocks.STONE.defaultBlockState(), 3);
            boolean edge = x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ();
            if (edge) level.setBlock(new BlockPos(x, box.minY() + 1, z), Blocks.STONE.defaultBlockState(), 3);
        }
    }
    /** effects: an adult villager standing still at the position, employed at that trade level
     * when the profession is a trade. */
    public static Villager villager(GameTestHelper h, BlockPos at, VillagerProfession profession, int level) {
        var villager = EntityType.VILLAGER.create(h.getLevel());
        villager.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        villager.setNoAi(true);
        villager.setPersistenceRequired();
        if (profession != VillagerProfession.NONE) villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(level));
        h.getLevel().addFreshEntity(villager);
        return villager;
    }
    /** effects: one of Guard Villagers' guards at the position, its AI running, empty-handed (so it
     * fights with its melee goal, as a guard with a sword does), joined the way a spawned guard
     * joins, so Village Law gives it its chase speed and its summons goal. */
    public static Guard guard(GameTestHelper h, BlockPos at) {
        var guard = GuardEntityType.GUARD.get().create(h.getLevel());
        guard.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        guard.setPersistenceRequired();
        h.getLevel().addFreshEntity(guard);
        return guard;
    }
    /** effects: an iron golem at the position, its AI running. */
    public static IronGolem golem(GameTestHelper h, BlockPos at) {
        var golem = EntityType.IRON_GOLEM.create(h.getLevel());
        golem.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        golem.setPersistenceRequired();
        h.getLevel().addFreshEntity(golem);
        return golem;
    }
    /** effects: a sheep standing still at the position. */
    public static Sheep sheep(GameTestHelper h, BlockPos at) {
        var sheep = EntityType.SHEEP.create(h.getLevel());
        sheep.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        sheep.setNoAi(true);
        h.getLevel().addFreshEntity(sheep);
        return sheep;
    }
    /** effects: a survival-mode mock player with an empty inventory standing at the position,
     * holding what is given, joined to the server over an embedded connection the way the
     * framework's own mock joins. Not the framework's mock: that one reports itself creative,
     * and Thief's witnesses look away from creative players. */
    public static ServerPlayer player(GameTestHelper h, BlockPos at, ItemStack... holding) { return player(h, at, "test-mock-player", holding); }
    /** effects: as {@link #player(GameTestHelper, BlockPos, ItemStack...)}, under that name. */
    public static ServerPlayer player(GameTestHelper h, BlockPos at, String name, ItemStack... holding) {
        var server = h.getLevel().getServer();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        var player = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override public boolean isSpectator() { return false; }
        };
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        // The gametest server's default game mode is creative, which the join applies.
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        for (var stack : holding) player.getInventory().add(stack);
        return player;
    }
}
