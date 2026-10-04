/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.mixin;

import com.chunkworks.villagelaw.Reports;
import io.github.mortuusars.thief.world.Crime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Switches off Thief's own guard attack where Village Law takes the crime instead.
 * {@code Crime.shouldGuardsAttack} is asked only by Thief's guard witness handler, for each guard or
 * golem that saw a crime, before the crime's event fires; answering false there leaves every other
 * reaction as it was (the villagers' gossip, "you have been seen", the stat, the event), and the
 * event opens the case. Where no officer saw the crime, or it happened outside any village the
 * protocol knows, Thief's attack stands. */
@Mixin(Crime.class)
abstract class CrimeMixin {
    @Inject(method = "shouldGuardsAttack", at = @At("HEAD"), cancellable = true)
    private void villagelaw$lawTakesTheCrime(ServerLevel level, LivingEntity criminal, CallbackInfoReturnable<Boolean> cir) {
        if (Reports.policed(level, criminal)) cir.setReturnValue(false);
    }
}
