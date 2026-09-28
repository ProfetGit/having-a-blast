package io.github.profetgit.havingablast.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.havingablast.client.Anticipation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Primed TNT pumps (squash and stretch, faster and bigger) through its last ticks instead of vanilla's even swell. */
@Mixin(TntRenderer.class)
public abstract class TntRendererMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/TntRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
    private void havingablast$fuse(TntRenderState state, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam, CallbackInfo ci) {
        Anticipation.fuse = state.fuseRemainingInTicks;
    }

    @Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/TntRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void havingablast$pump(PoseStack ps, float x, float y, float z) {
        Anticipation.tnt(ps, x, y, z);
    }
}
