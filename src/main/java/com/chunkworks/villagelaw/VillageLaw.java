/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Village Law: the guards keep the law the way Skyrim's do. A guard who sees a Thief crime runs to
 * the criminal and offers a choice: pay the fine, or leave within a minute and stay away two days.
 * Staying past the minute, or coming back early, brings the village's guards and golems down on
 * them; paying closes the matter and the village forgets the crime. Built on Thief (the crimes and
 * the witnesses), Guard Villagers (the officers) and Village Deed's village protocol (which
 * village); money is counted through Carried. */
@Mod(VillageLaw.ID)
public final class VillageLaw {
    public static final String ID = "villagelaw";
    public static final Logger LOGGER = LoggerFactory.getLogger("Village Law");

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    /** requires: the mod bus and container; effects: registers the config, the payloads and the
     * game listeners. */
    public VillageLaw(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, LawConfig.SPEC);
        bus.addListener(Summons::register);
        var game = NeoForge.EVENT_BUS;
        game.addListener(Reports::onCrime);
        game.addListener(Officers::onJoin);
        game.addListener(Officers::onChangeTarget);
        game.addListener(Officers::onHurt);
        game.addListener(Summons::onInteract);
        game.addListener(Patrol::onTick);
        game.addListener(Patrol::onDeath);
        game.addListener(Patrol::onLogout);
        game.addListener(Patrol::onLogin);
    }
}
