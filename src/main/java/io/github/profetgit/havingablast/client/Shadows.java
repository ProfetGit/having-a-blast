package io.github.profetgit.havingablast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/**
 * A soft blob under every flying piece, drawn with vanilla's own entity shadow texture and render type so it looks like
 * a mob's shadow. Pieces queue one per frame; the batch is written after all debris are submitted.
 */
final class Shadows {
    static final Identifier TEX = Identifier.withDefaultNamespace("textures/misc/shadow.png");
    static final int MAX_DROP = 12;
    static RenderType type;
    static float[] q = new float[5 * 256];
    static int n;
    static final BlockPos.MutableBlockPos MP = new BlockPos.MutableBlockPos();

    private Shadows() {
    }

    static void begin() {
        n = 0;
    }

    /** Where the ground is below (x, y, z): the top of the first collision shape within MAX_DROP blocks, or NaN. */
    static double ground(ClientLevel level, double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int by = (int) Math.floor(y);
        int min = level.getMinY();
        for (int i = 0; i <= MAX_DROP; i++) {
            int yy = by - i;
            if (yy < min) break;
            MP.set(bx, yy, bz);
            BlockState st = level.getBlockState(MP);
            if (st.isAir()) continue;
            VoxelShape shape = st.getCollisionShape(level, MP);
            if (shape.isEmpty()) continue;
            double top = yy + shape.max(net.minecraft.core.Direction.Axis.Y);
            if (top <= y + 0.01) return top;
        }
        return Double.NaN;
    }

    /** Queue a shadow centred under (x, z) on the ground height gy; radius in blocks, alpha 0..1. */
    static void add(double x, double gy, double z, float radius, float alpha) {
        if (alpha <= 0.02f || radius <= 0.01f) return;
        if (n * 5 + 5 > q.length) q = java.util.Arrays.copyOf(q, q.length * 2);
        int o = n++ * 5;
        q[o] = (float) x;
        q[o + 1] = (float) gy;
        q[o + 2] = (float) z;
        q[o + 3] = radius;
        q[o + 4] = alpha;
    }

    static void submit(PoseStack ps, double cx, double cy, double cz, SubmitNodeCollector c) {
        if (n == 0) return;
        if (type == null) type = RenderTypes.entityShadow(TEX);
        final int count = n;
        final float[] data = q;
        Compat.geometry(c, ps, type, (pose, vc) -> {
            var m = pose.pose();
            Vector3f nrm = pose.transformNormal(0, 1, 0, new Vector3f());
            for (int i = 0; i < count; i++) {
                int o = i * 5;
                float x = (float) (data[o] - cx), y = (float) (data[o + 1] - cy) + 0.012f, z = (float) (data[o + 2] - cz);
                float r = data[o + 3];
                int color = ((int) (data[o + 4] * 255) & 255) << 24 | 0xFFFFFF;
                vertex(vc, m, nrm, x - r, y, z - r, 0, 0, color);
                vertex(vc, m, nrm, x - r, y, z + r, 0, 1, color);
                vertex(vc, m, nrm, x + r, y, z + r, 1, 1, color);
                vertex(vc, m, nrm, x + r, y, z - r, 1, 0, color);
            }
        });
        n = 0;
    }

    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, Vector3f nrm, float x, float y, float z, float u, float v, int color) {
        vc.addVertex(m, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(nrm.x, nrm.y, nrm.z);
    }

    static boolean enabled() {
        return Minecraft.getInstance().options.entityShadows().get();
    }
}
