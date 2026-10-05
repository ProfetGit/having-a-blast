package io.github.profetgit.havingablast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

// What differs between the Minecraft versions this mod builds for.
public final class Compat {
    private Compat() {
    }

    public static void screenshot(com.mojang.blaze3d.pipeline.RenderTarget target, java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> then) {
        //? if >=1.21.5 {
        net.minecraft.client.Screenshot.takeScreenshot(target, then);
        //?} else {
        /*then.accept(net.minecraft.client.Screenshot.takeScreenshot(target));
        *///?}
    }

    // The game's own world light lookup, so mods that add light to it (Traveler's Lantern) reach the debris too.
    static int worldLight(net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.core.BlockPos pos) {
        //? if >=26.2 {
        return net.minecraft.util.LightCoordsUtil.getLightCoords(level, pos);
        //?} else {
        /*return net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pos);
        *///?}
    }

    static int withBlock(int packed, int block) {
        return net.minecraft.util.LightCoordsUtil.pack(block, net.minecraft.util.LightCoordsUtil.sky(packed));
    }

    // A custom geometry writer: the pose it was submitted with and the buffer to write to.
    interface Geo {
        void render(PoseStack.Pose pose, VertexConsumer vc);
    }

    static void geometry(SubmitNodeCollector c, PoseStack ps, RenderType type, Geo geo) {
        //? if >=1.21.9 {
        c.submitCustomGeometry(ps, type, geo::render);
        //?}
        //? if <1.21.9 {
        /*geo.render(ps.last(), c.getBuffer(type));
        *///?}
    }

    //? if >=1.21.5 <26.2 {
    /*static void quads(net.minecraft.world.level.block.state.BlockState state, java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> out) {
        java.util.List<net.minecraft.client.renderer.block.model.BlockModelPart> parts = new java.util.ArrayList<>();
        net.minecraft.client.Minecraft.getInstance().getBlockRenderer().getBlockModel(state).collectParts(net.minecraft.util.RandomSource.create(42), parts);
        for (net.minecraft.client.renderer.block.model.BlockModelPart p : parts) {
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) out.addAll(p.getQuads(d));
            out.addAll(p.getQuads(null));
        }
    }
*///?}
    //? if <1.21.5 {
    /*static void quads(net.minecraft.world.level.block.state.BlockState state, java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> out) {
        net.minecraft.client.resources.model.BakedModel model = net.minecraft.client.Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(42);
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) out.addAll(model.getQuads(state, d, random));
        out.addAll(model.getQuads(state, null, random));
    }
*///?}
}
