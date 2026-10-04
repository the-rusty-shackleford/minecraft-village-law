/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.client;

import com.chunkworks.villagelaw.Summons;
import com.chunkworks.villagelaw.VillageLaw;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** The client's registrations: the summons screen and the countdown take what the server sends,
 * and the countdown draws as a layer over the boss bars'. */
@EventBusSubscriber(modid = VillageLaw.ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        Summons.Client.receivers(SummonsScreen::accept, CountdownHud::accept);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> CountdownHud.clear());
    }
    @SubscribeEvent public static void layers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, VillageLaw.id("countdown"), CountdownHud::render);
    }
}
