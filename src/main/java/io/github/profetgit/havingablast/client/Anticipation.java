package io.github.profetgit.havingablast.client;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * The tells before the bang, built on vanilla's own: primed TNT pumps through its last 12 ticks (three squash and
 * stretch beats, each faster and bigger) and holds a held-breath swell for the final tick and while it waits for the
 * server's explosion; the creeper's swell is pushed further.
 */
public final class Anticipation {
    static final float PUMP = 12;
    /** Fuse of the TNT being drawn (set by the renderer mixin, render thread only). */
    public static float fuse;

    private Anticipation() {
    }

    /**
     * Vanilla swells the creeper to 1.4 wide and 1.1 tall with a 1% shiver. Here it puffs up to 1.7 wide, squashes,
     * then stretches tall in the last fifth (the held breath), and shivers harder and faster as it fills up.
     * Scaled about the feet, like vanilla.
     */
    public static void creeper(float swelling, float age, PoseStack ps) {
        float f = Math.max(0, Math.min(1, swelling));
        float fill = f * f * (3 - 2 * f);
        float shiver = 1 + (float) Math.sin(age * (2.2f + 2.6f * f)) * 0.06f * f * f;
        float wide = 1 + 0.7f * fill;
        float tall = 1 + 0.12f * fill;
        if (f > 0.8f) {
            // held breath: narrower and taller for the last beat
            float k = (f - 0.8f) / 0.2f;
            k = k * k;
            wide -= 0.18f * k;
            tall += 0.28f * k;
        }
        ps.scale(wide * shiver, tall / shiver, wide * shiver);
    }

    /** Replaces the TNT renderer's uniform swell (about the block centre) with a grounded squash and stretch. */
    public static void tnt(PoseStack ps, float x, float y, float z) {
        if (!BlastClient.visuals() || fuse > PUMP) {
            ps.scale(x, y, z);
            return;
        }
        float f = Math.max(0, fuse);
        float base = x;
        float sy, sxz;
        if (f <= 1.2f) {
            // held breath: fat and still, just before the bang
            float k = 1 - f / 1.2f;
            sy = base * (1.08f + 0.06f * k);
            sxz = base * (1.08f + 0.1f * k);
        } else {
            float u = (PUMP - f) / (PUMP - 1.2f);
            float wave = (float) Math.sin(Math.PI * 2 * 3 * Math.pow(u, 1.35));
            float amp = 0.05f + 0.14f * u;
            sy = base * (1 + amp * wave);
            sxz = base * (1 - amp * 0.55f * wave);
        }
        ps.translate(0, 0.5f * (sy - 1), 0);
        ps.scale(sxz, sy, sxz);
    }
}
