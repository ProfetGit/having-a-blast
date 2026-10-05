package io.github.profetgit.havingablast.mixin.common;

//? if >=1.21.2 {
import io.github.profetgit.havingablast.repair.Repair;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records a repairing blast as a whole: the entity pass (frames, paintings, stands), the block pass and its fire. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
    @Inject(method = "explode", at = @At("HEAD"))
    private void havingablast$begin(CallbackInfoReturnable<Integer> cir) {
        ServerExplosion ex = (ServerExplosion) (Object) this;
        Repair.beginExplosion(ex.level(), ex.getDirectSourceEntity(), ex.center());
    }

    @Inject(method = "explode", at = @At("RETURN"))
    private void havingablast$end(CallbackInfoReturnable<Integer> cir) {
        Repair.endExplosion(((ServerExplosion) (Object) this).level());
    }

    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void havingablast$blocks(List<BlockPos> positions, CallbackInfo ci) {
        Repair.blockPass(((ServerExplosion) (Object) this).level(), true);
        io.github.profetgit.havingablast.dev.BlastCount.begin(((ServerExplosion) (Object) this).level(), ((ServerExplosion) (Object) this).center());
    }

    @Inject(method = "interactWithBlocks", at = @At("RETURN"))
    private void havingablast$blocksEnd(List<BlockPos> positions, CallbackInfo ci) {
        Repair.blockPass(((ServerExplosion) (Object) this).level(), false);
        io.github.profetgit.havingablast.dev.BlastCount.end();
    }
}
//?}
//? if <1.21.2 {
/*import io.github.profetgit.havingablast.repair.Repair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Records a repairing blast as a whole: the entity pass (frames, paintings, stands), the block pass and its fire.
// Before 1.21.2 the server runs explode() (entities) and finalizeExplosion() (blocks and fire) back to back.
@Mixin(Explosion.class)
public abstract class ServerExplosionMixin {
    @Shadow
    @org.spongepowered.asm.mixin.Final
    private Level level;

    @Inject(method = "explode", at = @At("HEAD"))
    private void havingablast$begin(CallbackInfo ci) {
        Explosion ex = (Explosion) (Object) this;
        if (level instanceof ServerLevel sl) Repair.beginExplosion(sl, ex.getDirectSourceEntity(), ex.center());
    }

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void havingablast$blocks(boolean particles, CallbackInfo ci) {
        if (!(level instanceof ServerLevel sl)) return;
        Repair.blockPass(level, true);
        io.github.profetgit.havingablast.dev.BlastCount.begin(sl, ((Explosion) (Object) this).center());
    }

    @Inject(method = "finalizeExplosion", at = @At("RETURN"))
    private void havingablast$end(boolean particles, CallbackInfo ci) {
        if (!(level instanceof ServerLevel)) return;
        Repair.blockPass(level, false);
        io.github.profetgit.havingablast.dev.BlastCount.end();
        Repair.endExplosion(level);
    }
}
*///?}
