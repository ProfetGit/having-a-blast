package io.github.profetgit.havingablast.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** One explosion seen by this client: its capture window, its debris and the effects timeline. */
final class Blast {
    final Vec3 center;
    final float radius;
    /** Largest distance a vanilla blast ray can reach: power up to 1.3 r, spent 0.225 per 0.3-block step. */
    final double reach;
    final int openedTick;
    /** Client tick of the latest update captured for this blast, -1 until the first. */
    int lastCapture = -1;
    /** Render clock of the first frame that drew this blast; NaN until then. */
    float t0 = Float.NaN;
    final List<Debris> debris = new ArrayList<>();
    final it.unimi.dsi.fastutil.ints.IntArrayList items = new it.unimi.dsi.fastutil.ints.IntArrayList();
    /** Positions the blast removed outright, and break-event removals waiting to be judged against them. */
    final it.unimi.dsi.fastutil.longs.LongOpenHashSet removed = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    final java.util.List<Object[]> marked = new java.util.ArrayList<>();
    int captured, overflow, baked;
    Blasts.Physics physics;
    net.minecraft.util.RandomSource rng;
    boolean closed, scheduled, sorted, farFromCamera;
    Boom.Params fx;
    long[] rim;
    net.minecraft.world.level.block.state.BlockState[] rimState;
    float[] rimDelay;
    int pops;
    float lastPopSound = -99;
    final long seed;

    Blast(Vec3 center, float radius, int tick, long seed) {
        this.center = center;
        this.radius = radius;
        // before 1.21.9 the packet has no radius (Blasts.onExplosionPacket guesses one for the look), so a big blast's
        // blocks are looked for as far as the biggest common one (a wither's spawn, 7) could have thrown them
        //? if >=1.21.9 {
        float capture = radius;
        //?} else {
        /*float capture = Math.max(radius, 7f);
        *///?}
        this.reach = capture * 1.3 / 0.225 * 0.3 + 1.0;
        this.openedTick = tick;
        this.seed = seed;
    }
}
