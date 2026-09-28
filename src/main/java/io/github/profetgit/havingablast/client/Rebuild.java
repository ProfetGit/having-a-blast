package io.github.profetgit.havingablast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.havingablast.Config;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * The repair animation, recognised on the client without any packets of its own: the client remembers every block it
 * saw blow up (and where its debris came to rest); when the server puts exactly that block back at that spot, the
 * update is held while the block flies back in, grows from its debris size, turns upright and drops into its slot with
 * one squash that eases back to full height; then the real block (and any block entity data held with it) is applied, the flyer staying
 * drawn over it until the block's chunk mesh is up (HANDOFF). Plops fall in pitch
 * as the wall closes, the blast's rising plops in reverse. Variants (-Dhavingablast.fx): rise (default: hops out of the
 * ground below), rewind (from where the debris landed), drop (falls in from above).
 */
public final class Rebuild {
    /** What blew up at a spot, and where its debris ended. */
    static final class Blown {
        final BlockState state;
        final long when;
        double rx = Double.NaN, ry, rz;
        float size = 0.5f;

        Blown(BlockState state, long when) {
            this.state = state;
            this.when = when;
        }
    }

    static final class Flyer {
        final BlockPos pos;
        final BlockState state;
        final int flags;
        final double sx, sy, sz;
        final float size, dur, arc;
        final Quaternionf spin = new Quaternionf();
        float t0 = Float.NaN;
        boolean applied, landedFx;
        final List<ClientboundBlockEntityDataPacket> held = new ArrayList<>();
        int index;

        Flyer(BlockPos pos, BlockState state, int flags, double sx, double sy, double sz, float size, float dur, float arc) {
            this.pos = pos;
            this.state = state;
            this.flags = flags;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.size = size;
            this.dur = dur;
            this.arc = arc;
        }
    }

    static final Long2ObjectOpenHashMap<Blown> BLOWN = new Long2ObjectOpenHashMap<>();
    static final Long2ObjectOpenHashMap<Flyer> FLYING = new Long2ObjectOpenHashMap<>();
    static final RandomSource RNG = RandomSource.create();
    static final int MAX_BLOWN = 60_000;
    static final long FORGET_TICKS = 20L * 60 * 30;
    static final float SETTLE = 3;
    /**
     * Ticks the flyer stays drawn after its real block went in. The block only shows once its chunk section is re-meshed,
     * 1 to 3 frames later (more under load), and drawing nothing in between left a see-through hole that blinked open and
     * shut across the wall as the blocks landed.
     */
    static final float HANDOFF = 2;
    /**
     * In the slot (settle and hand-off) the flyer is 1.0005 blocks: exactly 1 left hairline cracks along its edges that
     * showed the dark hole behind. In the slot only its height springs: bulging 12% wider slid its faces over the
     * neighbours' faces in the same plane (a wall front, the ground), which z-fought in stripes, and narrowing bared a
     * sliver of the top of the block below, a light line blinking with the wobble.
     */
    static final float SLOT_SCALE = 1.0005f;
    /** Set while this class applies a held update itself. */
    static boolean applying;
    static int sequence;
    static float lastLand = -99;
    public static int rebuilt, decorPoofs;
    /** Where blocks landed lately (x, y, z, tick), for the frames and stands the server hangs back up after them. */
    static final double[] RECENT = new double[4 * 256];
    static int recentNext;

    private Rebuild() {
    }

    static String variant() {
        String fx = BlastClient.FX;
        return fx.contains("rewind") ? "rewind" : fx.contains("drop") ? "drop" : "rise";
    }

    static void remember(BlockPos pos, BlockState state) {
        if (BLOWN.size() >= MAX_BLOWN) return;
        BLOWN.put(pos.asLong(), new Blown(state, Blasts.ticks));
    }

    static void rested(Debris d, double x, double y, double z) {
        Blown b = BLOWN.get(d.origin.asLong());
        if (b == null) return;
        b.rx = x;
        b.ry = y;
        b.rz = z;
        b.size = d.size;
    }

    /** ClientLevel.setServerVerifiedBlockState, before it applies: returns true to hold the update for the animation. */
    public static boolean intercept(ClientLevel level, BlockPos pos, BlockState old, BlockState now, int flags) {
        if (applying) return false;
        long key = pos.asLong();
        Flyer busy = FLYING.remove(key);
        if (busy != null) apply(level, busy);
        if (BLOWN.isEmpty() || !BlastClient.visuals() || !Config.get().repairAnimation) return false;
        Blown b = BLOWN.get(key);
        if (b == null || b.state != now || !(old.isAir() || old.canBeReplaced() || !old.getFluidState().isEmpty())) return false;
        BLOWN.remove(key);
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        String v = variant();
        double sx, sy, sz;
        float size = b.size, arc;
        if (v.equals("drop")) {
            sx = cx;
            sy = cy + 4.5 + RNG.nextFloat();
            sz = cz;
            arc = 0;
        } else if (v.equals("rise") || Double.isNaN(b.rx) || Math.abs(b.rx - cx) + Math.abs(b.rz - cz) > 14) {
            sx = cx + (RNG.nextFloat() - 0.5f) * 0.6f;
            sy = cy - 1.2;
            sz = cz + (RNG.nextFloat() - 0.5f) * 0.6f;
            arc = 1.4f;
        } else {
            sx = b.rx;
            sy = b.ry;
            sz = b.rz;
            arc = 1.2f + (float) Math.min(2.5, Math.hypot(b.rx - cx, b.rz - cz) * 0.3);
        }
        double dist = Math.sqrt((sx - cx) * (sx - cx) + (sy - cy) * (sy - cy) + (sz - cz) * (sz - cz));
        float dur = (float) Math.max(8, Math.min(15, 7 + dist * 0.9));
        Flyer f = new Flyer(pos.immutable(), now, flags, sx, sy, sz, size, dur, arc);
        f.spin.rotationXYZ(RNG.nextFloat() * 6.28f, RNG.nextFloat() * 6.28f, RNG.nextFloat() * 6.28f);
        FLYING.put(key, f);
        return true;
    }

    /** Block entity data for a block still in flight waits for it. */
    public static boolean holdBlockEntity(ClientboundBlockEntityDataPacket p) {
        if (applying || FLYING.isEmpty()) return false;
        Flyer f = FLYING.get(p.getPos().asLong());
        if (f == null || f.applied) return false;
        f.held.add(p);
        return true;
    }

    static void apply(ClientLevel level, Flyer f) {
        if (f.applied) return;
        f.applied = true;
        applying = true;
        try {
            level.setServerVerifiedBlockState(f.pos, f.state, f.flags);
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) for (ClientboundBlockEntityDataPacket p : f.held) conn.handleBlockEntityData(p);
        } finally {
            applying = false;
        }
        rebuilt++;
        int o = (recentNext++ % 256) * 4;
        RECENT[o] = f.pos.getX() + 0.5;
        RECENT[o + 1] = f.pos.getY() + 0.5;
        RECENT[o + 2] = f.pos.getZ() + 0.5;
        RECENT[o + 3] = Blasts.ticks;
    }

    /**
     * An item frame, painting or armor stand appearing beside blocks that just flew back in is one the server kept
     * through the blast and hangs back up now: it pops in with a puff and a plop, like the blocks.
     */
    public static void onAddEntity(net.minecraft.world.entity.EntityType<?> type, double x, double y, double z) {
        if (recentNext == 0 || !BlastClient.visuals() || !Config.get().repairAnimation) return;
        if (type != net.minecraft.world.entity.EntityTypes.ITEM_FRAME && type != net.minecraft.world.entity.EntityTypes.GLOW_ITEM_FRAME
            && type != net.minecraft.world.entity.EntityTypes.PAINTING && type != net.minecraft.world.entity.EntityTypes.ARMOR_STAND) return;
        for (int i = 0; i < Math.min(recentNext, 256); i++) {
            int o = i * 4;
            if (Blasts.ticks - RECENT[o + 3] > 120) continue;
            double dx = RECENT[o] - x, dy = RECENT[o + 1] - y, dz = RECENT[o + 2] - z;
            if (dx * dx + dy * dy + dz * dz > 9) continue;
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;
            double py = type == net.minecraft.world.entity.EntityTypes.ARMOR_STAND ? y + 0.3 : y;
            Boom.poof(x, py, z, 0.9f);
            level.playLocalSound(x, y, z, SoundEvents.CHICKEN_EGG, SoundSource.BLOCKS, 0.5f, 1.3f, false);
            decorPoofs++;
            return;
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            BLOWN.clear();
            FLYING.clear();
            return;
        }
        if (Blasts.ticks % 200 == 0) BLOWN.values().removeIf(b -> Blasts.ticks - b.when > FORGET_TICKS);
        // a flyer that was never drawn (the view was elsewhere) still lands on time
        float now = Blasts.ticks;
        for (Flyer f : new ArrayList<>(FLYING.values())) {
            if (Float.isNaN(f.t0)) f.t0 = now;
            if (now - f.t0 > f.dur + SETTLE + HANDOFF + 2) {
                apply(mc.level, f);
                FLYING.remove(f.pos.asLong());
            }
        }
    }

    static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** Draws the flyers; applies each real block at its landing frame and keeps drawing the settle over it briefly. */
    static void draw(float now, Vec3 cam, PoseStack ps, SubmitNodeCollector c, ClientLevel level) {
        if (FLYING.isEmpty()) return;
        List<Flyer> done = null;
        for (Flyer f : FLYING.values()) {
            if (Float.isNaN(f.t0)) f.t0 = now;
            float t = now - f.t0;
            double cx = f.pos.getX() + 0.5, cy = f.pos.getY() + 0.5, cz = f.pos.getZ() + 0.5;
            double x, y, z;
            float scale, sy = 1, sxz = 1;
            Quaternionf q = DebrisRenderer.Q;
            if (t < f.dur) {
                float u = t / f.dur;
                float e = u < 0.5f ? 2 * u * u : 1 - (float) Math.pow(-2 * u + 2, 2) / 2;
                x = f.sx + (cx - f.sx) * e;
                z = f.sz + (cz - f.sz) * e;
                y = f.sy + (cy - f.sy) * e + f.arc * 4 * u * (1 - u);
                // full size before the last stretch: a flyer still growing (or narrowed by the stretch) as it dropped into its
                // slot bared a thin line of the block below, or of the dark hole, around its edges
                scale = f.size + (1 - f.size) * smooth(u * 1.45f);
                q.set(f.spin).slerp(DebrisRenderer.Q2.identity(), smooth(u * 1.15f));
                // stretched taller toward the end, so it reads as dropping in (never narrower than its slot)
                if (u > 0.7f) sy = 1 + 0.18f * (u - 0.7f) / 0.3f;
            } else {
                // squash into the slot, spring back a little taller, settle; the real block goes in when it has settled
                if (!f.landedFx) land(level, f, now);
                float s2 = t - f.dur;
                if (s2 >= SETTLE) {
                    if (!f.applied) apply(level, f);
                    if (s2 >= SETTLE + HANDOFF || level.getBlockState(f.pos) != f.state) {
                        if (done == null) done = new ArrayList<>();
                        done.add(f);
                        continue;
                    }
                }
                x = cx;
                y = cy;
                z = cz;
                q.identity();
                scale = SLOT_SCALE;
                // one squash on contact that eases back to exactly full height, never past it: a springy settle bobbed the
                // block up and down around flush 2-3 times, and each crossing bared its edges against the neighbours (dark
                // seams in a floor, light lines on a wall) that blinked with the bob
                // shallow: in a floor or a wall the dip shows as a dark outline against the neighbours while it recovers
                float k = 1 - smooth(s2 / SETTLE);
                sy = 1 - 0.08f * k * k;
                sxz = 1;
            }
            int light = DebrisRenderer.lightAt(level, x, y + 0.6, z);
            ps.pushPose();
            ps.translate(x - cam.x, y - cam.y, z - cam.z);
            if (sy != 1 || sxz != 1) {
                ps.translate(0, -scale * 0.5f, 0);
                ps.scale(sxz, sy, sxz);
                ps.translate(0, scale * 0.5f, 0);
            }
            ps.rotateAround(q, 0, 0, 0);
            ps.scale(scale, scale, scale);
            ps.translate(-0.5f, -0.5f, -0.5f);
            DebrisRenderer.submitModel(Blasts.model(f.state, level, f.pos), ps, c, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        if (done != null) for (Flyer f : done) FLYING.remove(f.pos.asLong());
    }

    static void land(ClientLevel level, Flyer f, float now) {
        f.landedFx = true;
        if (now - lastLand > 20) sequence = 0;
        lastLand = now;
        float pitch = Math.max(0.6f, 1.7f - 0.045f * sequence++);
        double x = f.pos.getX() + 0.5, y = f.pos.getY() + 0.5, z = f.pos.getZ() + 0.5;
        if (sequence % 2 == 1) {
            level.playLocalSound(x, y, z, SoundEvents.CHICKEN_EGG, SoundSource.BLOCKS, 0.5f, pitch, false);
            level.playLocalSound(x, y, z, f.state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.55f, 0.8f + pitch * 0.15f, false);
        }
        Fx.crumbs(level, f.state, x, y - 0.3, z, 2);
        // a soft puff where it lands, at the base of the slot
        Boom.poof(x, y - 0.35, z, 1.1f);
    }
}
