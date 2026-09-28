package io.github.profetgit.havingablast.dev;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dev only (the demo, -Dhavingablast.demo): counts on the integrated server the blocks each blast removes, by the same
 * rule the client's capture uses, so the demo can prove the client captured exactly what the server broke. A removal
 * that came with a break event (2001: an attached block popping off, or a player mining) counts only next to a block
 * the blast removed outright.
 */
public final class BlastCount {
    public static final boolean ACTIVE = System.getProperty("havingablast.demo") != null;
    /** -Dhavingablast.probe.positions=1: print every counted position (to diff against the client's CAPTURE lines). */
    static final boolean PRINT = System.getProperty("havingablast.probe.positions") != null;
    public static int total;
    /** Removals by blasts no player got the packet for (past 64 blocks); the client may still see some as a nearby blast's debris. */
    public static int unsent;
    static ServerLevel level;
    static final Long2ObjectOpenHashMap<BlockState> before = new Long2ObjectOpenHashMap<>();
    static final LongOpenHashSet marked = new LongOpenHashSet();

    private BlastCount() {
    }

    /** Whether some player got this blast's explode packet: the server sends it only within 64 blocks (flung TNT in a
     * big chain often goes off farther out), and without it the client rightly draws nothing. */
    static boolean sent;
    static net.minecraft.world.phys.Vec3 center;

    public static void begin(ServerLevel l, net.minecraft.world.phys.Vec3 c) {
        if (!ACTIVE) return;
        center = c;
        sent = false;
        for (var p : l.players()) if (p.distanceToSqr(c) < 4096) sent = true;
        level = l;
        before.clear();
        marked.clear();
    }

    public static void onSetBlock(Level l, BlockPos pos) {
        if (level == null || l != level) return;
        long p = pos.asLong();
        if (!before.containsKey(p)) before.put(p, l.getBlockState(pos));
    }

    public static void onEvent(Level l, int type, BlockPos pos) {
        if (level != null && l == level && type == 2001) marked.add(pos.asLong());
    }

    public static void end() {
        if (level == null) return;
        LongOpenHashSet direct = new LongOpenHashSet(), side = new LongOpenHashSet();
        for (var e : before.long2ObjectEntrySet()) {
            BlockState old = e.getValue(), now = level.getBlockState(BlockPos.of(e.getLongKey()));
            if (!counts(old, now)) continue;
            (marked.contains(e.getLongKey()) ? side : direct).add(e.getLongKey());
        }
        int n = direct.size();
        if (PRINT) for (long p : direct) System.out.println("[habprobe] SREMOVED " + BlockPos.of(p).toShortString());
        for (long p : side) {
            if (!besideAny(p, direct)) continue;
            n++;
            if (PRINT) System.out.println("[habprobe] SREMOVED " + BlockPos.of(p).toShortString() + " (side)");
        }
        if (sent) total += n;
        else {
            unsent += n;
            if (PRINT) System.out.println("[habprobe] UNSENT blast at " + center + ": " + n + " removed");
        }
        level = null;
    }

    /** A real block (not TNT, not a piston part, not a fluid) became air, fluid or fire (beds, anchors and fireballs
     * set fire in the spots they just emptied, before the client hears of them). */
    public static boolean counts(BlockState old, BlockState now) {
        if (old.isAir() || old == now || old.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) return false;
        if (!(now.isAir() || now.getBlock() == Blocks.WATER || now.getBlock() == Blocks.LAVA || now.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock)) return false;
        if (old.getBlock() == Blocks.TNT || old.getBlock() == Blocks.MOVING_PISTON || old.getBlock() == Blocks.PISTON_HEAD) return false;
        return !(old.getBlock() instanceof LiquidBlock);
    }

    public static boolean besideAny(long p, LongOpenHashSet set) {
        BlockPos pos = BlockPos.of(p);
        for (Direction d : Direction.values()) if (set.contains(pos.relative(d).asLong())) return true;
        return false;
    }
}
