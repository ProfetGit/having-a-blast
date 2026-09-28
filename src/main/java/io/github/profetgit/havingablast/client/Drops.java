package io.github.profetgit.havingablast.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

/**
 * The server's item drops of a blast reach the client just before its explode packet (same tick, probed on 26.3), as
 * merged stacks lying in the crater. They stay hidden until the nearest debris pops, so each drop appears with a pop.
 */
public final class Drops {
    /** Items added in the last tick, waiting to see whether an explosion claims them. */
    static final List<long[]> recent = new ArrayList<>();
    static final Int2ObjectOpenHashMap<Blast> HIDDEN = new Int2ObjectOpenHashMap<>();
    public static int hiddenTotal, revealedTotal;

    private Drops() {
    }

    public static boolean hidden(Entity e) {
        return !HIDDEN.isEmpty() && e.getType() == EntityTypes.ITEM && HIDDEN.containsKey(e.getId());
    }

    /** Blocks that just started falling (sand, gravel...): their block turning to air is a fall, not a blast. */
    static final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap FALLING = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

    static boolean justFell(net.minecraft.core.BlockPos pos) {
        return !FALLING.isEmpty() && Blasts.ticks - FALLING.getOrDefault(pos.asLong(), -100) <= 2;
    }

    public static void onAdd(int id, EntityType<?> type, double x, double y, double z) {
        if (type == EntityTypes.FALLING_BLOCK) {
            FALLING.put(net.minecraft.core.BlockPos.containing(x, y, z).asLong(), Blasts.ticks);
            return;
        }
        if (type != EntityTypes.ITEM || !BlastClient.visuals()) return;
        for (Blast b : Blasts.blasts) {
            if (!b.closed && b.center.distanceToSqr(x, y, z) <= b.reach * b.reach) {
                hide(id, b);
                return;
            }
        }
        recent.add(new long[] {id, Blasts.ticks, Double.doubleToRawLongBits(x), Double.doubleToRawLongBits(y), Double.doubleToRawLongBits(z)});
    }

    /** A new blast claims the drops that arrived just before its packet. */
    static void claim(Blast b) {
        for (long[] r : recent) {
            if (Blasts.ticks - r[1] > 1) continue;
            if (b.center.distanceToSqr(Double.longBitsToDouble(r[2]), Double.longBitsToDouble(r[3]), Double.longBitsToDouble(r[4])) <= b.reach * b.reach) hide((int) r[0], b);
        }
    }

    static void hide(int id, Blast b) {
        if (HIDDEN.put(id, b) == null) {
            b.items.add(id);
            hiddenTotal++;
        }
    }

    /** At the end of the capture window: each drop waits for the debris that comes to rest nearest to it. */
    static void assign(ClientLevel level, Blast b) {
        double[] p = new double[3];
        for (int i = 0; i < b.items.size(); i++) {
            int id = b.items.getInt(i);
            Entity e = level.getEntity(id);
            Debris best = null;
            double bd = Double.MAX_VALUE;
            if (e != null) {
                for (Debris d : b.debris) {
                    if (d.nseg == 0) continue;
                    d.position(d.restT, p);
                    double dd = e.distanceToSqr(p[0], p[1], p[2]);
                    if (dd < bd) {
                        bd = dd;
                        best = d;
                    }
                }
            }
            if (best == null) reveal(id);
            else best.items.add(id);
        }
    }

    static void reveal(int id) {
        if (HIDDEN.remove(id) != null) revealedTotal++;
    }

    static void revealAll(IntArrayList ids) {
        for (int i = 0; i < ids.size(); i++) reveal(ids.getInt(i));
    }

    static void tick() {
        recent.removeIf(r -> Blasts.ticks - r[1] > 1);
        if (!FALLING.isEmpty()) FALLING.values().removeIf(t -> Blasts.ticks - t > 2);
    }

    static void clear() {
        recent.clear();
        FALLING.clear();
        HIDDEN.clear();
    }
}
