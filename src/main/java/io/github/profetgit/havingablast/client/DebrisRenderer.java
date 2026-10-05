package io.github.profetgit.havingablast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
//? if >=1.21.9 {
import net.minecraft.client.renderer.state.level.LevelRenderState;
//?}
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws every live debris in the entity pass (submitted next to the entities, so shader packs shade them like
 * entities), evaluating its flight, spin, squash and pop at this frame's exact partial tick.
 */
public final class DebrisRenderer {
    static final double[] P = new double[3];
    static final Quaternionf Q = new Quaternionf(), Q2 = new Quaternionf(), QD = new Quaternionf();
    static final Vector3f UP = new Vector3f(0, 1, 0);
    static final BlockPos.MutableBlockPos MP = new BlockPos.MutableBlockPos();
    static Object modelSet;
    /** Debris drawn in the last frame, for the demo's curves. */
    public static int drawn;
    // The frustum this frame's entities are culled with (26.2 keeps it in the camera render state).
    public static net.minecraft.client.renderer.culling.Frustum FRUSTUM;

    private DebrisRenderer() {
    }

    /** Nanoseconds this mod spent on the render thread since the last read (submit, bake, the FX callback, ticks). */
    public static long spentNanos;

    //? if >=1.21.9 {
    public static void submit(PoseStack ps, LevelRenderState st, SubmitNodeCollector c) {
        //? if >=26.2 {
        FRUSTUM = st.cameraRenderState.cullFrustum;
        //?}
        submit(ps, st.cameraRenderState.pos, st.cameraRenderState.orientation, c);
    }
    //?}

    public static void submit(PoseStack ps, Vec3 cam, Quaternionf rot, SubmitNodeCollector c) {
        long t0 = System.nanoTime();
        try {
            submitTimed(ps, cam, rot, c);
        } finally {
            spentNanos += System.nanoTime() - t0;
        }
    }

    static void submitTimed(PoseStack ps, Vec3 cam, Quaternionf rot, SubmitNodeCollector c) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        drawn = 0;
        if (level == null || Blasts.blasts.isEmpty() && Rebuild.FLYING.isEmpty() && Boom.POOFS.isEmpty()) return;
        //? if >=26.2 {
        Object set = mc.getModelManager().getBlockModelSet();
        //?}
        //? if >=1.21.5 <26.2 {
        /*Object set = mc.getModelManager().getMissingBlockStateModel();
        *///?}
        //? if <1.21.5 {
        /*Object set = mc.getModelManager().getMissingModel();
        *///?}
        if (set != modelSet) {
            Blasts.clearModels();
            modelSet = set;
        }
        float now = Blasts.clock(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        for (Blast b : Blasts.blasts) {
            if (Float.isNaN(b.t0)) b.t0 = now;
        }
        Blasts.bakePending(level, now);
        Boom.submit(now, cam, rot, ps, c, level);
        Rebuild.draw(now, cam, ps, c, level);
        for (Blast b : Blasts.blasts) {
            float t = now - b.t0;
            Rim.draw(b, t, cam, ps, c, level);
            var frustum = FRUSTUM;
            for (Debris d : b.debris) {
                if (!d.dead) draw(d, t, cam, ps, c, level, frustum);
            }
        }
    }

    static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    static float easeOut(float x) {
        x = Math.max(0, Math.min(1, x));
        return 1 - (1 - x) * (1 - x);
    }

    static final net.minecraft.world.phys.AABB BOX = new net.minecraft.world.phys.AABB(0, 0, 0, 0, 0, 0);

    static void draw(Debris d, float t, Vec3 cam, PoseStack ps, SubmitNodeCollector c, ClientLevel level, net.minecraft.client.renderer.culling.Frustum frustum) {
        float half;
        float flash = t < Blasts.HOLD ? 1 - t / Blasts.HOLD : 0;
        float sy = 1, sxz = 1, stretch = 1;
        float scale;
        if (d.nseg == 0 || t < d.launch) {
            P[0] = d.origin.getX() + 0.5;
            P[1] = d.origin.getY() + 0.5;
            P[2] = d.origin.getZ() + 0.5;
            Q.identity();
            scale = 1.002f;
        } else {
            float tf = t - d.launch;
            d.position(tf, P);
            scale = d.size + (1 - d.size) * (1 - smooth(tf / 4));
            // tumble until the first landing, then settle into the nearest flat orientation
            float fc = Math.min(tf, d.firstContact);
            float angle = d.spin * fc + d.spin * 0.35f * Math.max(0, tf - d.firstContact);
            Q.identity().rotateAxis(angle, d.axis.x, d.axis.y, d.axis.z);
            if (tf > d.firstContact) {
                float u = smooth((tf - d.firstContact) / Math.max(2f, d.restT - d.firstContact + 3f));
                Q.slerp(d.restRot, u);
            }
            // launch stretch along the velocity
            if (tf < 5) {
                float e = 1 - tf / 5f;
                stretch = 1 + 0.35f * e * e * Math.min(1, d.launchSpeed / 0.8f);
            }
            // landing squash (world-vertical, about the bottom)
            for (int i = 0; i < d.ncontact; i++) {
                float u = tf - d.contactT[i];
                if (u < 0 || u > 4.5f) continue;
                float e = u < 1 ? easeOut(u) : u < 1.5f ? 1 : 1 - smooth((u - 1.5f) / 3f);
                float a = d.contactK[i] * e;
                sy = Math.min(sy, 1 - 0.32f * a);
                sxz = Math.max(sxz, 1 + 0.16f * a);
            }
            // the pop: squash, snap stretch, vanish
            float tp = t - d.popT;
            if (tp >= 0) {
                if (tp < 3) {
                    float a = smooth(tp / 3);
                    sy = 1 - 0.28f * a;
                    sxz = 1 + 0.16f * a;
                } else if (tp < 4) {
                    float a = easeOut(tp - 3);
                    sy = 0.72f + (1.32f - 0.72f) * a;
                    sxz = 1.16f + (0.84f - 1.16f) * a;
                    if (!d.popped) pop(d, level);
                } else if (tp < 6) {
                    if (!d.popped) pop(d, level);
                    float a = (tp - 4) / 2;
                    float k = 1 - a * a;
                    sy = 1.32f * k;
                    sxz = 0.84f * k;
                } else {
                    if (!d.popped) pop(d, level);
                    d.dead = true;
                    return;
                }
            }
        }
        half = scale * 0.5f * sy;
        // off screen: not submitted (the pop above still runs, so its sound and drops happen on time)
        if (frustum != null && !frustum.isVisible(new net.minecraft.world.phys.AABB(P[0] - 1, P[1] - 1, P[2] - 1, P[0] + 1, P[1] + 1, P[2] + 1))) return;
        int light = light(d, level);
        ps.pushPose();
        ps.translate(P[0] - cam.x, P[1] - cam.y, P[2] - cam.z);
        if (sy != 1 || sxz != 1) {
            ps.translate(0, -scale * 0.5f, 0);
            ps.scale(sxz, sy, sxz);
            ps.translate(0, scale * 0.5f, 0);
        }
        if (stretch != 1) {
            QD.rotationTo(UP, d.launchDir);
            ps.rotateAround(QD, 0, 0, 0);
            float inv = 1 / (float) Math.sqrt(stretch);
            ps.scale(inv, stretch, inv);
            ps.rotateAround(Q2.set(QD).conjugate(), 0, 0, 0);
        }
        ps.rotateAround(Q, 0, 0, 0);
        ps.scale(scale, scale, scale);
        ps.translate(-0.5f, -0.5f, -0.5f);
        int overlay = flash > 0 ? OverlayTexture.pack(OverlayTexture.u(flash), OverlayTexture.v(false)) : OverlayTexture.NO_OVERLAY;
        submitModel(d.model, ps, c, light, overlay);
        ps.popPose();
        drawn++;
    }

    static void pop(Debris d, ClientLevel level) {
        d.popped = true;
        Blasts.totalPopped++;
        Blast b = d.blast;
        double x = P[0], y = P[1], z = P[2];
        Drops.revealAll(d.items);
        if (d.lost || !io.github.profetgit.havingablast.Config.get().pops) {
            // quiet: no crumbs, poof or sound, the piece just shrinks away
            d.quiet = true;
            return;
        }
        Fx.crumbs(level, d.state, x, y, z, 5);
        float now = Blasts.ticks;
        if (now - b.lastPopSound >= 1.2f) {
            b.lastPopSound = now;
            int n = Math.max(1, b.debris.size() - 1);
            float pitch = 0.8f + 0.9f * (float) Math.pow(d.popIndex / (float) n, 0.8);
            Fx.plop(level, d.state, x, y, z, Math.min(2f, pitch));
        }
        b.pops++;
    }

    static int light(Debris d, ClientLevel level) {
        int packed = lightAt(level, P[0], P[1], P[2]);
        float bl = LightCoordsUtil.block(packed), sk = LightCoordsUtil.sky(packed);
        if (d.light < 0) {
            d.light = bl;
            d.sky = sk;
        } else {
            d.light += (bl - d.light) * 0.35f;
            d.sky += (sk - d.sky) * 0.35f;
        }
        int packedOut = LightCoordsUtil.pack(Math.round(d.light), Math.round(d.sky));
        return d.model.emission > 0 ? LightCoordsUtil.withBlock(packedOut, Math.max(Math.round(d.light), d.model.emission)) : packedOut;
    }

    /** Light at a point; inside an opaque block (a debris flying through the ground) the brightest open neighbour. */
    static int lightAt(ClientLevel level, double x, double y, double z) {
        MP.set(Math.floor(x), Math.floor(y), Math.floor(z));
        if (!level.getBlockState(MP).isSolidRender()) {
            return Compat.worldLight(level, MP);
        }
        int bl = 0, sk = 0;
        int bx = MP.getX(), by = MP.getY(), bz = MP.getZ();
        for (int i = 0; i < 6; i++) {
            MP.set(bx + (i == 0 ? 1 : i == 1 ? -1 : 0), by + (i == 2 ? 1 : i == 3 ? -1 : 0), bz + (i == 4 ? 1 : i == 5 ? -1 : 0));
            int n = Compat.worldLight(level, MP);
            bl = Math.max(bl, LightCoordsUtil.block(n));
            sk = Math.max(sk, LightCoordsUtil.sky(n));
        }
        return LightCoordsUtil.pack(bl, sk);
    }

    //? if >=26.2 {
    //
    // Sodium draws block models from BlockModelRenderState.submit; a direct submitBlockModel then drew nothing (checked
    // with Sodium 0.9.2 on 26.3). With it present, the debris go through vanilla's submit (a small list copy per call);
    // without it, the cached parts go straight to the collector. Fabric API alone (Indigo) is the other way round:
    // through BlockModelRenderState.submit the debris were invisible (0.2.3), the direct path draws them.

    static final boolean SLOW = BlastClient.FX.contains("slowpath") || !BlastClient.FX.contains("fastpath")
        && BlastClient.present("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
    //?}
    //? if >=26.2 {
    static void submitModel(Blasts.Model m, PoseStack ps, SubmitNodeCollector c, int light, int overlay) {
        if (m.fast && !SLOW) {
            if (!m.parts.isEmpty()) c.submitBlockModel(ps, m.type, m.parts, m.tints, light, overlay, 0);
        } else {
            m.rs.submit(ps, c, light, overlay, 0);
        }
    }
    //?}
    //? if <26.2 {
    /*static void submitModel(Blasts.Model m, PoseStack ps, SubmitNodeCollector c, int light, int overlay) {
        Compat.geometry(c, ps, m.type, (pose, vc) -> {
            for (int i = 0, n = m.quads.size(); i < n; i++) {
                net.minecraft.client.renderer.block.model.BakedQuad q = m.quads.get(i);
                int ti = q.tintIndex();
                float r = 1, g = 1, b = 1;
                if (ti >= 0 && ti < m.tints.length) {
                    int col = m.tints[ti];
                    r = (col >> 16 & 255) / 255f;
                    g = (col >> 8 & 255) / 255f;
                    b = (col & 255) / 255f;
                }
                vc.putBulkData(pose, q, r, g, b, 1f, light, overlay);
            }
        });
    }
*///?}
}
