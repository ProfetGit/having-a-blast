package io.github.profetgit.havingablast.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.havingablast.client.Anticipation;
import io.github.profetgit.havingablast.client.BlastClient;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The creeper's swell, pushed further (Anticipation.creeper). */
@Mixin(CreeperRenderer.class)
public abstract class CreeperRendererMixin {
    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/CreeperRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"), cancellable = true)
    private void havingablast$swell(CreeperRenderState state, PoseStack ps, CallbackInfo ci) {
        if (!BlastClient.visuals()) return;
        Anticipation.creeper(state.swelling, state.ageInTicks, ps);
        ci.cancel();
    }
}
