package io.github.profetgit.havingablast.repair;

import io.github.profetgit.havingablast.mixin.common.ArmorStandInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Item frames (with their item, rotation and map), paintings and armor stands (with their equipment) a repairing blast
 * breaks are kept whole in the blast's group instead of dropping, and go back once the group's blocks are in and their
 * animation has landed. Also the ones the blast only leaves without a wall: they're taken at once (vanilla would let
 * them hang in the air for up to 5 s, then drop). One that can't go back (a player built over its wall) drops what it
 * would have dropped in the blast. A frame or stand a player breaks is never taken.
 */
public final class Decor {
    /** Ticks after the group's last block: the client's rebuild animation (up to 20 ticks) has landed by then. */
    static final long AFTER_BLOCKS = 25;
    /** Set around BlockAttachedEntity.tick's drop: a frame that lost its wall since the blast (an aftershock). */
    public static final ThreadLocal<int[]> TICK = ThreadLocal.withInitial(() -> new int[1]);
    /** Counters for the tests. */
    public static int captured, restored, dropped;

    private Decor() {
    }

    /** ItemFrame and Painting.dropItem, head: returns true to cancel the drop (the entity is kept for the repair). */
    public static boolean onDrop(Entity e) {
        if (Repair.restoring || !(e.level() instanceof ServerLevel level)) return false;
        Repair.Recording r = Repair.active(level);
        if (r != null) return keep(level, r.ledger, r.group, e);
        if (TICK.get()[0] <= 0 || !Kind.anyEnabled()) return false;
        Ledger l = Ledger.of(level);
        Ledger.Group g = groupNear(l, e.getBoundingBox());
        return g != null && keep(level, l, g, e);
    }

    /** ArmorStand.hurtServer, head: a repairing blast keeps the stand instead of breaking it. */
    public static boolean onStandBlast(ArmorStand stand, ServerLevel level, DamageSource source) {
        Repair.Recording r = Repair.active(level);
        if (r == null || stand.isRemoved() || stand.isMarker() || stand.isInvulnerableTo(level, source)) return false;
        if (source.getEntity() instanceof Mob && !level.getGameRules().get(GameRules.MOB_GRIEFING)) return false;
        return keep(level, r.ledger, r.group, stand);
    }

    /** End of a recording: frames and paintings whose wall the blast took go now, not 5 s later. */
    static void sweep(Repair.Recording r) {
        if (r.ledger == null || r.seen.isEmpty()) return;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (long p : r.seen) {
            x0 = Math.min(x0, BlockPos.getX(p));
            y0 = Math.min(y0, BlockPos.getY(p));
            z0 = Math.min(z0, BlockPos.getZ(p));
            x1 = Math.max(x1, BlockPos.getX(p));
            y1 = Math.max(y1, BlockPos.getY(p));
            z1 = Math.max(z1, BlockPos.getZ(p));
        }
        AABB box = new AABB(x0 - 1, y0 - 1, z0 - 1, x1 + 2, y1 + 2, z1 + 2);
        for (HangingEntity h : r.level.getEntitiesOfClass(HangingEntity.class, box, h -> !h.isRemoved())) {
            if (!h.survives()) keep(r.level, r.ledger, r.group, h);
        }
    }

    /** A group waiting for repair with a removed block touching this box. */
    static Ledger.Group groupNear(Ledger l, AABB box) {
        if (l.entries.isEmpty()) return null;
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX - 0.5, box.minY - 0.5, box.minZ - 0.5),
            BlockPos.containing(box.maxX + 0.5, box.maxY + 0.5, box.maxZ + 0.5))) {
            Ledger.Entry e = l.entries.get(p.asLong());
            if (e == null || e.touched || e.after == null || !Repair.removed(e.after)) continue;
            Ledger.Group g = l.groups.get(e.group);
            if (g != null && !g.started) return g;
        }
        return null;
    }

    static boolean keep(ServerLevel level, Ledger l, Ledger.Group g, Entity e) {
        CompoundTag t;
        try {
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            e.saveWithoutId(out);
            t = out.buildResult();
            t.putString("id", EntityType.getKey(e.getType()).toString());
        } catch (RuntimeException ex) {
            return false;
        }
        g.decor.add(t);
        l.setDirty();
        captured++;
        if (!e.isRemoved()) e.discard();
        return true;
    }

    /** A player built where the stand stood: a solid block in its feet cell (the blocks around it are as before the blast). */
    static boolean blocked(ServerLevel level, ArmorStand a) {
        BlockPos feet = a.blockPosition();
        VoxelShape shape = level.getBlockState(feet).getCollisionShape(level, feet);
        return !shape.isEmpty() && shape.bounds().move(feet).intersects(a.getBoundingBox().deflate(1.0E-4));
    }

    /** Puts a group's decorations back (the blocks are in): each on its wall or spot, or its drops if it can't. */
    static void restore(ServerLevel level, Ledger.Group g) {
        for (CompoundTag t : g.decor) {
            Entity e;
            try {
                e = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), t), level, EntitySpawnReason.LOAD,
                    EntityProcessor.NOP);
            } catch (RuntimeException ex) {
                e = null;
            }
            if (e == null || level.getEntity(e.getUUID()) != null) continue;
            e.setDeltaMovement(Vec3.ZERO);
            if (e instanceof HangingEntity h) {
                if (!h.survives()) {
                    h.dropItem(level, null);
                    dropped++;
                    continue;
                }
                level.addFreshEntity(h);
                h.playPlacementSound();
            } else if (e instanceof ArmorStand a) {
                if (blocked(level, a)) {
                    ((ArmorStandInvoker) a).havingablast$brokenByAnything(level, level.damageSources().generic());
                    dropped++;
                    continue;
                }
                level.addFreshEntity(a);
                level.playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.75f, 0.8f);
            } else {
                level.addFreshEntity(e);
            }
            restored++;
        }
        g.decor.clear();
    }
}
