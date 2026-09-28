package io.github.profetgit.havingablast.mixin.common;

import io.github.profetgit.havingablast.repair.Decor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An armor stand a repairing blast would break is kept, equipment and all, and stands again after the repair. */
@Mixin(ArmorStand.class)
public abstract class ArmorStandMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void havingablast$keep(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source.is(DamageTypeTags.IS_EXPLOSION) && Decor.onStandBlast((ArmorStand) (Object) this, level, source)) cir.setReturnValue(false);
    }
}
