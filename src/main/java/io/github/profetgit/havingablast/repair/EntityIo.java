package io.github.profetgit.havingablast.repair;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import net.minecraft.world.entity.EntityType;

// Whole-entity save and load: the entity NBT API changed in 1.21.2 (spawn reasons), 1.21.6 (ValueIO) and 1.21.11.
public final class EntityIo {
    private EntityIo() {
    }

    //? if >=1.21.6 {
    public static CompoundTag save(ServerLevel level, Entity e) {
        net.minecraft.world.level.storage.TagValueOutput out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        e.saveWithoutId(out);
        return out.buildResult();
    }
    //?}
    //? if <1.21.6 {
    /*public static CompoundTag save(ServerLevel level, Entity e) {
        return e.saveWithoutId(new CompoundTag());
    }
    *///?}

    //? if >=1.21.11 {
    public static Entity load(ServerLevel level, CompoundTag t) {
        return EntityType.loadEntityRecursive(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), t), level,
            net.minecraft.world.entity.EntitySpawnReason.LOAD, net.minecraft.world.entity.EntityProcessor.NOP);
    }
    //?}
    //? if >=1.21.6 <1.21.11 {
    /*public static Entity load(ServerLevel level, CompoundTag t) {
        return EntityType.loadEntityRecursive(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), t), level,
            net.minecraft.world.entity.EntitySpawnReason.LOAD, java.util.function.Function.identity());
    }
    *///?}
    //? if >=1.21.2 <1.21.6 {
    /*public static Entity load(ServerLevel level, CompoundTag t) {
        return EntityType.loadEntityRecursive(t, level, net.minecraft.world.entity.EntitySpawnReason.LOAD, java.util.function.Function.identity());
    }
    *///?}
    //? if <1.21.2 {
    /*public static Entity load(ServerLevel level, CompoundTag t) {
        return EntityType.loadEntityRecursive(t, level, java.util.function.Function.identity());
    }
    *///?}
}
