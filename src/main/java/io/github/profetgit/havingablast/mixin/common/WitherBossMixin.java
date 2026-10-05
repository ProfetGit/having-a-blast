package io.github.profetgit.havingablast.mixin.common;

//? if >=1.21.2 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.profetgit.havingablast.repair.Repair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The blocks a hurt wither breaks around itself are recorded for the wither repair, one group per breaking tick.
@Mixin(WitherBoss.class)
public abstract class WitherBossMixin {
    @WrapOperation(method = "customServerAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean havingablast$break(ServerLevel level, BlockPos pos, boolean drop, Entity by, Operation<Boolean> original) {
        Repair.witherBreak(level, (Entity) (Object) this);
        return original.call(level, pos, drop, by);
    }

    @Inject(method = "customServerAiStep", at = @At("RETURN"))
    private void havingablast$breakEnd(ServerLevel level, CallbackInfo ci) {
        Repair.endWitherBreak(level);
    }
}
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.profetgit.havingablast.repair.Repair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The blocks a hurt wither breaks around itself are recorded for the wither repair, one group per breaking tick.
@Mixin(WitherBoss.class)
public abstract class WitherBossMixin {
    @WrapOperation(method = "customServerAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean havingablast$break(Level level, BlockPos pos, boolean drop, Entity by, Operation<Boolean> original) {
        if (level instanceof ServerLevel sl) Repair.witherBreak(sl, (Entity) (Object) this);
        return original.call(level, pos, drop, by);
    }

    @Inject(method = "customServerAiStep", at = @At("RETURN"))
    private void havingablast$breakEnd(CallbackInfo ci) {
        if (((Entity) (Object) this).level() instanceof ServerLevel sl) Repair.endWitherBreak(sl);
    }
}
*///?}
