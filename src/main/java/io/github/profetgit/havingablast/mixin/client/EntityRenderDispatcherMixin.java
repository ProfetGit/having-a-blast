package io.github.profetgit.havingablast.mixin.client;

import io.github.profetgit.havingablast.client.Drops;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Item drops of a blast stay hidden until their debris pops (Drops), so the drops don't show twice. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    //? if >=26.3 {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void havingablast$hideDrops(E entity, Frustum frustum, double x, double y, double z, float partial, CallbackInfoReturnable<Boolean> cir) {
        if (Drops.hidden(entity)) cir.setReturnValue(false);
    }
    //?} else {
    /*@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void havingablast$hideDrops(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (Drops.hidden(entity)) cir.setReturnValue(false);
    }
    *///?}
}
