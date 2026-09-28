package io.github.profetgit.havingablast.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One blasted block. Its whole flight is baked when it is captured into up to MAX_SEG ballistic segments (start time,
 * centre, velocity; the last one rests), so every frame evaluates the exact parabola at its own partial tick; landings,
 * squash, settle and pop are curves of time too. Times are ticks: launch/popT relative to the blast's first drawn
 * frame, segment and contact times relative to the launch.
 */
final class Debris {
    static final int MAX_SEG = 8;
    static final float G = 0.08f;

    BlockState state;
    Blasts.Model model;
    BlockPos origin;
    Blast blast;
    /** Distance from the blast centre as a share of the blast's reach, 0..1. */
    float reach;
    float size;
    float launch;
    int nseg;
    final float[] segT = new float[MAX_SEG];
    final double[] segP = new double[MAX_SEG * 3];
    final double[] segV = new double[MAX_SEG * 3];
    final boolean[] segFall = new boolean[MAX_SEG];
    int ncontact;
    final float[] contactT = new float[MAX_SEG];
    final float[] contactK = new float[MAX_SEG];
    /** Flight time of the first landing (the tumble starts settling there) and of the final rest. */
    float firstContact = Float.MAX_VALUE, restT;
    float popT;
    int popIndex;
    boolean popped, dead, quiet;
    /** Fell out of the world or deep off an edge: no landing, no pop effects. */
    boolean lost;
    final Vector3f axis = new Vector3f();
    /** Tumble rate in radians per tick. */
    float spin;
    final Quaternionf restRot = new Quaternionf();
    final Vector3f launchDir = new Vector3f();
    float launchSpeed;
    float light = -1, sky = -1;
    /** Hidden item drops that appear when this debris pops. */
    final it.unimi.dsi.fastutil.ints.IntArrayList items = new it.unimi.dsi.fastutil.ints.IntArrayList(0);

    void addSegment(float t, double px, double py, double pz, double vx, double vy, double vz, boolean fall) {
        int i = nseg++;
        segT[i] = t;
        segP[i * 3] = px;
        segP[i * 3 + 1] = py;
        segP[i * 3 + 2] = pz;
        segV[i * 3] = vx;
        segV[i * 3 + 1] = vy;
        segV[i * 3 + 2] = vz;
        segFall[i] = fall;
    }

    void contact(float t, float k) {
        if (ncontact < MAX_SEG) {
            contactT[ncontact] = t;
            contactK[ncontact++] = k;
        }
        if (t < firstContact) firstContact = t;
    }

    /** Centre at flight time tf (ticks since launch) into out[0..2]. */
    void position(float tf, double[] out) {
        int i = nseg - 1;
        while (i > 0 && segT[i] > tf) i--;
        double dt = Math.max(0, tf - segT[i]);
        out[0] = segP[i * 3] + segV[i * 3] * dt;
        out[1] = segP[i * 3 + 1] + segV[i * 3 + 1] * dt - (segFall[i] ? 0.5 * G * dt * dt : 0);
        out[2] = segP[i * 3 + 2] + segV[i * 3 + 2] * dt;
    }

    /** Largest distance the centre moves between two samples 0.05 ticks apart over the whole path. */
    double maxJump() {
        double[] a = new double[3], b = new double[3];
        double worst = 0;
        position(0, a);
        for (float t = 0.05f; t <= restT + 1; t += 0.05f) {
            position(t, b);
            double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
            worst = Math.max(worst, Math.sqrt(dx * dx + dy * dy + dz * dz));
            double[] tmp = a;
            a = b;
            b = tmp;
        }
        return worst;
    }

    /** Velocity at flight time tf into out[0..2]. */
    void velocity(float tf, double[] out) {
        int i = nseg - 1;
        while (i > 0 && segT[i] > tf) i--;
        double dt = Math.max(0, tf - segT[i]);
        out[0] = segV[i * 3];
        out[1] = segV[i * 3 + 1] - (segFall[i] ? G * dt : 0);
        out[2] = segV[i * 3 + 2];
    }
}
