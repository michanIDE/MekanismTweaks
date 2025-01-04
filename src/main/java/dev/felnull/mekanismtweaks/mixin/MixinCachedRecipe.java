package dev.felnull.mekanismtweaks.mixin;


import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
// import mekanism.common.tile.prefab.TileEntityProgressMachine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntSupplier;

@Mixin(value = CachedRecipe.class, remap = false)
public abstract class MixinCachedRecipe<RECIPE extends MekanismRecipe> {

    @Shadow public abstract void process();

    @Shadow private IntSupplier baselineMaxOperations;
    @Shadow private IntSupplier requiredTicks;

    @Inject(method = "process", at = @At("HEAD"))
    private void setbaselineMaxOperations(CallbackInfo ci) {
        int requiredTicksInt = requiredTicks.getAsInt();
        if(requiredTicksInt < 0){
            int reqTime = -requiredTicksInt;
            baselineMaxOperations = () -> reqTime;
        }
    }
}
