package io.github.profetgit.havingablast.mixin.client;

import io.github.profetgit.havingablast.client.Blasts;
import io.github.profetgit.havingablast.client.Drops;
import io.github.profetgit.havingablast.client.Rebuild;
import io.github.profetgit.havingablast.client.Probe;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
//? if >=1.21.9 {
import net.minecraft.core.particles.ExplosionParticleInfo;
//?}
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
//? if >=1.21.9 {
import net.minecraft.util.random.WeightedList;
//?}
import net.minecraft.world.phys.Vec3;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    private static final String ENSURE = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

    @Inject(method = "handleExplosion", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER))
    private void havingablast$explosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        //? if >=1.21.9 {
        if (Probe.ON) Probe.explosion(packet.center(), packet.radius(), packet.blockCount());
        Blasts.onExplosionPacket(packet.center(), packet.radius(), packet.blockCount(), packet.explosionParticle());
        //?}
        //? if >=1.21.2 <1.21.9 {
        /*// the packet carries no radius before 1.21.9: the big explosion particle stands for a TNT-sized blast
        boolean big = packet.explosionParticle().getType() == net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER;
        if (Probe.ON) Probe.explosion(packet.center(), big ? 4f : 1f, 0);
        Blasts.onExplosionPacket(packet.center(), big ? 4f : 1f, 0, packet.explosionParticle());
        *///?}
        //? if <1.21.2 {
        /*// before 1.21.2 the packet lists the blocks and the client removes them itself, so they are taken from the list
        // before vanilla does (no block update follows for them on the client's side of the removal)
        boolean interacts = packet.getBlockInteraction() != net.minecraft.world.level.Explosion.BlockInteraction.KEEP;
        net.minecraft.core.particles.ParticleOptions particle = packet.getPower() >= 2f && interacts ? packet.getLargeExplosionParticles() : packet.getSmallExplosionParticles();
        Vec3 center = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        if (Probe.ON) Probe.explosion(center, packet.getPower(), packet.getToBlow().size());
        Blasts.onExplosionPacket(center, packet.getPower(), packet.getToBlow().size(), particle);
        ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null && interacts) {
            for (net.minecraft.core.BlockPos pos : packet.getToBlow()) {
                Blasts.onBlockUpdate(level, pos, level.getBlockState(pos), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        *///?}
    }

    //? if >=1.21.2 {
    // The cartoon puff replaces vanilla's explosion sprite and the block particles sprayed in the sphere. Wrapped, not
    // redirected: Explosive Enhancement wraps the same two calls, and with it installed its puff is the one drawn.
    @WrapOperation(method = "handleExplosion", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void havingablast$explosionParticle(ClientLevel level, ParticleOptions p, double x, double y, double z, double vx, double vy, double vz, Operation<Void> original) {
        if (!Blasts.replacing) original.call(level, p, x, y, z, vx, vy, vz);
    }
    //?}

    //? if >=1.21.9 {
    @WrapOperation(method = "handleExplosion", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;trackExplosionEffects(Lnet/minecraft/world/phys/Vec3;FILnet/minecraft/util/random/WeightedList;)V"))
    private void havingablast$explosionEffects(ClientLevel level, Vec3 center, float radius, int count, WeightedList<ExplosionParticleInfo> particles, Operation<Void> original) {
        if (!Blasts.replacing) original.call(level, center, radius, count, particles);
    }
    //?}

    /** A break event (2001) marks its block: popping off beside the blast counts as debris, a lone one (mining) doesn't. */
    @Inject(method = "handleLevelEvent", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER))
    private void havingablast$levelEvent(net.minecraft.network.protocol.game.ClientboundLevelEventPacket packet, CallbackInfo ci) {
        if (packet.getType() == 2001) Blasts.onBreakEvent(packet.getPos());
    }

    /** A piston moving: the cells along its push line change for other reasons than a blast. */
    @Inject(method = "handleBlockEvent", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER))
    private void havingablast$blockEvent(net.minecraft.network.protocol.game.ClientboundBlockEventPacket packet, CallbackInfo ci) {
        if (packet.getBlock() instanceof net.minecraft.world.level.block.piston.PistonBaseBlock) Blasts.onPiston(packet.getPos(), packet.getB1());
    }

    @Inject(method = "handleBlockEntityData", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
    private void havingablast$blockEntityData(net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
        if (Rebuild.holdBlockEntity(packet)) ci.cancel();
    }

    @Inject(method = "handleAddEntity", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER))
    private void havingablast$addEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (Probe.ON) Probe.entity(BuiltInRegistries.ENTITY_TYPE.getKey(packet.getType()).getPath(), packet.getId(), packet.getX(), packet.getY(), packet.getZ());
        Drops.onAdd(packet.getId(), packet.getType(), packet.getX(), packet.getY(), packet.getZ());
        Rebuild.onAddEntity(packet.getType(), packet.getX(), packet.getY(), packet.getZ());
    }
}
