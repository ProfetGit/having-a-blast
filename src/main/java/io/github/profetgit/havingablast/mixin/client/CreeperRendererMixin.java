package io.github.profetgit.havingablast.mixin.client;

//? if >=1.21.2 {
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
//?}
//? if <1.21.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.havingablast.client.Anticipation;
import io.github.profetgit.havingablast.client.BlastClient;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The creeper's swell, pushed further (Anticipation.creeper).
@Mixin(CreeperRenderer.class)
public abstract class CreeperRendererMixin {
    @Inject(method = "scale(Lnet/minecraft/world/entity/monster/Creeper;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"), cancellable = true)
    private void havingablast$swell(Creeper creeper, PoseStack ps, float partial, CallbackInfo ci) {
        if (!BlastClient.visuals()) return;
        Anticipation.creeper(creeper.getSwelling(partial), creeper.tickCount + partial, ps);
        ci.cancel();
    }
}
*///?}
