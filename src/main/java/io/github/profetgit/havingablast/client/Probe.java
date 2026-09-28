package io.github.profetgit.havingablast.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Dev only (-Dhavingablast.probe=1 or the demo): logs explosion packets, block updates and entity adds with tick and time. */
public final class Probe {
    public static final boolean ON = !System.getProperty("havingablast.probe", "").isEmpty() || System.getProperty("havingablast.demo") != null;
    public static final boolean POSITIONS = System.getProperty("havingablast.probe.positions") != null;
    private static final long T0 = System.nanoTime();

    private Probe() {
    }

    static String stamp() {
        Minecraft mc = Minecraft.getInstance();
        long gt = mc.level == null ? -1 : mc.level.getGameTime();
        return String.format("[habprobe] gt %d t %.2fms", gt, (System.nanoTime() - T0) / 1e6);
    }

    public static void explosion(Vec3 c, float radius, int count) {
        System.out.println(stamp() + String.format(" EXPLODE %.2f %.2f %.2f r %.2f count %d", c.x, c.y, c.z, radius, count));
    }

    public static void block(BlockPos pos, BlockState old, BlockState now) {
        System.out.println(stamp() + " BLOCK " + pos.toShortString() + " " + old + " -> " + now);
    }

    public static void entity(String type, int id, double x, double y, double z) {
        System.out.println(stamp() + String.format(" ADD %s #%d %.2f %.2f %.2f", type, id, x, y, z));
    }
}
