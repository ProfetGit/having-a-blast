package io.github.profetgit.havingablast.repair;

import io.github.profetgit.havingablast.Config;
import io.github.profetgit.havingablast.HavingABlast;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Blast auto repair (server side; creepers, TNT, beds, anchors, crystals, ghast fireballs, the wither, each with its
 * own toggle). Recording: while a repairing blast runs, every block change in that level is recorded (first "before"
 * state wins, block entity data for removed blocks, the fire it starts too) and the item and XP drops of its block
 * pass are cancelled, container spills included, so nothing is duplicated. Item frames, paintings and armor stands
 * it breaks, or leaves without a wall, are kept whole (items and all) and go back after the blocks (see Decor). TNT
 * the blast primes is used up and not restored. Aftershocks next to a pending hole (sand falling in, a cactus or sugar cane popping off) are recorded
 * the same way. Rebuild: after the delay, each group puts its blocks back a few per tick, bottom row first and outer
 * rim inward, full blocks before the ones attached to them, without neighbour or shape updates until the whole group
 * is in; then its blocks are updated once. What a player placed in a hole is never overwritten (the recorded block
 * drops its items instead, as vanilla would have); flowing water, snow and grass that crept in are replaced.
 */
public final class Repair {
    /** Recording context of the blast (or aftershock) being processed on this thread. */
    static final class Recording {
        final ServerLevel level;
        final Ledger ledger;
        final Ledger.Group group;
        final LongOpenHashSet seen = new LongOpenHashSet();
        final LongOpenHashSet primed = new LongOpenHashSet();
        final Recording outer;
        /** Drops are held back only while blocks break: mobs the blast kills drop their loot as usual. */
        boolean blockPass;
        boolean wither;

        Recording(ServerLevel level, Ledger ledger, Ledger.Group group, Recording outer) {
            this.level = level;
            this.ledger = ledger;
            this.group = group;
            this.outer = outer;
        }
    }

    static final ThreadLocal<Recording> CURRENT = new ThreadLocal<>();
    /** Depth of player actions being handled (placing, buckets): changes then mark pending entries as touched. */
    static final ThreadLocal<int[]> PLAYER = ThreadLocal.withInitial(() -> new int[1]);
    /** Set while this mod puts blocks back, so its own changes aren't recorded. */
    static boolean restoring;
    static boolean warnedCap;
    static Object configFor;
    /** Counters for the tests. */
    public static int recordedTotal, restoredTotal, skippedTotal, suppressedDrops;

    private Repair() {
    }

    // ---------------------------------------------------------------- recording

    /** ServerExplosion.explode, start: the whole blast is recorded (entity pass, block pass and the fire it starts). */
    public static void beginExplosion(ServerLevel level, Entity direct, net.minecraft.world.phys.Vec3 center) {
        Kind kind = Kind.of(direct);
        if (kind == null || !kind.enabled()) {
            // a blast that doesn't repair still marks its place in the stack, so the matching end closes nothing
            CURRENT.set(new Recording(level, null, null, CURRENT.get()));
            return;
        }
        Ledger ledger = Ledger.of(level);
        if (ledger.pending() >= Config.get().maxPendingBlocks) {
            if (!warnedCap) HavingABlast.LOG.warn("Having a Blast: {} blocks already wait for repair (maxPendingBlocks); this blast breaks as in vanilla", ledger.pending());
            warnedCap = true;
            CURRENT.set(new Recording(level, null, null, CURRENT.get()));
            return;
        }
        long due = level.getGameTime() + Config.get().repairDelaySeconds * 20L;
        Ledger.Group g = ledger.newGroup(kind, center.x, center.y, center.z, due);
        CURRENT.set(new Recording(level, ledger, g, CURRENT.get()));
    }

    public static void endExplosion(Level level) {
        Recording r = CURRENT.get();
        if (r == null || r.level != level) return;
        if (r.ledger == null) CURRENT.set(r.outer);
        else finish(r);
    }

    /** ServerExplosion.interactWithBlocks, start and end: only its drops are held back. */
    public static void blockPass(Level level, boolean on) {
        Recording r = active(level);
        if (r != null) r.blockPass = on;
    }

    /** The recording running in this level on this thread, if it repairs. */
    static Recording active(Level level) {
        Recording r = CURRENT.get();
        return r != null && r.level == level && r.ledger != null ? r : null;
    }

    /** Close a recording: note the state each position was left in, drop what didn't really change and used-up TNT. */
    static void finish(Recording r) {
        Decor.sweep(r);
        CURRENT.set(r.outer);
        Ledger l = r.ledger;
        for (long p : r.seen) {
            Ledger.Entry e = l.entries.get(p);
            if (e == null || e.group != r.group.id) continue;
            BlockPos pos = BlockPos.of(p);
            BlockState now = r.level.getBlockState(pos);
            if (r.primed.contains(p) || now == e.before && e.after == null) {
                l.entries.remove(p);
                continue;
            }
            e.after = now;
            // a block that survived (only its shape or type changed) keeps its own, live block entity
            if (!removed(now)) e.be = null;
            r.group.include(p);
        }
        // a new blast over a pending one: they rebuild together, as one, after the later delay
        for (Ledger.Group o : new ArrayList<>(l.groups.values())) {
            if (o != r.group && !o.started && o.overlaps(r.group)) l.merge(o, r.group);
        }
        if (r.group.minX > r.group.maxX && r.group.restored.isEmpty() && r.group.decor.isEmpty()) l.groups.remove(r.group.id);
        l.setDirty();
    }

    /** Gone: air, fluid, or fire the blast left in the hole (it burns out, or the repair puts it out). */
    static boolean removed(BlockState s) {
        return s.isAir() || s.getBlock() instanceof LiquidBlock || s.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock;
    }

    /** Level.setBlock, before the change: record the position's state if a repairing blast is breaking blocks here. */
    public static void onSetBlock(Level level, BlockPos pos) {
        if (restoring || !(level instanceof ServerLevel sl)) return;
        Recording r = CURRENT.get();
        if (r != null && r.level == sl) {
            if (r.ledger != null) record(r, pos);
            return;
        }
        if (PLAYER.get()[0] > 0) {
            Ledger l = Ledger.of(sl);
            Ledger.Entry e = l.entries.get(pos.asLong());
            if (e != null && !e.touched) {
                e.touched = true;
                l.setDirty();
            }
        }
    }

    static void record(Recording r, BlockPos pos) {
        long p = pos.asLong();
        if (!r.seen.add(p)) return;
        Ledger l = r.ledger;
        Ledger.Entry e = l.entries.get(p);
        if (e != null && !e.touched) {
            // pending since an earlier blast of the chain: that blast's "before" stays, the entry joins this group
            if (e.group != r.group.id) {
                Ledger.Group old = l.groups.get(e.group);
                e.group = r.group.id;
                if (old != null && !old.started) l.merge(old, r.group);
            }
            return;
        }
        BlockState old = r.level.getBlockState(pos);
        CompoundTag be = null;
        if (old.hasBlockEntity()) {
            BlockEntity b = r.level.getBlockEntity(pos);
            if (b != null) be = b.saveWithFullMetadata(r.level.registryAccess());
        }
        l.entries.put(p, new Ledger.Entry(p, old, be, r.group.id));
        recordedTotal++;
        l.setDirty();
    }

    /** ServerLevel.addFreshEntity: cancels item and XP drops (and falling blocks) while recording; notes primed TNT. */
    public static boolean blocksSpawn(ServerLevel level, Entity entity) {
        Recording r = active(level);
        if (r == null) return false;
        if (entity instanceof PrimedTnt) {
            r.primed.add(BlockPos.containing(entity.position()).asLong());
            return false;
        }
        if (r.blockPass && (entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof FallingBlockEntity)) {
            suppressedDrops++;
            return true;
        }
        return false;
    }

    /**
     * Level.destroyBlock without an entity (a block breaking by itself: a cactus, sugar cane or scaffolding losing
     * its support) and FallingBlockEntity.fall (sand losing the block under it): when it happens beside a hole that
     * waits for repair, it joins that repair instead of dropping or falling. Returns the recording to close, or null.
     */
    public static Object beginAftershock(Level level, BlockPos pos) {
        if (restoring || !(level instanceof ServerLevel sl) || CURRENT.get() != null) return null;
        if (!Kind.anyEnabled()) return null;
        Ledger l = Ledger.of(sl);
        if (l.entries.isEmpty()) return null;
        Ledger.Entry beside = null;
        for (Direction d : Direction.values()) {
            Ledger.Entry e = l.entries.get(pos.relative(d).asLong());
            if (e != null && !e.touched && e.after != null && removed(e.after)) {
                beside = e;
                break;
            }
        }
        if (beside == null) return null;
        Ledger.Group g = l.groups.get(beside.group);
        if (g == null || g.started) return null;
        Recording r = new Recording(sl, l, g, null);
        r.blockPass = true;
        CURRENT.set(r);
        return r;
    }

    /**
     * FireBlock.tick: fire the blast started (or that spread from it) is recorded, so what it burns, and the fire
     * itself, go back with the repair. Only fire standing on a recorded position counts: a player's fireplace doesn't.
     */
    public static Object beginFire(Level level, BlockPos pos) {
        if (restoring || !(level instanceof ServerLevel sl) || CURRENT.get() != null || !Kind.anyEnabled()) return null;
        Ledger l = Ledger.of(sl);
        if (l.entries.isEmpty()) return null;
        Ledger.Entry e = l.entries.get(pos.asLong());
        if (e == null || e.touched) return null;
        // a group already rebuilding still takes what its fire burns: the rebuild makes another pass for it
        Ledger.Group g = l.groups.get(e.group);
        if (g == null || g.decorAt >= 0) return null;
        Recording r = new Recording(sl, l, g, null);
        r.blockPass = true;
        CURRENT.set(r);
        return r;
    }

    /** The wither's own block breaking (WitherBoss.customServerAiStep): one recording per breaking tick. */
    public static void witherBreak(ServerLevel level, Entity wither) {
        if (restoring || CURRENT.get() != null || !Kind.WITHER.enabled()) return;
        Ledger l = Ledger.of(level);
        if (l.pending() >= Config.get().maxPendingBlocks) return;
        Ledger.Group g = l.newGroup(Kind.WITHER, wither.getX(), wither.getY(), wither.getZ(), level.getGameTime() + Config.get().repairDelaySeconds * 20L);
        Recording r = new Recording(level, l, g, null);
        r.blockPass = true;
        r.wither = true;
        CURRENT.set(r);
    }

    public static void endWitherBreak(ServerLevel level) {
        Recording r = CURRENT.get();
        if (r != null && r.wither && r.level == level) finish(r);
    }

    public static void endAftershock(Object token) {
        if (token instanceof Recording r) finish(r);
    }

    public static void playerAction(boolean start) {
        PLAYER.get()[0] += start ? 1 : -1;
    }

    // ---------------------------------------------------------------- rebuilding

    /** Dev counters: the rebuild's own time per tick (max and total) and how many ticks it worked. */
    public static long tickNanosMax, tickNanosTotal, tickCount;

    /** ServerLevel.tick, end: rebuild due groups within the per-tick budget. */
    public static void tick(ServerLevel level) {
        long t0 = System.nanoTime();
        boolean worked = tickTimed(level);
        if (worked) {
            long dt = System.nanoTime() - t0;
            tickNanosMax = Math.max(tickNanosMax, dt);
            tickNanosTotal += dt;
            tickCount++;
        }
    }

    static boolean tickTimed(ServerLevel level) {
        if (configFor != level.getServer()) {
            // the server's own config file (config/havingablast.json in its directory), read once per server
            configFor = level.getServer();
            Config.load(level.getServer().getServerDirectory());
        }
        Ledger l = Ledger.of(level);
        if (l.groups.isEmpty()) return false;
        long now = level.getGameTime();
        int speed = Math.max(25, Math.min(400, Config.get().repairSpeed));
        int cap = Math.max(1, 64 * speed / 100);
        int budget = Math.max(256, cap * 4);
        for (Ledger.Group g : new ArrayList<>(l.groups.values())) {
            if (budget <= 0) break;
            if (g.decorAt >= 0) {
                if (g.decorAt <= now) {
                    Decor.restore(level, g);
                    l.groups.remove(g.id);
                    l.setDirty();
                }
                continue;
            }
            if (g.due > now) continue;
            // after a restart the order (not saved) is built again from what is left; restored positions are kept
            if (!g.started || g.order == null) start(level, l, g);
            int perTick = (int) Math.max(1, Math.min(cap, (g.order.length * (long) speed + 3999) / 4000));
            int done = 0;
            while (g.next < g.order.length && done < perTick && budget > 0) {
                long p = g.order[g.next];
                Ledger.Entry e = l.entries.get(p);
                if (e == null || e.group != g.id) {
                    g.next++;
                    continue;
                }
                if (!level.isLoaded(BlockPos.of(p))) break;
                rebuild(level, l, g, e);
                g.next++;
                done++;
                budget--;
            }
            if (g.next >= g.order.length) {
                boolean waiting = false;
                for (Ledger.Entry e : l.entries.values()) {
                    if (e.group == g.id) {
                        waiting = true;
                        break;
                    }
                }
                if (!waiting) complete(level, l, g);
                else {
                    // entries in chunks that weren't loaded: try again from the start next time
                    g.order = null;
                    g.started = false;
                    g.due = now + 20;
                }
            }
            l.setDirty();
        }
        return true;
    }

    /** Tier: full blocks first, then other solid blocks, then the ones that hang on a neighbour. */
    static int tier(ServerLevel level, BlockPos pos, BlockState s) {
        if (s.isAir()) return 3;
        VoxelShape shape = s.getCollisionShape(level, pos);
        if (Block.isShapeFullBlock(shape)) return 0;
        if (!shape.isEmpty() && !s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && !s.hasProperty(BlockStateProperties.BED_PART)
            && !s.hasProperty(BlockStateProperties.ATTACH_FACE) && !s.hasProperty(BlockStateProperties.HANGING)) return 1;
        return 2;
    }

    static void start(ServerLevel level, Ledger l, Ledger.Group g) {
        g.started = true;
        List<Ledger.Entry> es = l.of(g.id);
        es.sort(Comparator.<Ledger.Entry>comparingInt(e -> BlockPos.getY(e.pos))
            .thenComparingInt(e -> tier(level, BlockPos.of(e.pos), e.before))
            .thenComparingDouble(e -> -dist2(g, e.pos)));
        g.order = new long[es.size()];
        for (int i = 0; i < es.size(); i++) g.order[i] = es.get(i).pos;
        g.next = 0;
    }

    static double dist2(Ledger.Group g, long p) {
        double dx = BlockPos.getX(p) + 0.5 - g.cx, dz = BlockPos.getZ(p) + 0.5 - g.cz;
        return dx * dx + dz * dz;
    }

    /** May the recorded block go back? Not over a player's block; over air, water that ran in, snow or grass, yes. */
    static boolean restorable(ServerLevel level, Ledger.Entry e) {
        if (e.touched) return false;
        BlockState now = level.getBlockState(BlockPos.of(e.pos));
        if (now == e.after || now == e.before) return true;
        if (e.after != null && !removed(e.after)) return false;
        return now.isAir() || now.canBeReplaced() || now.getBlock() instanceof LiquidBlock;
    }

    /** The other half of a door, bed or tall plant, if the ledger holds it. */
    static Ledger.Entry partner(Ledger l, Ledger.Entry e) {
        BlockState s = e.before;
        BlockPos pos = BlockPos.of(e.pos), other = null;
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            other = s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        } else if (s.hasProperty(BlockStateProperties.BED_PART) && s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction f = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
            other = s.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT ? pos.relative(f) : pos.relative(f.getOpposite());
        }
        return other == null ? null : l.entries.get(other.asLong());
    }

    static void rebuild(ServerLevel level, Ledger l, Ledger.Group g, Ledger.Entry e) {
        BlockPos pos = BlockPos.of(e.pos);
        Ledger.Entry mate = partner(l, e);
        boolean ok = restorable(level, e) && (mate == null || restorable(level, mate));
        l.entries.remove(e.pos);
        if (!ok) {
            skip(level, e);
            if (mate != null && mate.group == g.id) {
                l.entries.remove(mate.pos);
                skip(level, mate);
            }
            return;
        }
        put(level, g, e);
        if (mate != null && mate.group == g.id) {
            l.entries.remove(mate.pos);
            put(level, g, mate);
        }
    }

    static void put(ServerLevel level, Ledger.Group g, Ledger.Entry e) {
        BlockPos pos = BlockPos.of(e.pos);
        nudge(level, pos, e.before);
        restoring = true;
        try {
            // no neighbour or shape updates until the whole group is in: nothing pops off or falls mid-rebuild
            level.setBlock(pos, e.before, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (e.be != null) {
                BlockEntity be = BlockEntity.loadStatic(pos, e.before, e.be, level.registryAccess());
                if (be != null) level.setBlockEntity(be);
            }
        } finally {
            restoring = false;
        }
        g.restored.add(e.pos);
        restoredTotal++;
    }

    /** A recorded block that can't go back drops what it would have dropped in the blast (its contents too). */
    static void skip(ServerLevel level, Ledger.Entry e) {
        skippedTotal++;
        if (e.after == null || !removed(e.after) || e.before.isAir()) return;
        BlockPos pos = BlockPos.of(e.pos);
        BlockEntity be = e.be == null ? null : BlockEntity.loadStatic(pos, e.before, e.be, level.registryAccess());
        if (be != null) {
            be.setLevel(level);
            if (be instanceof Container c) Containers.dropContents(level, pos, c);
        }
        Block.dropResources(e.before, level, pos, be);
    }

    /** Entities standing where a block comes back are lifted onto it, never left inside. */
    static void nudge(ServerLevel level, BlockPos pos, BlockState s) {
        VoxelShape shape = s.getCollisionShape(level, pos);
        if (shape.isEmpty()) return;
        AABB box = shape.bounds().move(pos);
        for (Entity en : level.getEntities((Entity) null, box, en -> !en.isSpectator() && en.isPickable() || en instanceof ItemEntity)) {
            en.teleportTo(en.getX(), box.maxY + 0.01, en.getZ());
        }
    }

    static void complete(ServerLevel level, Ledger l, Ledger.Group g) {
        for (int i = 0; i < g.restored.size(); i++) {
            BlockPos pos = BlockPos.of(g.restored.getLong(i));
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock(), null);
        }
        if (g.decor.isEmpty()) l.groups.remove(g.id);
        else g.decorAt = level.getGameTime() + Decor.AFTER_BLOCKS;
        l.setDirty();
    }

    /** Rebuild everything pending now (command). */
    public static int repairNow(ServerLevel level) {
        Ledger l = Ledger.of(level);
        int n = l.pending();
        for (Ledger.Group g : l.groups.values()) {
            g.due = Math.min(g.due, level.getGameTime());
            if (g.decorAt >= 0) g.decorAt = Math.min(g.decorAt, level.getGameTime());
        }
        l.setDirty();
        return n;
    }
}
