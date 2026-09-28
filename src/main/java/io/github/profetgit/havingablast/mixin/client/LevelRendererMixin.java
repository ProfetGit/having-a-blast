package io.github.profetgit.havingablast.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.havingablast.client.DebrisRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    /** Debris and puffs are submitted with the entities, so they share the entity pass (and its shader-pack program). */
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void havingablast$submitDebris(PoseStack ps, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        DebrisRenderer.submit(ps, state, collector);
    }
}
