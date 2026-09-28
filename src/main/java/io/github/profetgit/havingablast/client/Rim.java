package io.github.profetgit.havingablast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The crater's rim jiggles: surviving blocks next to the blown-out ones get a copy drawn over them, a hair bigger than
 * the block so it hides it, that swells and wobbles back over a few frames (a real block can't move). Up to 48 per
 * blast, the ones with an open top, nearest the centre first; each starts when the ripple of launches reaches it.
 */
final class Rim {
    static final int MAX = 48;
    static final float LIFE = 9;

    private Rim() {
    }

    /** Collect the rim once the blast's blocks are all in. */
    static void collect(ClientLevel level, Blast b) {
        it.unimi.dsi.fastutil.longs.LongOpenHashSet gone = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (Debris d : b.debris) gone.add(d.origin.asLong());
        it.unimi.dsi.fastutil.longs.LongOpenHashSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        java.util.List<long[]> found = new java.util.ArrayList<>();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (Debris d : b.debris) {
            for (Direction dir : Direction.values()) {
                mp.setWithOffset(d.origin, dir);
                long key = mp.asLong();
                if (gone.contains(key) || !seen.add(key)) continue;
                BlockState st = level.getBlockState(mp);
                if (st.isAir() || !st.isSolidRender() || !st.getFluidState().isEmpty()) continue;
                BlockPos above = mp.above();
                if (level.getBlockState(above).isSolidRender() && !gone.contains(above.asLong())) continue;
                double dist = b.center.distanceTo(Vec3.atCenterOf(mp));
                found.add(new long[] {key, Double.doubleToRawLongBits(dist)});
            }
        }
        found.sort((x, y) -> Double.compare(Double.longBitsToDouble(x[1]), Double.longBitsToDouble(y[1])));
        int n = Math.min(MAX, found.size());
        b.rim = new long[n];
        b.rimState = new BlockState[n];
        b.rimDelay = new float[n];
        for (int i = 0; i < n; i++) {
            b.rim[i] = found.get(i)[0];
            b.rimState[i] = level.getBlockState(BlockPos.of(b.rim[i]));
            double dist = Double.longBitsToDouble(found.get(i)[1]);
            b.rimDelay[i] = Blasts.HOLD + (float) Math.min(1, dist / b.reach) * Blasts.RIPPLE;
        }
    }

    static void draw(Blast b, float t, Vec3 cam, PoseStack ps, SubmitNodeCollector c, ClientLevel level) {
        if (b.rim == null) return;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int i = 0; i < b.rim.length; i++) {
            float u = t - b.rimDelay[i];
            if (u < 0 || u > LIFE) continue;
            mp.set(BlockPos.getX(b.rim[i]), BlockPos.getY(b.rim[i]), BlockPos.getZ(b.rim[i]));
            // the block may be gone or changed since (a later blast): then no copy
            if (level.getBlockState(mp) != b.rimState[i]) continue;
            // a quick swell, then a damped wobble; never under 1.004, so the copy always hides the block inside
            float e = (float) Math.exp(-u / 2.4f);
            float wob = (float) Math.sin(u * 1.9f) * e;
            float sy = 1.004f + 0.10f * Math.max(0, wob) + 0.04f * e;
            float sxz = 1.004f + 0.06f * Math.max(0, -wob) + 0.03f * e;
            int light = DebrisRenderer.lightAt(level, mp.getX() + 0.5, mp.getY() + 1.5, mp.getZ() + 0.5);
            ps.pushPose();
            ps.translate(mp.getX() + 0.5 - cam.x, mp.getY() - cam.y, mp.getZ() + 0.5 - cam.z);
            ps.scale(sxz, sy, sxz);
            ps.translate(-0.5f, -0.002f, -0.5f);
            DebrisRenderer.submitModel(Blasts.model(b.rimState[i], level, mp), ps, c, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }
}
