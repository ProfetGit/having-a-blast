package io.github.profetgit.havingablast.mixin.common;

import io.github.profetgit.havingablast.repair.Repair;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records a repairing blast as a whole: the entity pass (frames, paintings, stands), the block pass and its fire. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
    @Inject(method = "explode", at = @At("HEAD"))
    private void havingablast$begin(CallbackInfoReturnable<Integer> cir) {
        Repair.beginExplosion((ServerExplosion) (Object) this);
    }

    @Inject(method = "explode", at = @At("RETURN"))
    private void havingablast$end(CallbackInfoReturnable<Integer> cir) {
        Repair.endExplosion((ServerExplosion) (Object) this);
    }

    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void havingablast$blocks(List<BlockPos> positions, CallbackInfo ci) {
        Repair.blockPass((ServerExplosion) (Object) this, true);
        io.github.profetgit.havingablast.dev.BlastCount.begin(((ServerExplosion) (Object) this).level(), ((ServerExplosion) (Object) this).center());
    }

    @Inject(method = "interactWithBlocks", at = @At("RETURN"))
    private void havingablast$blocksEnd(List<BlockPos> positions, CallbackInfo ci) {
        Repair.blockPass((ServerExplosion) (Object) this, false);
        io.github.profetgit.havingablast.dev.BlastCount.end();
    }
}
