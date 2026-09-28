package io.github.profetgit.havingablast.client;

import io.github.profetgit.havingablast.HavingABlast;

/** Client-side setup and settings; only ever reached from client mixins, so a dedicated server never loads client classes. */
public final class BlastClient {
    private static boolean started;
    /** -Dhavingablast.vanilla=1: visuals off (vanilla-vs-mod captures). */
    static final boolean VANILLA = !System.getProperty("havingablast.vanilla", "").isEmpty();
    /** -Dhavingablast.fx=<name>: a look variant under review. */
    public static final String FX = System.getProperty("havingablast.fx", "");
    /** Explosive Enhancement installed: its puff is drawn instead of this mod's. */
    public static final boolean EXPLOSIVE_ENHANCEMENT = present("net.superkat.explosiveenhancement.ExplosiveEnhancementClient");

    static boolean present(String cls) {
        try {
            Class.forName(cls, false, BlastClient.class.getClassLoader());
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private BlastClient() {
    }

    public static void init() {
        if (started) return;
        started = true;
        io.github.profetgit.havingablast.Config.load(net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath());
        HavingABlast.LOG.info("Having a Blast client visuals ready{}{}", VANILLA ? " (off: -Dhavingablast.vanilla)" : "",
            EXPLOSIVE_ENHANCEMENT ? " (Explosive Enhancement found: its explosion puff is used)" : "");
    }

    public static boolean visuals() {
        return !VANILLA && io.github.profetgit.havingablast.Config.get().visuals;
    }

    /** Debris size: a mix of 0.38 to 0.78 blocks, most near half a block (picked 2026-09-27); fx "full" and "mini" for comparisons. */
    static float debrisSize(net.minecraft.util.RandomSource r) {
        if (FX.contains("full")) return 1.0f;
        if (FX.contains("mini")) return 0.5f;
        return 0.38f + 0.4f * r.nextFloat() * r.nextFloat();
    }

    static double spread() {
        return FX.contains("full") ? 1.0 : 1.1;
    }

    /** Intensity setting as a factor, 0.5 to 1.5. */
    static float intensity() {
        return Math.max(50, Math.min(150, io.github.profetgit.havingablast.Config.get().intensity)) / 100f;
    }

    static double power() {
        return 0.62 * (0.75 + 0.25 * intensity());
    }
}
