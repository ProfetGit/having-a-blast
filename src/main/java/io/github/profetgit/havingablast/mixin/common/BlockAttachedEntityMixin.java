package io.github.profetgit.havingablast.mixin.common;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.profetgit.havingablast.repair.Decor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Marks the periodic "lost its wall" drop, so a frame whose wall an aftershock took joins that repair. */
@Mixin(BlockAttachedEntity.class)
public abstract class BlockAttachedEntityMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/decoration/BlockAttachedEntity;dropItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V"))
    private void havingablast$lostWall(BlockAttachedEntity self, ServerLevel level, Entity breaker, Operation<Void> original) {
        int[] depth = Decor.TICK.get();
        depth[0]++;
        try {
            original.call(self, level, breaker);
        } finally {
            depth[0]--;
        }
    }
}
