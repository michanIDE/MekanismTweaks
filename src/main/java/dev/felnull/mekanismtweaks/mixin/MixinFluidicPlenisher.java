package dev.felnull.mekanismtweaks.mixin;


import dev.felnull.mekanismtweaks.Temp;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IConfigurable;
import mekanism.api.math.FloatingLong;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.machine.TileEntityFluidicPlenisher;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.fluids.FluidAttributes;

import java.util.EnumSet;
import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileEntityFluidicPlenisher.class, remap = false)
public abstract class MixinFluidicPlenisher extends TileEntityMekanism implements IConfigurable{

    @Shadow @Final private static EnumSet<Direction> dirs = EnumSet.complementOf(EnumSet.of(Direction.UP));

    @Shadow private final Set<BlockPos> activeNodes = new ObjectLinkedOpenHashSet<>();
    @Shadow public boolean finishedCalc;
    @Shadow public int ticksRequired;

    @Shadow public BasicFluidTank fluidTank;
    @Shadow private EnergyInventorySlot energySlot;

    @Shadow protected abstract void onUpdateServer();
    @Shadow protected abstract boolean canReplace(BlockPos pos, boolean checkNodes, boolean isPathfinding);
    @Shadow protected abstract boolean canExtractBucket();

    @Shadow @Final private Set<BlockPos> usedNodes = new ObjectOpenHashSet<>();

    public MixinFluidicPlenisher(BlockPos pos, boolean isHighTier) {
        super(null, pos, null);
    }

    @Inject(method = "onUpdateServer", at = @At(value = "INVOKE", target = "Lmekanism/common/tile/machine/TileEntityFluidicPlenisher;doPlenish()V", shift = At.Shift.AFTER))
    public void injected(CallbackInfo ci) {
        Temp.inject.accept(ticksRequired, this::onUpdateServer);
    }

    protected void doMoreProcess(int reqTimes) {
        super.onUpdateServer();
        energySlot.fillContainerOrConvert();
        inputSlot.fillTank(outputSlot);
        if (MekanismUtils.canFunction(this) && !fluidTank.isEmpty()) {
            FloatingLong energyPerTick = energyContainer.getEnergyPerTick();
            if (energyContainer.extract(energyPerTick, Action.SIMULATE, AutomationType.INTERNAL).equals(energyPerTick)) {
                if (!finishedCalc) {
                    energyContainer.extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
                }
                operatingTicks++;
                if (operatingTicks >= ticksRequired) {
                    operatingTicks = 0;
                    if (finishedCalc) {
                        BlockPos below = getBlockPos().below();
                        if (canReplace(below, false, false) && canExtractBucket() &&
                            WorldUtils.tryPlaceContainedLiquid(null, level, below, fluidTank.getFluid(), null)) {
                            level.gameEvent(GameEvent.FLUID_PLACE, below);
                            energyContainer.extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
                            fluidTank.extract(FluidAttributes.BUCKET_VOLUME, Action.EXECUTE, AutomationType.INTERNAL);
                        }
                    } else {
                        doPlenish();
                    }
                }
            }
        }
    }

    private void doPlenishes(int reqTimes) {
        if (usedNodes.size() >= MekanismConfig.general.maxPlenisherNodes.get()) {
            finishedCalc = true;
            return;
        }
        if (activeNodes.isEmpty()) {
            if (usedNodes.isEmpty()) {
                BlockPos below = getBlockPos().below();
                if (!canReplace(below, true, true)) {
                    finishedCalc = true;
                    return;
                }
                activeNodes.add(below);
            } else {
                finishedCalc = true;
                return;
            }
        }
        Set<BlockPos> toRemove = new ObjectOpenHashSet<>();
        for (BlockPos nodePos : activeNodes) {
            if (WorldUtils.isBlockLoaded(level, nodePos)) {
                if (canReplace(nodePos, true, false) && canExtractBucket() &&
                    WorldUtils.tryPlaceContainedLiquid(null, level, nodePos, fluidTank.getFluid(), null)) {
                    level.gameEvent(GameEvent.FLUID_PLACE, nodePos);
                    fluidTank.extract(FluidAttributes.BUCKET_VOLUME, Action.EXECUTE, AutomationType.INTERNAL);
                }
                for (Direction dir : dirs) {
                    BlockPos sidePos = nodePos.relative(dir);
                    if (WorldUtils.isBlockLoaded(level, sidePos) && canReplace(sidePos, true, true)) {
                        activeNodes.add(sidePos);
                    }
                }
                toRemove.add(nodePos);
                break;
            } else {
                toRemove.add(nodePos);
            }
        }
        usedNodes.addAll(toRemove);
        activeNodes.removeAll(toRemove);
    }
}
