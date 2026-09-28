package io.github.profetgit.havingablast.mixin.common;

import io.github.profetgit.havingablast.repair.Decor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A painting a repairing blast breaks (or leaves without a wall) is kept instead of dropping. */
@Mixin(Painting.class)
public abstract class PaintingMixin {
    @Inject(method = "dropItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void havingablast$keep(ServerLevel level, Entity breaker, CallbackInfo ci) {
        if (Decor.onDrop((Entity) (Object) this)) ci.cancel();
    }
}
