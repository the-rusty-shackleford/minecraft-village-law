/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.gametest;

import com.chunkworks.carried.api.Carried;
import com.chunkworks.villagelaw.domain.Payment;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.List;
import java.util.UUID;

/** Money and ground for the tests: a payer whose emeralds are only in a worn Backpacks+ bag (Village
 * Deed's fixture: the bag by registry id, filled through the vanilla container component, on a fake
 * player that is neither ticked nor tracked, so Backpacks+'s gear sync never reaches the other
 * tests' mock connections), and a flat stone track to time a run on. */
final class Wallets {
    /** The armour chest slot, where a worn bag sits. */
    private static final int CHEST = 38;
    private Wallets() {}

    /** effects: a survival fake player at the position wearing an expedition bag that holds the
     * cells, carrying nothing else. */
    static ServerPlayer bagged(GameTestHelper h, BlockPos at, String name, ItemStack... cells) {
        var bag = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("backpacksplus:expedition_backpack")));
        h.assertTrue(!bag.isEmpty() && !bag.is(Items.AIR), "Backpacks+'s expedition bag is registered");
        bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(cells)));
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        p.getInventory().clearContent();
        p.getInventory().setItem(CHEST, bag);
        p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return p;
    }
    /** effects: what the player carries in emeralds, a block counted at nine, bags included. */
    static int worth(ServerPlayer p) { return Payment.worth(Carried.count(p, Items.EMERALD), Carried.count(p, Items.EMERALD_BLOCK)); }

    /** A straight stone track running east, three blocks wide, along the arena's north edge. */
    record Track(BlockPos start) {
        /** effects: the standing place {@code dx} blocks east of the start. */
        BlockPos at(int dx) { return start.east(dx); }
    }
    /** effects: lays a stone track 48 blocks long and three wide, its middle row {@code z} blocks
     * in from the arena's north edge, and returns where a runner stands at its west end. */
    static Track strip(GameTestHelper h, int z) {
        for (int x = 1; x <= 48; x++) for (int dz = -1; dz <= 1; dz++) h.setBlock(x, 0, z + 1 + dz, Blocks.STONE);
        return new Track(h.absolutePos(new BlockPos(2, 1, z + 1)));
    }
}
