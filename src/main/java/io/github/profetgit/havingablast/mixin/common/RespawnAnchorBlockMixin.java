package io.github.profetgit.havingablast.mixin.common;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.profetgit.havingablast.repair.Kind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A respawn anchor's explosion has no source entity: name it, so the anchor toggle decides its repair. */
@Mixin(RespawnAnchorBlock.class)
public abstract class RespawnAnchorBlockMixin {
    @WrapOperation(method = "explode", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;Lnet/minecraft/world/phys/Vec3;FZLnet/minecraft/world/level/Level$ExplosionInteraction;)V"))
    private void havingablast$anchor(ServerLevel level, Entity source, DamageSource damage, ExplosionDamageCalculator calc, Vec3 at, float power, boolean fire,
        Level.ExplosionInteraction interaction, Operation<Void> original) {
        Kind outer = Kind.BLOCK_SOURCE.get();
        Kind.BLOCK_SOURCE.set(Kind.ANCHOR);
        try {
            original.call(level, source, damage, calc, at, power, fire, interaction);
        } finally {
            Kind.BLOCK_SOURCE.set(outer);
        }
    }
}
