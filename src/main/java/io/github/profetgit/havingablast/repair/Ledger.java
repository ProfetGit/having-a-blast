package io.github.profetgit.havingablast.repair;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.5 {
import net.minecraft.world.level.saveddata.SavedDataType;
//?}

/**
 * Pending repairs of one dimension, saved with the world (data/havingablast/repairs_<dimension>.dat), so a restart,
 * an unloaded chunk or leaving the area loses nothing. One entry per position (the state before the first blast of a
 * chain wins); entries belong to groups, one per blast or merged chain, that rebuild together.
 */
public final class Ledger extends SavedData {
    /** One recorded position. */
    public static final class Entry {
        public final long pos;
        public BlockState before, after;
        /** Block entity data, only when the blast removed the block (a survivor keeps its own, live contents). */
        public CompoundTag be;
        public int group;
        /** A player placed or changed something here since: never overwrite it. */
        public boolean touched;

        Entry(long pos, BlockState before, CompoundTag be, int group) {
            this.pos = pos;
            this.before = before;
            this.be = be;
            this.group = group;
        }
    }

    /** A blast (or chain) rebuilding together. */
    public static final class Group {
        public final int id;
        public long due;
        public Kind kind;
        public double cx, cy, cz;
        public int minX, minY, minZ, maxX, maxY, maxZ;
        /** Restored positions, updated as a whole when the group is done. */
        public final it.unimi.dsi.fastutil.longs.LongArrayList restored = new it.unimi.dsi.fastutil.longs.LongArrayList();
        /** Positions in rebuild order (bottom row first, outer rim inward); built when the rebuild starts. */
        public long[] order;
        public int next;
        public boolean started;
        /** Item frames, paintings and armor stands the blast broke (full entity data), put back after the blocks. */
        public final List<CompoundTag> decor = new ArrayList<>();
        /** Game time the decorations go back (the blocks are in and their animation has landed); -1 until then. */
        public long decorAt = -1;

        Group(int id) {
            this.id = id;
        }

        boolean overlaps(Group o) {
            return minX <= o.maxX + 1 && maxX + 1 >= o.minX && minY <= o.maxY + 1 && maxY + 1 >= o.minY && minZ <= o.maxZ + 1 && maxZ + 1 >= o.minZ;
        }

        void include(long p) {
            int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
            if (minX > maxX) {
                minX = maxX = x;
                minY = maxY = y;
                minZ = maxZ = z;
                return;
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
    }

    public final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();
    public final Int2ObjectOpenHashMap<Group> groups = new Int2ObjectOpenHashMap<>();
    int nextGroup = 1;

    //? if >=1.21.5 {
    static final java.util.Map<String, SavedDataType<Ledger>> TYPES = new java.util.concurrent.ConcurrentHashMap<>();
    //?}

    //? if >=26.2 {
    // One type per dimension, cached: the storage keys by the type record, and a fresh Ledger::new never equals the last.
    public static SavedDataType<Ledger> type(ServerLevel level) {
        String dim = level.dimension().identifier().toString().replace(':', '_').replace('/', '_');
        return TYPES.computeIfAbsent(dim, d -> new SavedDataType<>(Identifier.fromNamespaceAndPath("havingablast", "repairs_" + d), Ledger::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE));
    }

    public static Ledger of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(type(level));
    }
    //?}
    //? if >=1.21.5 <26.2 {
    /*// One type per dimension, cached (its id is a string before 26.2).
    public static SavedDataType<Ledger> type(ServerLevel level) {
        String dim = level.dimension().identifier().toString().replace(':', '_').replace('/', '_');
        return TYPES.computeIfAbsent(dim, d -> new SavedDataType<>("havingablast_repairs_" + d, Ledger::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE));
    }

    public static Ledger of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(type(level));
    }
    *///?}
    //? if <1.21.5 {
    /*// before 1.21.5 the data is NBT: a factory and a file name per dimension
    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag saved = save();
        for (String k : saved.getAllKeys()) tag.put(k, saved.get(k));
        return tag;
    }

    public static Ledger of(ServerLevel level) {
        String dim = level.dimension().identifier().toString().replace(':', '_').replace('/', '_');
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Ledger::new, (tag, registries) -> load(tag), DataFixTypes.SAVED_DATA_COMMAND_STORAGE), "havingablast_repairs_" + dim);
    }
    *///?}

    Group newGroup(Kind kind, double cx, double cy, double cz, long due) {
        Group g = new Group(nextGroup++);
        g.kind = kind;
        g.cx = cx;
        g.cy = cy;
        g.cz = cz;
        g.due = due;
        g.minX = Integer.MAX_VALUE;
        g.maxX = Integer.MIN_VALUE;
        groups.put(g.id, g);
        setDirty();
        return g;
    }

    /** Moves every entry of `from` into `into` (a new blast over a pending one rebuilds them together, later). */
    void merge(Group from, Group into) {
        if (from == into) return;
        for (Entry e : entries.values()) {
            if (e.group == from.id) {
                e.group = into.id;
                into.include(e.pos);
            }
        }
        into.due = Math.max(into.due, from.due);
        into.decor.addAll(from.decor);
        groups.remove(from.id);
        setDirty();
    }

    public int pending() {
        return entries.size();
    }

    // ---- saving

    //? if >=1.21.5 {
    public static final Codec<Ledger> CODEC = CompoundTag.CODEC.xmap(Ledger::load, Ledger::save);
    //?}

    CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag gl = new ListTag();
        for (Group g : groups.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", g.id);
            t.putLong("due", g.due);
            t.putString("kind", g.kind.name());
            t.putDouble("cx", g.cx);
            t.putDouble("cy", g.cy);
            t.putDouble("cz", g.cz);
            t.putIntArray("box", new int[] {g.minX, g.minY, g.minZ, g.maxX, g.maxY, g.maxZ});
            t.putLongArray("restored", g.restored.toLongArray());
            t.putBoolean("started", g.started);
            if (!g.decor.isEmpty()) {
                ListTag dl = new ListTag();
                dl.addAll(g.decor);
                t.put("decor", dl);
            }
            if (g.decorAt >= 0) t.putLong("decorAt", g.decorAt);
            gl.add(t);
        }
        root.put("groups", gl);
        ListTag el = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", e.pos);
            t.put("before", NbtUtils.writeBlockState(e.before));
            if (e.after != null) t.put("after", NbtUtils.writeBlockState(e.after));
            if (e.be != null) t.put("be", e.be);
            t.putInt("group", e.group);
            if (e.touched) t.putBoolean("touched", true);
            el.add(t);
        }
        root.put("entries", el);
        root.putInt("nextGroup", nextGroup);
        return root;
    }

    static Ledger load(CompoundTag root) {
        Ledger l = new Ledger();
        var blocks = BuiltInRegistries.BLOCK;
        l.nextGroup = Nbt.intOr(root, "nextGroup", 1);
        for (Tag tag : Nbt.list(root, "groups")) {
            CompoundTag t = (CompoundTag) tag;
            Group g = new Group(Nbt.intOr(t, "id", 0));
            g.due = Nbt.longOr(t, "due", 0);
            try {
                g.kind = Kind.valueOf(Nbt.stringOr(t, "kind", "TNT"));
            } catch (IllegalArgumentException e) {
                g.kind = Kind.TNT;
            }
            g.cx = Nbt.doubleOr(t, "cx", 0);
            g.cy = Nbt.doubleOr(t, "cy", 0);
            g.cz = Nbt.doubleOr(t, "cz", 0);
            int[] box = Nbt.intArray(t, "box", new int[6]);
            if (box.length == 6) {
                g.minX = box[0];
                g.minY = box[1];
                g.minZ = box[2];
                g.maxX = box[3];
                g.maxY = box[4];
                g.maxZ = box[5];
            }
            for (long p : Nbt.longArray(t, "restored")) g.restored.add(p);
            g.started = Nbt.boolOr(t, "started", false);
            for (Tag d : Nbt.list(t, "decor")) if (d instanceof CompoundTag c) g.decor.add(c);
            g.decorAt = Nbt.longOr(t, "decorAt", -1);
            l.groups.put(g.id, g);
        }
        for (Tag tag : Nbt.list(root, "entries")) {
            CompoundTag t = (CompoundTag) tag;
            Entry e = new Entry(Nbt.longOr(t, "pos", 0), NbtUtils.readBlockState(blocks, Nbt.compoundOrEmpty(t, "before")),
                Nbt.compound(t, "be"), Nbt.intOr(t, "group", 0));
            CompoundTag after = Nbt.compound(t, "after");
            e.after = after == null ? null : NbtUtils.readBlockState(blocks, after);
            e.touched = Nbt.boolOr(t, "touched", false);
            if (l.groups.containsKey(e.group)) l.entries.put(e.pos, e);
        }
        return l;
    }

    /** Entries of a group, for tests and the status command. */
    public List<Entry> of(int group) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) if (e.group == group) out.add(e);
        return out;
    }
}
