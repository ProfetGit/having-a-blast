package io.github.profetgit.havingablast.mixin.client;

import io.github.profetgit.havingablast.client.Blasts;
import io.github.profetgit.havingablast.client.Probe;
import io.github.profetgit.havingablast.client.Rebuild;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    /** Every server block update passes here before it is applied (single updates and section batches alike). */
    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"), cancellable = true)
    private void havingablast$blockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        ClientLevel level = (ClientLevel) (Object) this;
        BlockState old = level.getBlockState(pos);
        if (Probe.ON) Probe.block(pos, old, state);
        // a block the server puts back where the client saw it blow up: held while it flies back in
        if (Rebuild.intercept(level, pos, old, state, flags)) {
            ci.cancel();
            return;
        }
        Blasts.onBlockUpdate(level, pos, old, state);
    }
}
