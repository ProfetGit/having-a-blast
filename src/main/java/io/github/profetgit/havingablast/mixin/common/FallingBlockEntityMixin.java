package io.github.profetgit.havingablast.mixin.common;

import io.github.profetgit.havingablast.repair.Repair;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sand or gravel that loses the block under it to a repairing blast is taken into the repair instead of falling. */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin {
    private static final ThreadLocal<ArrayDeque<Object>> havingablast$falls = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "fall", at = @At("HEAD"))
    private static void havingablast$fall(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<FallingBlockEntity> cir) {
        Object t = Repair.beginAftershock(level, pos);
        havingablast$falls.get().push(t == null ? Boolean.FALSE : t);
    }

    @Inject(method = "fall", at = @At("RETURN"))
    private static void havingablast$fallEnd(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<FallingBlockEntity> cir) {
        ArrayDeque<Object> stack = havingablast$falls.get();
        if (!stack.isEmpty()) Repair.endAftershock(stack.pop());
    }
}
