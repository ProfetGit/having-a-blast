package io.github.profetgit.havingablast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The cartoon explosion, drawn from the pixel-art sheet (dev/fx/make_fx.py) as billboards. Every element is a pure
 * function of the time since the blast, evaluated per frame, so it is exactly as smooth as the frame rate:
 * <ul>
 * <li>burst: a white star flash that swells and turns pale yellow (2.4 ticks);</li>
 * <li>shockwave: a ring racing over the ground with a dust skirt riding it (8 ticks);</li>
 * <li>sparks: bright streaks shooting out, stretched along their flight (3.5 to 5.5 ticks);</li>
 * <li>billows: fire puffs of four flat layers (white core, yellow, orange, dark-rimmed red) that pop out with an
 * overshoot; the inner layers collapse first, then each billow turns into an outlined smoke puff that rises, cools
 * from warm white to ash grey and breaks apart;</li>
 * <li>embers drifting up; a dust puff where a debris lands hard; a white poof where one pops.</li>
 * </ul>
 * Fire, sparks and the flash are full-bright; smoke, dust and rings take the light of the blast's surroundings.
 */
final class Boom {
    static final int HOT = 0xFFFFFFFF, YELLOW = 0xFFFFD83A, ORANGE = 0xFFFF9A1F, RED = 0xFFE8531A, EMBER = 0xFFFFB347,
        WARM = 0xFFF4E2C8, SMOKE = 0xFFF4F4F0, SMOKE_DARK = 0xFFD6D8DE, ASH = 0xFFBFC3CC, DUST = 0xFFDCD2C2, RING = 0xFFF6F2EA;
    static final float LIFE = 34;
    /** Blasts drawn with the whole effect at once; landing dust and pop poofs drawn per frame at most. */
    static final int FULL = 24, MAX_AFTERMATH = 600;

    private Boom() {
    }

    /** Per-blast constants of the effect, drawn once from the blast's seed. */
    static final class Params {
        final int nb, ns, nd, ne;
        final float[] b, s, dd, e;
        final double groundY;
        final boolean ground, water;

        Params(Blast bl, ClientLevel level) {
            RandomSource r = RandomSource.create(bl.seed ^ 0x2545F4914F6CDD1DL);
            float sc = scale(bl);
            nb = Math.min(14, 7 + (int) (bl.radius * 1.5));
            b = new float[nb * 10];
            for (int i = 0; i < nb; i++) {
                double ux = r.nextDouble() * 2 - 1, uy = 0.35 + r.nextDouble() * 0.9, uz = r.nextDouble() * 2 - 1;
                double len = Math.max(0.25, Math.sqrt(ux * ux + uy * uy + uz * uz));
                int o = i * 10;
                b[o] = (float) (ux / len);
                b[o + 1] = (float) (uy / len);
                b[o + 2] = (float) (uz / len);
                b[o + 3] = sc * (0.5f + 1.1f * r.nextFloat());
                b[o + 4] = sc * (2.2f + 1.1f * r.nextFloat());
                b[o + 5] = i < 3 ? r.nextFloat() * 0.4f : 0.3f + r.nextFloat() * 1.6f;
                b[o + 6] = 18 + r.nextFloat() * 9;
                b[o + 7] = (i + r.nextInt(2)) & 3;
                b[o + 8] = ((i >> 2) + r.nextInt(2)) & 3;
                b[o + 9] = r.nextFloat() * 0.6f + 0.7f;
            }
            ns = Math.min(18, 8 + (int) (bl.radius * 1.5));
            s = new float[ns * 6];
            for (int i = 0; i < ns; i++) {
                double ux = r.nextDouble() * 2 - 1, uy = r.nextDouble() * 1.2 - 0.2, uz = r.nextDouble() * 2 - 1;
                double len = Math.max(0.25, Math.sqrt(ux * ux + uy * uy + uz * uz));
                int o = i * 6;
                s[o] = (float) (ux / len);
                s[o + 1] = (float) (uy / len);
                s[o + 2] = (float) (uz / len);
                s[o + 3] = sc * (3.2f + 2.2f * r.nextFloat());
                s[o + 4] = 3.5f + 2f * r.nextFloat();
                s[o + 5] = r.nextFloat() * 0.5f;
            }
            nd = 12;
            dd = new float[nd * 4];
            for (int i = 0; i < nd; i++) {
                int o = i * 4;
                dd[o] = (float) (Math.PI * 2 * (i + r.nextFloat() * 0.6f) / nd);
                dd[o + 1] = sc * (0.8f + 0.45f * r.nextFloat());
                dd[o + 2] = 13 + r.nextFloat() * 6;
                dd[o + 3] = r.nextInt(2);
            }
            ne = 7;
            e = new float[ne * 6];
            for (int i = 0; i < ne; i++) {
                int o = i * 6;
                double a = r.nextDouble() * Math.PI * 2;
                e[o] = (float) Math.cos(a);
                e[o + 1] = (float) Math.sin(a);
                e[o + 2] = sc * (0.6f + 1.2f * r.nextFloat());
                e[o + 3] = 2 + r.nextFloat() * 3;
                e[o + 4] = 12 + r.nextFloat() * 10;
                e[o + 5] = r.nextFloat() * 6.28f;
            }
            // the ground the shockwave runs over: the first surface at or below the centre (the crater isn't dug yet)
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            double gy = Double.NaN;
            int x = (int) Math.floor(bl.center.x), z = (int) Math.floor(bl.center.z), y0 = (int) Math.floor(bl.center.y);
            for (int y = y0; y >= y0 - 4 && Double.isNaN(gy); y--) {
                mp.set(x, y, z);
                var st = level.getBlockState(mp);
                if (!st.isAir() && !st.getCollisionShape(level, mp).isEmpty()) gy = y + st.getCollisionShape(level, mp).max(net.minecraft.core.Direction.Axis.Y);
            }
            mp.set(x, y0, z);
            water = !level.getFluidState(mp).isEmpty();
            ground = !water && !Double.isNaN(gy) && bl.center.y - gy < 2.5;
            groundY = ground ? gy + 0.03 : bl.center.y;
        }
    }

    static float scale(Blast b) {
        return (float) Math.max(0.6, Math.min(2.4, b.radius / 3.0)) * BlastClient.intensity();
    }

    static boolean done(Blast b, float t) {
        return t > LIFE + 4;
    }

    static float clamp01(float x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    static float smooth(float x) {
        x = clamp01(x);
        return x * x * (3 - 2 * x);
    }

    static float easeOutCubic(float x) {
        x = clamp01(x);
        float k = 1 - x;
        return 1 - k * k * k;
    }

    static float easeOutQuart(float x) {
        x = clamp01(x);
        float k = 1 - x;
        return 1 - k * k * k * k;
    }

    static float easeOutBack(float x, float over) {
        x = clamp01(x);
        float c3 = over + 1;
        return 1 + c3 * (x - 1) * (x - 1) * (x - 1) + over * (x - 1) * (x - 1);
    }

    static int lerp(int a, int b, float t) {
        t = clamp01(t);
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /** Soft poofs outside any blast (a repaired block dropping into its slot): x, y, z, size, start time. */
    static final java.util.ArrayDeque<float[]> POOFS = new java.util.ArrayDeque<>();

    static void poof(double x, double y, double z, float size) {
        if (POOFS.size() > 400) POOFS.pollFirst();
        POOFS.addLast(new float[] {(float) x, (float) y, (float) z, size, Blasts.ticks});
    }

    /** Submits every blast's effect for this frame in one geometry batch. */
    static void submit(float now, Vec3 cam, Quaternionf camRot, PoseStack ps, SubmitNodeCollector c, ClientLevel level) {
        RenderType type = Sprites.type();
        if (type == null) return;
        final Quaternionf rot = new Quaternionf(camRot);
        final double cx = cam.x, cy = cam.y, cz = cam.z;
        // level of detail: the FULL nearest live blasts get the whole effect, the rest a lighter one
        int live = 0;
        for (Blast b : Blasts.blasts) if (!Float.isNaN(b.t0) && now - b.t0 <= LIFE + 4) live++;
        final double liteBeyond;
        if (live > FULL) {
            double[] dist = new double[live];
            int k = 0;
            for (Blast b : Blasts.blasts) if (!Float.isNaN(b.t0) && now - b.t0 <= LIFE + 4) dist[k++] = b.center.distanceToSqr(cx, cy, cz);
            java.util.Arrays.sort(dist);
            liteBeyond = dist[FULL - 1];
        } else {
            liteBeyond = Double.MAX_VALUE;
        }
        POOFS.removeIf(p -> Blasts.ticks - p[4] > 9);
        c.submitCustomGeometry(ps, type, (pose, vc) -> {
            long t0 = System.nanoTime();
            Writer w = new Writer(vc, pose.pose(), pose.transformNormal(0, 1, 0, new Vector3f()), rot, cx, cy, cz);
            for (float[] p : POOFS) {
                float tp = now - p[4];
                if (tp < 0 || tp > 8) continue;
                float g = easeOutBack(tp / 1.8f, 1.6f);
                int k = tp < 3 ? -1 : tp < 4.5f ? 0 : tp < 6 ? 1 : 2;
                int col = k < 0 ? 4 : k, row = k < 0 ? 1 : 2;
                int env = DebrisRenderer.lightAt(level, p[0], p[1] + 0.3, p[2]);
                float size = p[3] * g * (tp < 3 ? 1 : 1 - 0.3f * (tp - 3) / 5);
                w.billboard(p[0], p[1] + tp * 0.03f, p[2], 0.05f, size, size, col, row, 0, SMOKE, LightCoordsUtil.withBlock(env, Math.max(LightCoordsUtil.block(env), 8)));
            }
            for (Blast b : Blasts.blasts) {
                if (Float.isNaN(b.t0) || b.fx == null) continue;
                float t = now - b.t0;
                double d2 = b.center.distanceToSqr(cx, cy, cz);
                if (d2 > 192 * 192) continue;
                if (t <= LIFE + 4) draw(w, b, b.fx, t, level, d2 > liteBeyond);
                if (w.aftermath < MAX_AFTERMATH) aftermath(w, b, t, level);
            }
            DebrisRenderer.spentNanos += System.nanoTime() - t0;
        });
    }

    static void draw(Writer w, Blast bl, Params p, float t, ClientLevel level, boolean lite) {
        float sc = scale(bl);
        double ox = bl.center.x, oy = bl.center.y + 0.35 * sc, oz = bl.center.z;
        // the light where it happens (a cave stays a cave), never quite black: the smoke is still readable
        int raw = DebrisRenderer.lightAt(level, bl.center.x, bl.center.y + 1, bl.center.z);
        int env = LightCoordsUtil.withBlock(raw, Math.max(LightCoordsUtil.block(raw), 6));
        int glowEnv = LightCoordsUtil.withBlock(env, 13);

        if (p.water) {
            bubbles(w, bl, p, t, sc, env);
            return;
        }
        // burst: the star flash
        if (t < 2.4f) {
            float u = t / 2.4f;
            int frame = t < 0.55f ? 0 : t < 1.2f ? 1 : t < 1.85f ? 2 : 3;
            // the impact frame: biggest at once (a quick overshoot), then it shrinks into the fireball
            float size = sc * 5.6f * (0.7f + 0.3f * easeOutBack(t / 0.45f, 2.2f)) * (1 - smooth((t - 0.5f) / 1.9f) * 0.85f);
            w.billboard(ox, oy + 0.4 * sc, oz, 1.6f * sc, size, size, 4 + frame, 3, t * 0.12f, lerp(HOT, 0xFFFFF4B0, u), LightCoordsUtil.FULL_BRIGHT);
        }
        // ground shockwave and its dust skirt
        if (p.ground && t > 0.35f && t < 8.35f) {
            float u = (t - 0.35f) / 8f;
            float d = sc * (2.6f + 9f * easeOutQuart(u));
            w.ring(ox, p.groundY, oz, 0, d, Math.min(7, (int) (u * 8)), true, RING, glowEnv);
        }
        if (p.ground && !lite) {
            for (int i = 0; i < p.nd; i++) {
                int o = i * 4;
                float tb = t - 0.5f, life = p.dd[o + 2];
                if (tb < 0 || tb > life) continue;
                float u = tb / life;
                float rad = sc * (1.2f + 3.6f * easeOutQuart(tb / 7f)) + 0.04f * tb;
                float g = easeOutBack(tb / 2.2f, 1.6f) * (u < 0.6f ? 1 : 1 - 0.3f * (u - 0.6f) / 0.4f);
                float size = p.dd[o + 1] * g * (1.2f + 0.25f * u);
                double x = ox + Math.cos(p.dd[o]) * rad, z = oz + Math.sin(p.dd[o]) * rad;
                int col = u < 0.66f ? (int) (4 + p.dd[o + 3]) : 0;
                int row = u < 0.66f ? 1 : 2;
                if (u >= 0.66f) {
                    int k = u < 0.8f ? 0 : u < 0.92f ? 1 : 2;
                    col = k;
                }
                w.billboard(x, p.groundY + size * 0.36, z, 0, size, size, col, row, 0, lerp(DUST, ASH, u), env);
            }
        }
        // sparks
        for (int i = 0; i < (lite ? 0 : p.ns); i++) {
            int o = i * 6;
            float tb = t - p.s[o + 5], life = p.s[o + 4];
            if (tb < 0 || tb > life) continue;
            float u = tb / life;
            float dist = p.s[o + 3] * easeOutCubic(u);
            float len = sc * (2.3f * (float) Math.pow(1 - u, 1.3) + 0.15f);
            double x = ox + p.s[o] * dist, y = oy + p.s[o + 1] * dist - 0.04 * tb * tb, z = oz + p.s[o + 2] * dist;
            w.streak(x, y, z, p.s[o], p.s[o + 1] - 0.08f * tb, p.s[o + 2], len, sc * 0.4f * (1 - 0.5f * u), u < 0.35f ? lerp(HOT, YELLOW, u / 0.35f) : lerp(YELLOW, ORANGE, (u - 0.35f) / 0.65f));
        }
        // billows: fire, then smoke
        for (int i = 0; i < (lite ? Math.min(4, p.nb) : p.nb); i++) {
            int o = i * 10;
            float tb = t - p.b[o + 5];
            float life = p.b[o + 6];
            if (tb < 0 || tb > life) continue;
            float out = easeOutCubic(tb / 7f);
            float rise = (0.07f * tb + 0.006f * tb * tb) * (float) Math.sqrt(sc);
            double x = ox + p.b[o] * p.b[o + 3] * out, y = oy + p.b[o + 1] * p.b[o + 3] * out + rise, z = oz + p.b[o + 2] * p.b[o + 3] * out;
            float size = p.b[o + 4];
            float g = easeOutBack(tb / 2.6f, 1.7f);
            int shape = (int) p.b[o + 7];
            float roll = (int) p.b[o + 8] * 1.5708f + tb * 0.01f * (p.b[o + 9] - 1);
            boolean flip = (i & 2) != 0;
            // fire layers, inner first to collapse; each sits a little nearer the camera than the one below
            float core = 0.42f * g * (1 - smooth(tb / 2.2f));
            float yel = 0.66f * g * (1 - smooth((tb - 1.2f) / 2.6f));
            float org = 0.86f * g * (1 - smooth((tb - 2.4f) / 2.9f));
            float red = g * (1 - smooth((tb - 3.6f) / 3.0f));
            if (red > 0.01f) w.billboardF(x, y, z, 0.012f * sc, size * red, size * red, 4 + shape, 0, roll, flip, lerp(RED, 0xFFB8401A, (tb - 2) / 4), LightCoordsUtil.FULL_BRIGHT);
            if (org > 0.01f) w.billboardF(x, y, z, 0.03f * sc, size * org, size * org, shape, 0, roll + 0.4f, flip, ORANGE, LightCoordsUtil.FULL_BRIGHT);
            if (yel > 0.01f) w.billboardF(x, y, z, 0.048f * sc, size * yel, size * yel, (shape + 1) & 3, 0, roll + 1.1f, !flip, YELLOW, LightCoordsUtil.FULL_BRIGHT);
            if (core > 0.01f) w.billboardF(x, y, z, 0.066f * sc, size * core, size * core, (shape + 2) & 3, 0, roll + 2.0f, flip, HOT, LightCoordsUtil.FULL_BRIGHT);
            // smoke takes over behind the fire and outlives it
            float ts = tb - 2.8f;
            if (ts > 0) {
                float su = tb / life;
                float sg = easeOutBack(ts / 3.4f, 1.4f) * 1.2f;
                float shrink = su < 0.7f ? 1 : 1 - 0.4f * (su - 0.7f) / 0.3f;
                int col = shape, row = 1;
                if (su >= 0.72f) {
                    int k = su < 0.82f ? 0 : su < 0.92f ? 1 : 2;
                    int idx = shape * 3 + k;
                    col = idx < 8 ? idx : idx - 8;
                    row = idx < 8 ? 2 : 3;
                }
                int smoke = (i & 3) == 3 ? SMOKE_DARK : SMOKE;
                int color = tb < 6 ? lerp(WARM, smoke, (tb - 2.8f) / 3.2f) : lerp(smoke, ASH, (tb - 6) / (life - 6));
                w.billboardF(x, y, z, 0, size * sg * shrink, size * sg * shrink, col, row, roll * 0.5f, flip, color, tb < 6 ? glowEnv : env);
            }
        }
        // embers
        for (int i = 0; i < (lite ? 0 : p.ne); i++) {
            int o = i * 6;
            float tb = t - p.e[o + 3], life = p.e[o + 4];
            if (tb < 0 || tb > life) continue;
            float u = tb / life;
            if (u > 0.7f && ((int) (tb * 4)) % 2 == 0) continue;
            double x = ox + p.e[o] * p.e[o + 2] * easeOutCubic(tb / 6) + Math.sin(tb * 0.5 + p.e[o + 5]) * 0.15 * sc;
            double y = oy + 0.5 * sc + 0.09 * tb * Math.sqrt(sc);
            double z = oz + p.e[o + 1] * p.e[o + 2] * easeOutCubic(tb / 6) + Math.cos(tb * 0.5 + p.e[o + 5]) * 0.15 * sc;
            float size = sc * 0.55f * (1 - 0.5f * u);
            w.billboard(x, y, z, 0.1f, size, size, 7, 1, 0, lerp(YELLOW, EMBER, u), LightCoordsUtil.FULL_BRIGHT);
        }
    }

    static final int BUBBLE = 0xFFD6ECFF;

    /** Under water: no fire, no shockwave; the billows are pale blue bubbles that pop out and rise fast. */
    static void bubbles(Writer w, Blast bl, Params p, float t, float sc, int env) {
        double ox = bl.center.x, oy = bl.center.y, oz = bl.center.z;
        for (int i = 0; i < p.nb; i++) {
            int o = i * 10;
            float tb = t - p.b[o + 5] * 0.6f;
            float life = p.b[o + 6] * 0.7f;
            if (tb < 0 || tb > life) continue;
            float u = tb / life;
            float out = easeOutCubic(tb / 5f);
            double x = ox + p.b[o] * p.b[o + 3] * out * 0.7, y = oy + p.b[o + 1] * p.b[o + 3] * out * 0.7 + 0.12 * tb + 0.01 * tb * tb, z = oz + p.b[o + 2] * p.b[o + 3] * out * 0.7;
            float g = easeOutBack(tb / 2f, 1.8f) * (u < 0.75f ? 1 : 1 - (u - 0.75f) / 0.25f);
            float size = p.b[o + 4] * 0.55f * g;
            int shape = (int) p.b[o + 7];
            int col = u < 0.7f ? shape : shape * 3 + 1 < 8 ? shape * 3 + 1 : shape * 3 + 1 - 8;
            int row = u < 0.7f ? 1 : shape * 3 + 1 < 8 ? 2 : 3;
            w.billboard(x, y, z, 0, size, size, col, row, (int) p.b[o + 8] * 1.5708f, BUBBLE, LightCoordsUtil.withBlock(env, 12));
        }
    }

    /** Dust where debris lands hard and the white poof where one pops; both outlive the blast's own effect. */
    static void aftermath(Writer w, Blast bl, float t, ClientLevel level) {
        double[] p = w.tmp;
        for (Debris d : bl.debris) {
            if (d.nseg == 0) continue;
            float tf = t - d.launch;
            for (int k = 0; k < Math.min(2, d.ncontact); k++) {
                float tb = tf - d.contactT[k];
                if (tb < 0 || tb > 9 || d.contactK[k] < 0.35f || w.aftermath >= MAX_AFTERMATH) continue;
                d.position(d.contactT[k], p);
                float g = easeOutBack(tb / 2f, 1.4f) * (1 - smooth((tb - 3.5f) / 5.5f));
                float size = d.size * (1.2f + 0.8f * d.contactK[k]) * g;
                if (size < 0.02f) continue;
                int env = DebrisRenderer.lightAt(level, p[0], p[1] + 0.3, p[2]);
                w.billboard(p[0], p[1] - d.size * 0.5 + size * 0.3, p[2], 0.02f, size, size * 0.8f, 4 + (d.popIndex & 1), 1, 0, DUST, env);
                w.aftermath++;
            }
            float tp = t - d.popT - 3;
            if (d.popped && !d.quiet && tp >= 0 && tp < 7 && w.aftermath < MAX_AFTERMATH) {
                d.position(d.restT, p);
                float g = easeOutBack(tp / 1.8f, 1.8f);
                float size = d.size * 1.9f * g * (tp < 3 ? 1 : 1 - 0.3f * (tp - 3) / 4);
                int col = 4 + (d.popIndex & 1), row = 1;
                if (tp >= 3) {
                    int k = tp < 4.3f ? 0 : tp < 5.6f ? 1 : 2;
                    int idx = (d.popIndex & 3) * 3 + k;
                    col = idx < 8 ? idx : idx - 8;
                    row = idx < 8 ? 2 : 3;
                }
                int env = DebrisRenderer.lightAt(level, p[0], p[1] + 0.3, p[2]);
                w.billboard(p[0], p[1] + 0.1 + tp * 0.03, p[2], 0.03f, size, size, col, row, (d.popIndex & 3) * 1.5708f, SMOKE, LightCoordsUtil.withBlock(env, Math.max(LightCoordsUtil.block(env), 8)));
                w.aftermath++;
            }
        }
    }

    /** Writes sprite quads: camera-facing billboards, velocity-aligned streaks and flat or facing rings. */
    static final class Writer {
        final VertexConsumer vc;
        final Matrix4f m;
        final Vector3f n;
        final Vector3f right, up, toCam;
        final double cx, cy, cz;
        final double[] tmp = new double[3];
        int aftermath;
        final Vector3f a = new Vector3f(), b = new Vector3f();

        Writer(VertexConsumer vc, Matrix4f m, Vector3f n, Quaternionf rot, double cx, double cy, double cz) {
            this.vc = vc;
            this.m = m;
            this.n = n;
            this.right = rot.transform(new Vector3f(1, 0, 0));
            this.up = rot.transform(new Vector3f(0, 1, 0));
            this.toCam = rot.transform(new Vector3f(0, 0, 1));
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
        }

        /** A camera-facing sprite from cell (col,row) of the 32 px grid, lifted toward the camera by `lift` blocks. */
        void billboard(double x, double y, double z, float lift, float w, float h, int col, int row, float roll, int color, int light) {
            float k = nearFade(x, y, z);
            if (k <= 0) return;
            w *= k;
            h *= k;
            float cs = (float) Math.cos(roll), sn = (float) Math.sin(roll);
            a.set(right).mul(cs).add(up.x * sn, up.y * sn, up.z * sn).mul(w * 0.5f);
            b.set(up).mul(cs).sub(right.x * sn, right.y * sn, right.z * sn).mul(h * 0.5f);
            float u0 = col * 0.125f, v0 = row * 0.125f;
            quad((float) (x - cx) + toCam.x * lift, (float) (y - cy) + toCam.y * lift, (float) (z - cz) + toCam.z * lift, a, b, u0, v0, u0 + 0.125f, v0 + 0.125f, color, light);
        }

        /** billboard, optionally mirrored (u swapped, so the quad stays a front face). */
        /** Sprites shrink away within 4 blocks of the camera, so smoke or dust never covers the view. */
        float nearFade(double x, double y, double z) {
            double dx = x - cx, dy = y - cy, dz = z - cz;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 >= 16) return 1;
            float u = (float) ((Math.sqrt(d2) - 1.5) / 2.5);
            return smooth(u);
        }

        void billboardF(double x, double y, double z, float lift, float w, float h, int col, int row, float roll, boolean flip, int color, int light) {
            float k = nearFade(x, y, z);
            if (k <= 0) return;
            w *= k;
            h *= k;
            if (!flip) {
                billboard(x, y, z, lift, w / k, h / k, col, row, roll, color, light);
                return;
            }
            float cs = (float) Math.cos(roll), sn = (float) Math.sin(roll);
            a.set(right).mul(cs).add(up.x * sn, up.y * sn, up.z * sn).mul(w * 0.5f);
            b.set(up).mul(cs).sub(right.x * sn, right.y * sn, right.z * sn).mul(h * 0.5f);
            float u0 = col * 0.125f, v0 = row * 0.125f;
            quad((float) (x - cx) + toCam.x * lift, (float) (y - cy) + toCam.y * lift, (float) (z - cz) + toCam.z * lift, a, b, u0 + 0.125f, v0, u0, v0 + 0.125f, color, light);
        }

        /** A spark: the streak cell stretched along its flight direction, turned to face the camera around that axis. */
        void streak(double x, double y, double z, float dx, float dy, float dz, float len, float width, int color) {
            b.set(dx, dy, dz).normalize();
            a.set(b).cross(toCam);
            if (a.lengthSquared() < 1e-6f) a.set(right);
            a.normalize().mul(width * 0.5f);
            b.mul(len * 0.5f);
            quad((float) (x - cx), (float) (y - cy), (float) (z - cz), a, b, 0.75f, 0.125f, 0.875f, 0.25f, color, LightCoordsUtil.FULL_BRIGHT);
        }

        /** A 64 px ring frame (0..7), flat on the ground or facing the camera, `d` blocks across. */
        void ring(double x, double y, double z, float lift, float d, int frame, boolean flat, int color, int light) {
            float u0 = (frame & 3) * 0.25f, v0 = 0.5f + (frame >> 2) * 0.25f;
            if (flat) {
                a.set(d * 0.5f, 0, 0);
                b.set(0, 0, -d * 0.5f);
                quad((float) (x - cx), (float) (y - cy), (float) (z - cz), a, b, u0, v0, u0 + 0.25f, v0 + 0.25f, color, light);
            } else {
                a.set(right).mul(d * 0.5f);
                b.set(up).mul(d * 0.5f);
                quad((float) (x - cx) + toCam.x * lift, (float) (y - cy) + toCam.y * lift, (float) (z - cz) + toCam.z * lift, a, b, u0, v0, u0 + 0.25f, v0 + 0.25f, color, light);
            }
        }

        /** Quad centred at (x,y,z) spanning +-a and +-b, counter-clockwise seen from where a x b points. */
        void quad(float x, float y, float z, Vector3f a, Vector3f b, float u0, float v0, float u1, float v1, int color, int light) {
            vertex(x - a.x - b.x, y - a.y - b.y, z - a.z - b.z, u0, v1, color, light);
            vertex(x + a.x - b.x, y + a.y - b.y, z + a.z - b.z, u1, v1, color, light);
            vertex(x + a.x + b.x, y + a.y + b.y, z + a.z + b.z, u1, v0, color, light);
            vertex(x - a.x + b.x, y - a.y + b.y, z - a.z + b.z, u0, v0, color, light);
        }

        void vertex(float x, float y, float z, float u, float v, int color, int light) {
            vc.addVertex(m, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
        }
    }
}
