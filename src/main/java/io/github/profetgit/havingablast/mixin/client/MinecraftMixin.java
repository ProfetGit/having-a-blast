package io.github.profetgit.havingablast.mixin.client;

import io.github.profetgit.havingablast.client.BlastClient;
import io.github.profetgit.havingablast.client.Blasts;
import io.github.profetgit.havingablast.demo.Director;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void havingablast$boot(CallbackInfo ci) {
        BlastClient.init();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void havingablast$tick(CallbackInfo ci) {
        Blasts.tick((Minecraft) (Object) this);
    }

    /** Drives the dev demo (dev/demo/run.sh); inert unless the game runs with -Dhavingablast.demo. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void havingablast$demoTick(CallbackInfo ci) {
        if (Director.ACTIVE) Director.onTick((Minecraft) (Object) this);
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void havingablast$demoFrame(boolean advanceGameTime, CallbackInfo ci) {
        if (Director.ACTIVE) Director.onFrame((Minecraft) (Object) this);
    }
}
