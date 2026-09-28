package io.github.profetgit.havingablast.mixin.common;

import io.github.profetgit.havingablast.repair.Repair;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    /** No item, XP or falling-block spawns from blocks a repairing blast breaks: they come back instead. */
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void havingablast$noDrops(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Repair.blocksSpawn((ServerLevel) (Object) this, entity)) cir.setReturnValue(false);
    }

    @Inject(method = "levelEvent", at = @At("HEAD"))
    private void havingablast$countEvent(net.minecraft.world.entity.Entity source, int type, net.minecraft.core.BlockPos pos, int data, CallbackInfo ci) {
        if (io.github.profetgit.havingablast.dev.BlastCount.ACTIVE) io.github.profetgit.havingablast.dev.BlastCount.onEvent((ServerLevel) (Object) this, type, pos);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void havingablast$repair(BooleanSupplier hasTime, CallbackInfo ci) {
        Repair.tick((ServerLevel) (Object) this);
        if (io.github.profetgit.havingablast.dev.RepairTest.ACTIVE) io.github.profetgit.havingablast.dev.RepairTest.tick((ServerLevel) (Object) this);
    }
}
