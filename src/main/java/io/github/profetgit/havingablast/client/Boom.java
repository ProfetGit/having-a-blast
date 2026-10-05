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
 * function of the time since the blast, evaluated per frame:
 * <ul>
 * <li>fireball: one big lit sphere that grows from a white-hot dot, sitting low so the ground hides its lower half (a
 * dome); it pales out and breaks into light lumps (about 12 ticks);</li>
 * <li>shockwave: a flat fiery ring racing over the ground, thinning and breaking up (14 ticks, stopping short of the
 * camera);</li>
 * <li>mushroom: round puffs thrown up out of the fireball into a stem and a cap; each cools from yellow through
 * orange and brick red to grey and breaks into bits (30 ticks);</li>
 * <li>sparks in two waves: yellow with the blast, white as the fireball pales;</li>
 * <li>a round dust skirt, embers; a small dust puff where a debris lands hard (a pop has no smoke).</li>
 * </ul>
 * The colours are painted into the frames, nothing has an ink outline, and each element keeps one quad size for its
 * whole life and animates by frames, so its pixels never change size (the look of Explosive Enhancement, which
 * players liked next to this mod's debris). Sprites only turn by whole quarter turns or mirror.
 */
final class Boom {
    static final int WHITE = 0xFFFFFFFF, YELLOW = 0xFFFFD83A, ORANGE = 0xFFFF9A1F, EMBER = 0xFFFFB347,
        SMOKE = 0xFFF4F4F0, ASH = 0xFFC4C6CC, DUST = 0xFFDCD2C2, BUBBLE = 0xFFD6ECFF;
    /** Sheet cells: 32 px, 1/8 of the width and 1/24 of the height (256 x 768). */
    static final float CU = 0.125f, CV = 1 / 24f;
    /** Round-look frames (32 px cells from row 16): fireball 0-11, puff 12-23, dust 24-27; 64 px rings from y 640. */
    static final int FIREBALL = 0, PUFF = 12, DUST_PUFF = 24;
    static final float LIFE = 48;
    /** Blasts drawn with the whole effect at once; landing dust puffs drawn per frame at most. */
    static final int FULL = 24, MAX_AFTERMATH = 600;
    /** Drawn radius of each ring frame, in pixels of its 64 px cell. */
    static final float[] RING_R = {9, 15.5f, 20.5f, 24.5f, 27.5f, 29.5f, 30.5f, 31.2f};

    private Boom() {
    }

    /** Per-blast constants of the effect, drawn once from the blast's seed. */
    static final class Params {
        /** Mushroom puffs: target offset (3), size, delay, life, mirrored. */
        final int np;
        final float[] m;
        final int ns, nd, ne;
        final float[] s, dd, e;
        final double groundY;
        final boolean ground, water;
        /** The underside of the first solid block above the blast (open: 12 blocks up), before and after the crater. */
        double ceil0 = Double.NaN, ceil1 = Double.NaN;

        Params(Blast bl, ClientLevel level) {
            RandomSource r = RandomSource.create(bl.seed ^ 0x2545F4914F6CDD1DL);
            float sc = scale(bl);
            // mushroom: two stem puffs, a ring of cap puffs and one on top
            np = 6 + Math.min(3, (int) Math.max(0, bl.radius - 3));
            m = new float[np * 7];
            double c0 = r.nextDouble() * Math.PI * 2;
            for (int i = 0; i < np; i++) {
                int o = i * 7;
                boolean stem = i < 2, top = i == np - 1;
                double a = c0 + (i - 2) * Math.PI * 2 / Math.max(1, np - 3) + (r.nextDouble() - 0.5) * 0.6;
                double rad = stem ? sc * 0.2 * r.nextDouble() : top ? 0 : sc * (2.0 + 0.6 * r.nextDouble());
                m[o] = (float) (Math.cos(a) * rad);
                m[o + 1] = sc * (stem ? 2.4f + 1.8f * i + 0.3f * r.nextFloat() : top ? 6.6f : 5.4f + 0.5f * r.nextFloat());
                m[o + 2] = (float) (Math.sin(a) * rad);
                m[o + 3] = sc * 1.6f * (stem ? 0.9f : 1.05f) * (0.9f + 0.2f * r.nextFloat());
                m[o + 4] = (stem ? 0.4f : 0.6f) + 0.6f * r.nextFloat();
                m[o + 5] = 26 + 10 * r.nextFloat();
                m[o + 6] = r.nextInt(2);
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
                s[o + 5] = (i & 1) == 0 ? r.nextFloat() * 0.5f : 6.2f + r.nextFloat() * 1.2f;
            }
            nd = 10;
            dd = new float[nd * 4];
            for (int i = 0; i < nd; i++) {
                int o = i * 4;
                dd[o] = (float) (Math.PI * 2 * (i + r.nextFloat() * 0.6f) / nd);
                dd[o + 1] = sc * (0.8f + 0.3f * r.nextFloat());
                dd[o + 2] = 8 + r.nextFloat() * 4;
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

    /**
     * The ceiling the smoke hugs: read at the first frame and again once the crater's blocks are gone (tick 3), eased
     * from one to the other over 3 ticks so a blast that opens its ceiling lets the cloud rise without a jump.
     */
    static double ceiling(Params p, Blast bl, float t, ClientLevel level) {
        if (Double.isNaN(p.ceil0)) p.ceil0 = readCeiling(bl, level);
        if (t < 3) return p.ceil0;
        if (Double.isNaN(p.ceil1)) p.ceil1 = readCeiling(bl, level);
        return p.ceil0 + (p.ceil1 - p.ceil0) * smooth((t - 3) / 3);
    }

    static double readCeiling(Blast bl, ClientLevel level) {
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int x = (int) Math.floor(bl.center.x), z = (int) Math.floor(bl.center.z), y0 = (int) Math.floor(bl.center.y) + 1;
        for (int y = y0; y <= y0 + 8; y++) {
            mp.set(x, y, z);
            var shape = level.getBlockState(mp).getCollisionShape(level, mp);
            if (!shape.isEmpty()) return y + shape.min(net.minecraft.core.Direction.Axis.Y);
        }
        return bl.center.y + 12;
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

    static int lerp(int a, int b, float t) {
        t = clamp01(t);
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /**
     * The fireball frame at `tb` ticks into its life (-1: gone): swells through frames 0-5 in 5 ticks, full (6, 7),
     * paling (8, 9), light lumps (10, 11), gone after 12.2 ticks.
     */
    static int fireFrame(float tb) {
        if (tb < 0) return -1;
        if (tb < 5.0f) return Math.min(5, (int) (tb / 5.0f * 6));
        if (tb < 6.2f) return 6;
        if (tb < 7.4f) return 7;
        int k = 8 + (int) ((tb - 7.4f) / 1.2f);
        return k <= 11 ? k : -1;
    }

    /** The mushroom puff frame at `u` (0..1 of its life): fire colours in the first 22 %, greys, then breaking up. */
    static int puffFrame(float u) {
        if (u < 0.22f) return (int) (u / 0.22f * 6);
        if (u < 0.75f) return 6 + Math.min(2, (int) ((u - 0.22f) / 0.53f * 3));
        return 9 + Math.min(2, (int) ((u - 0.75f) / 0.25f * 3));
    }

    /** The round dust frame at `u` (0..1 of its life): whole, then breaking up in three steps. */
    static int dustFrame(float u) {
        return u < 0.55f ? 0 : u < 0.72f ? 1 : u < 0.88f ? 2 : 3;
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
        Compat.geometry(c, ps, type, (pose, vc) -> {
            long t0 = System.nanoTime();
            Writer w = new Writer(vc, pose.pose(), pose.transformNormal(0, 1, 0, new Vector3f()), rot, cx, cy, cz);
            for (float[] p : POOFS) {
                float tp = now - p[4];
                if (tp < 0 || tp > 8) continue;
                int env = DebrisRenderer.lightAt(level, p[0], p[1] + 0.3, p[2]);
                w.frame(p[0], p[1] + tp * 0.03f, p[2], 0.05f, p[3] * 0.75f, DUST_PUFF + dustFrame(tp / 8), false, SMOKE, LightCoordsUtil.withBlock(env, Math.max(LightCoordsUtil.block(env), 8)));
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
        if (p.water) {
            bubbles(w, bl, p, t, sc, env);
            return;
        }
        double ceil = ceiling(p, bl, t, level);
        // smoke light: one value per blast at a time, the fire's glow fading into the light of the place
        int smokeLight = LightCoordsUtil.withBlock(raw, Math.round(15 + (Math.max(LightCoordsUtil.block(raw), 6) - 15) * smooth((t - 4) / 10f)));
        int full = LightCoordsUtil.FULL_BRIGHT;

        // shockwave: a fixed-size quad whose frames draw the ring further out; near the camera it is scaled down so
        // it stops 2.5 blocks short instead of sweeping under the player
        if (p.ground && t > 0.2f && t < 14.6f) {
            int fr = Math.min(7, (int) ((t - 0.2f) / 1.8f));
            float d = sc * 10.5f;
            float reach = d * 0.5f * RING_R[fr] / 32;
            double hx = ox - w.cx, hz = oz - w.cz;
            float room = (float) (Math.sqrt(hx * hx + hz * hz) - 2.5);
            if (reach > room) d *= Math.max(0, room) / reach;
            if (d > sc) w.ring(ox, p.groundY, oz, 0, d, fr, true, WHITE, full);
        }
        // dust skirt: small round puffs rolling out over the ground
        if (p.ground && !lite) {
            for (int i = 0; i < p.nd; i++) {
                int o = i * 4;
                float tb = t - 0.4f, life = p.dd[o + 2];
                if (tb < 0 || tb > life) continue;
                float u = tb / life;
                float rad = sc * (1.0f + 2.2f * easeOutQuart(tb / 6f)) + 0.03f * tb;
                float size = p.dd[o + 1];
                double x = ox + Math.cos(p.dd[o]) * rad, z = oz + Math.sin(p.dd[o]) * rad;
                w.frame(x, p.groundY + size * 0.3, z, 0, size, DUST_PUFF + dustFrame(u), p.dd[o + 3] > 0, lerp(DUST, ASH, u), env);
            }
        }
        // sparks
        for (int i = 0; i < (lite ? 0 : p.ns); i++) {
            int o = i * 6;
            float tb = t - p.s[o + 5], life = p.s[o + 4];
            if (tb < 0 || tb > life) continue;
            float u = tb / life;
            float dist = p.s[o + 3] * easeOutCubic(u);
            boolean late = (i & 1) != 0;
            float len = sc * (2.3f * (float) Math.pow(1 - u, 1.3) + 0.15f) * (late ? 1.6f : 1);
            double x = ox + p.s[o] * dist, y = oy + p.s[o + 1] * dist - 0.04 * tb * tb, z = oz + p.s[o + 2] * dist;
            int col = late ? lerp(WHITE, 0xFFFFF2C0, u) : u < 0.35f ? lerp(WHITE, YELLOW, u / 0.35f) : lerp(YELLOW, ORANGE, (u - 0.35f) / 0.65f);
            w.streak(x, y, z, p.s[o], p.s[o + 1] - 0.08f * tb, p.s[o + 2], len, sc * (late ? 0.6f : 0.4f) * (1 - 0.5f * u), col);
        }
        // mushroom: puffs thrown up out of the fireball, slowing into a stem and a cap, then drifting up
        for (int i = 0; i < (lite ? 0 : p.np); i++) {
            int o = i * 7;
            float tb = t - p.m[o + 4], life = p.m[o + 5];
            if (tb < 0 || tb > life) continue;
            float k = 1 - (float) Math.exp(-tb / 2.6f);
            float size = p.m[o + 3];
            double x = ox + p.m[o] * k, y = oy + p.m[o + 1] * k + 0.02 * sc * tb, z = oz + p.m[o + 2] * k;
            y = Math.min(y, ceil - 0.08 - size * 0.45);
            int fr = puffFrame(tb / life);
            w.frame(x, y, z, 0, size, PUFF + fr, p.m[o + 6] > 0, WHITE, fr <= 4 ? full : smokeLight);
        }
        // fireball: one big lit sphere sitting low, so the ground hides its lower half (a dome); under a low ceiling it
        // shrinks to fit instead of sinking into the floor
        int ff = fireFrame(t);
        if (ff >= 0) {
            double y = (p.ground ? p.groundY : bl.center.y) + 0.5 + 0.04 * sc * t;
            float size = sc * 6.8f;
            double room = ceil - y + 0.3 * sc;
            if (size * 0.5f > room) size = (float) Math.max(sc * 2, room * 2);
            w.frame(ox, y, oz, 0, size, FIREBALL + ff, (bl.seed & 1) != 0, WHITE, ff <= 9 ? full : smokeLight);
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
            w.billboard(x, y, z, 0.1f, size, size, 7, 1, 0, lerp(YELLOW, EMBER, u), full);
        }
    }

    /** Under water: no fire, no shockwave; round pale blue bubbles that pop out and rise fast. */
    static void bubbles(Writer w, Blast bl, Params p, float t, float sc, int env) {
        double ox = bl.center.x, oy = bl.center.y, oz = bl.center.z;
        for (int i = 0; i < p.np; i++) {
            int o = i * 7;
            float tb = t - p.m[o + 4] * 0.5f, life = p.m[o + 5] * 0.6f;
            if (tb < 0 || tb > life) continue;
            float k = 1 - (float) Math.exp(-tb / 3f);
            double x = ox + p.m[o] * k * 0.8, y = oy + p.m[o + 1] * k * 0.7 + 0.08 * tb, z = oz + p.m[o + 2] * k * 0.8;
            w.frame(x, y, z, 0, p.m[o + 3] * 0.6f, DUST_PUFF + dustFrame(tb / life), p.m[o + 6] > 0, BUBBLE, LightCoordsUtil.withBlock(env, 12));
        }
    }

    /** Dust where debris lands hard (small, about the size of the piece); it outlives the blast's own effect. A pop
     * has no smoke of its own, only the block's crumbs and the plop. */
    static void aftermath(Writer w, Blast bl, float t, ClientLevel level) {
        double[] p = w.tmp;
        for (Debris d : bl.debris) {
            if (d.nseg == 0) continue;
            float tf = t - d.launch;
            for (int k = 0; k < Math.min(2, d.ncontact); k++) {
                float tb = tf - d.contactT[k];
                if (tb < 0 || tb > 9 || d.contactK[k] < 0.35f || w.aftermath >= MAX_AFTERMATH) continue;
                d.position(d.contactT[k], p);
                float size = d.size * (1.2f + 0.8f * d.contactK[k]) * 0.75f;
                int env = DebrisRenderer.lightAt(level, p[0], p[1] + 0.3, p[2]);
                w.frame(p[0], p[1] - d.size * 0.5 + size * 0.3, p[2], 0.02f, size, DUST_PUFF + dustFrame(tb / 9), (d.popIndex & 1) != 0, DUST, env);
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
            float u0 = col * CU, v0 = row * CV;
            quad((float) (x - cx) + toCam.x * lift, (float) (y - cy) + toCam.y * lift, (float) (z - cz) + toCam.z * lift, a, b, u0, v0, u0 + CU, v0 + CV, color, light);
        }

        /** Round-look frame `n` (see FIREBALL, PUFF, DUST_PUFF) as a camera-facing square, optionally mirrored. */
        void frame(double x, double y, double z, float lift, float size, int n, boolean flip, int color, int light) {
            float k = nearFade(x, y, z);
            if (k <= 0) return;
            float h = size * k * 0.5f;
            a.set(right).mul(h);
            b.set(up).mul(h);
            float u0 = (n & 7) * CU, v0 = (16 + (n >> 3)) * CV;
            float px = (float) (x - cx) + toCam.x * lift, py = (float) (y - cy) + toCam.y * lift, pz = (float) (z - cz) + toCam.z * lift;
            if (flip) quad(px, py, pz, a, b, u0 + CU, v0, u0, v0 + CV, color, light);
            else quad(px, py, pz, a, b, u0, v0, u0 + CU, v0 + CV, color, light);
        }

        /** Sprites shrink away within 4 blocks of the camera, so smoke or dust never covers the view. */
        float nearFade(double x, double y, double z) {
            double dx = x - cx, dy = y - cy, dz = z - cz;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 >= 16) return 1;
            float u = (float) ((Math.sqrt(d2) - 1.5) / 2.5);
            return smooth(u);
        }

        /** A spark: the streak cell stretched along its flight direction, turned to face the camera around that axis. */
        void streak(double x, double y, double z, float dx, float dy, float dz, float len, float width, int color) {
            b.set(dx, dy, dz).normalize();
            a.set(b).cross(toCam);
            if (a.lengthSquared() < 1e-6f) a.set(right);
            a.normalize().mul(width * 0.5f);
            b.mul(len * 0.5f);
            quad((float) (x - cx), (float) (y - cy), (float) (z - cz), a, b, 6 * CU, CV, 7 * CU, 2 * CV, color, LightCoordsUtil.FULL_BRIGHT);
        }

        /** A 64 px ring frame (0..7) of the round look, flat on the ground or facing the camera, `d` blocks across. */
        void ring(double x, double y, double z, float lift, float d, int frame, boolean flat, int color, int light) {
            float u0 = (frame & 3) * 2 * CU, v0 = (20 + (frame >> 2) * 2) * CV;
            if (flat) {
                a.set(d * 0.5f, 0, 0);
                b.set(0, 0, -d * 0.5f);
                quad((float) (x - cx), (float) (y - cy), (float) (z - cz), a, b, u0, v0, u0 + 2 * CU, v0 + 2 * CV, color, light);
            } else {
                a.set(right).mul(d * 0.5f);
                b.set(up).mul(d * 0.5f);
                quad((float) (x - cx) + toCam.x * lift, (float) (y - cy) + toCam.y * lift, (float) (z - cz) + toCam.z * lift, a, b, u0, v0, u0 + 2 * CU, v0 + 2 * CV, color, light);
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
