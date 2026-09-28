package io.github.profetgit.havingablast.mixin.client;

import io.github.profetgit.havingablast.client.BlastClient;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The client counts the fuse down itself and removed the TNT up to two ticks before the server's explosion arrived,
 * leaving dead frames before the bang. With the visuals on, the client keeps it (fully swollen) until the server
 * removes it, with a timeout in case it never does.
 */
@Mixin(PrimedTnt.class)
public abstract class PrimedTntMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/PrimedTnt;discard()V"))
    private void havingablast$holdUntilServer(PrimedTnt tnt) {
        if (tnt.level().isClientSide() && BlastClient.visuals() && tnt.getFuse() > -20) return;
        tnt.discard();
    }
}
