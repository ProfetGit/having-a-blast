package io.github.profetgit.havingablast.mixin.common;

import io.github.profetgit.havingablast.repair.Repair;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blocks a player places (or pours) into a hole waiting for repair are theirs: the repair leaves them. */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void havingablast$useOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        Repair.playerAction(true);
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void havingablast$useOnEnd(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        Repair.playerAction(false);
    }

    @Inject(method = "useItem", at = @At("HEAD"))
    private void havingablast$use(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Repair.playerAction(true);
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void havingablast$useEnd(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Repair.playerAction(false);
    }
}
