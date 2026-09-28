package io.github.profetgit.havingablast.mixin.common;

import io.github.profetgit.havingablast.repair.Repair;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fire a repairing blast started (and fire spreading from it) is recorded: what it burns comes back, and it goes out. */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    private static final ThreadLocal<ArrayDeque<Object>> havingablast$fires = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "tick", at = @At("HEAD"))
    private void havingablast$fire(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        Object t = Repair.beginFire(level, pos);
        havingablast$fires.get().push(t == null ? Boolean.FALSE : t);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void havingablast$fireEnd(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        ArrayDeque<Object> stack = havingablast$fires.get();
        if (!stack.isEmpty()) Repair.endAftershock(stack.pop());
    }
}
