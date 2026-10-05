package io.github.profetgit.havingablast.mixin.client;

//? if <1.21.2 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.profetgit.havingablast.client.Blasts;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// The cartoon puff replaces vanilla's explosion sprite, which the client's own Explosion spawns in finalizeExplosion.
@Mixin(Explosion.class)
public abstract class ExplosionParticlesMixin {
    @WrapOperation(method = "finalizeExplosion", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void havingablast$puff(Level level, ParticleOptions p, double x, double y, double z, double vx, double vy, double vz, Operation<Void> original) {
        if (!Blasts.replacing) original.call(level, p, x, y, z, vx, vy, vz);
    }
}
*///?}
//? if >=1.21.2 {
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

// The client's own Explosion (1.21.1 and older) spawns the puff itself: nothing to do since 1.21.2.
@Mixin(Minecraft.class)
public abstract class ExplosionParticlesMixin {
}
//?}
