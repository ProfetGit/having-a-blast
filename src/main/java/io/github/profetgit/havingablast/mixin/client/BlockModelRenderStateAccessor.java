package io.github.profetgit.havingablast.mixin.client;

//? if >=26.2 {
import java.util.List;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockModelRenderState.class)
public interface BlockModelRenderStateAccessor {
    @Accessor("modelParts")
    List<BlockStateModelPart> havingablast$parts();

    @Accessor("transformation")
    Matrix4fc havingablast$transformation();

    @Accessor("renderType")
    RenderType havingablast$renderType();

    @Accessor("specialRenderer")
    SpecialModelRenderer<?> havingablast$special();
}
//?} else {
/*import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

// The model resolver is 26.x only: kept so the mixin list is the same for every version.
@Mixin(Minecraft.class)
public interface BlockModelRenderStateAccessor {
}
*///?}
