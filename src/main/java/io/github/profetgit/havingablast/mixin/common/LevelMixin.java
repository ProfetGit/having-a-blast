package io.github.profetgit.havingablast.mixin.common;

import io.github.profetgit.havingablast.repair.Repair;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {
    private static final ThreadLocal<ArrayDeque<Object>> havingablast$aftershocks = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void havingablast$record(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
        Repair.onSetBlock((Level) (Object) this, pos);
        if (io.github.profetgit.havingablast.dev.BlastCount.ACTIVE) io.github.profetgit.havingablast.dev.BlastCount.onSetBlock((Level) (Object) this, pos);
    }

    /** A block breaking by itself (no entity) beside a hole waiting for repair joins that repair. */
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"))
    private void havingablast$aftershock(BlockPos pos, boolean drop, Entity by, int recursion, CallbackInfoReturnable<Boolean> cir) {
        Object t = by == null ? Repair.beginAftershock((Level) (Object) this, pos) : null;
        havingablast$aftershocks.get().push(t == null ? Boolean.FALSE : t);
    }

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("RETURN"))
    private void havingablast$aftershockEnd(BlockPos pos, boolean drop, Entity by, int recursion, CallbackInfoReturnable<Boolean> cir) {
        ArrayDeque<Object> stack = havingablast$aftershocks.get();
        if (!stack.isEmpty()) Repair.endAftershock(stack.pop());
    }
}
