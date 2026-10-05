package io.github.profetgit.havingablast.repair;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

// NBT reads with defaults: 1.21.5 made the getters return Optionals (or take a default), before that they returned the type's zero.
public final class Nbt {
    private Nbt() {
    }

    //? if >=1.21.5 {
    public static int intOr(CompoundTag t, String k, int d) {
        return t.getIntOr(k, d);
    }

    public static long longOr(CompoundTag t, String k, long d) {
        return t.getLongOr(k, d);
    }

    public static double doubleOr(CompoundTag t, String k, double d) {
        return t.getDoubleOr(k, d);
    }

    public static String stringOr(CompoundTag t, String k, String d) {
        return t.getStringOr(k, d);
    }

    public static boolean boolOr(CompoundTag t, String k, boolean d) {
        return t.getBooleanOr(k, d);
    }

    public static int[] intArray(CompoundTag t, String k, int[] d) {
        return t.getIntArray(k).orElse(d);
    }

    public static long[] longArray(CompoundTag t, String k) {
        return t.getLongArray(k).orElse(new long[0]);
    }

    public static ListTag list(CompoundTag t, String k) {
        return t.getListOrEmpty(k);
    }

    public static CompoundTag compound(CompoundTag t, String k) {
        return t.getCompound(k).orElse(null);
    }

    public static CompoundTag compoundOrEmpty(CompoundTag t, String k) {
        return t.getCompoundOrEmpty(k);
    }
    //?}
    //? if <1.21.5 {
    /*public static int intOr(CompoundTag t, String k, int d) {
        return t.contains(k) ? t.getInt(k) : d;
    }

    public static long longOr(CompoundTag t, String k, long d) {
        return t.contains(k) ? t.getLong(k) : d;
    }

    public static double doubleOr(CompoundTag t, String k, double d) {
        return t.contains(k) ? t.getDouble(k) : d;
    }

    public static String stringOr(CompoundTag t, String k, String d) {
        return t.contains(k) ? t.getString(k) : d;
    }

    public static boolean boolOr(CompoundTag t, String k, boolean d) {
        return t.contains(k) ? t.getBoolean(k) : d;
    }

    public static int[] intArray(CompoundTag t, String k, int[] d) {
        return t.contains(k) ? t.getIntArray(k) : d;
    }

    public static long[] longArray(CompoundTag t, String k) {
        return t.getLongArray(k);
    }

    public static ListTag list(CompoundTag t, String k) {
        return t.getList(k, 10);
    }

    public static CompoundTag compound(CompoundTag t, String k) {
        return t.contains(k) ? t.getCompound(k) : null;
    }

    public static CompoundTag compoundOrEmpty(CompoundTag t, String k) {
        return t.getCompound(k);
    }
    *///?}
}
