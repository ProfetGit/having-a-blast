package io.github.profetgit.havingablast.mixin.client;

//? if >=26.2 {
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
//?}
//? if >=1.21.9 <26.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.havingablast.client.DebrisRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    // Debris and puffs are submitted with the entities, so they share the entity pass (and its shader-pack program).
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void havingablast$submitDebris(PoseStack ps, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        DebrisRenderer.submit(ps, state, collector);
    }

    // The culling frustum only lives in the arguments of the entity extraction before 26.2.
    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void havingablast$frustum(Camera camera, Frustum frustum, DeltaTracker delta, LevelRenderState state, CallbackInfo ci) {
        DebrisRenderer.FRUSTUM = frustum;
    }
}
*///?}
//? if >=1.21.2 <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.havingablast.client.DebrisRenderer;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    private Frustum cullingFrustum;

    // Debris and puffs are drawn with the entities, before the entity buffers are flushed.
    @Inject(method = "renderEntities", at = @At("TAIL"))
    private void havingablast$submitDebris(PoseStack ps, MultiBufferSource.BufferSource buffer, Camera camera, DeltaTracker delta, List<Entity> entities, CallbackInfo ci) {
        DebrisRenderer.FRUSTUM = cullingFrustum;
        DebrisRenderer.submit(ps, camera.getPosition(), camera.rotation(), buffer);
    }
}
*///?}
//? if <1.21.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.havingablast.client.DebrisRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    private Frustum cullingFrustum;

    // Debris and puffs are drawn right after the entity loop, before the entity buffers are flushed.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V", ordinal = 0))
    private void havingablast$submitDebris(DeltaTracker delta, boolean b, Camera camera, GameRenderer gr, LightTexture lt, Matrix4f m1, Matrix4f m2, CallbackInfo ci) {
        DebrisRenderer.FRUSTUM = cullingFrustum;
        DebrisRenderer.submit(new PoseStack(), camera.getPosition(), camera.rotation(), Minecraft.getInstance().renderBuffers().bufferSource());
    }
}
*///?}
