package io.github.profetgit.havingablast.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

/** Particles and sounds of the cartoon: crumbs, puffs of air, plops. */
final class Fx {
    private Fx() {
    }

    static void crumbs(ClientLevel level, BlockState state, double x, double y, double z, int n) {
        if (state.isAir()) return;
        BlockParticleOption opt = new BlockParticleOption(ParticleTypes.BLOCK, state);
        for (int i = 0; i < n; i++) {
            double ox = (Blasts.RNG.nextDouble() - 0.5) * 0.6, oy = (Blasts.RNG.nextDouble() - 0.5) * 0.6, oz = (Blasts.RNG.nextDouble() - 0.5) * 0.6;
            level.addParticle(opt, x + ox, y + oy, z + oz, ox * 0.3, 0.12 + Blasts.RNG.nextDouble() * 0.1, oz * 0.3);
        }
    }

    static void gust(ClientLevel level, double x, double y, double z) {
        level.addParticle(ParticleTypes.SMALL_GUST, x, y, z, 0, 0, 0);
    }

    static void plop(ClientLevel level, BlockState state, double x, double y, double z, float pitch) {
        level.playLocalSound(x, y, z, SoundEvents.CHICKEN_EGG, SoundSource.BLOCKS, 0.6f, pitch, false);
        level.playLocalSound(x, y, z, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.45f, 0.9f + pitch * 0.1f, false);
    }
}
