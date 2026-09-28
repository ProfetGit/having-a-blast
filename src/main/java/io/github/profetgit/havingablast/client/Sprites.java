package io.github.profetgit.havingablast.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.profetgit.havingablast.HavingABlast;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The FX sprite sheet (dev/fx/make_fx.py): 256 px, 32 px cells above and 64 px rings below, greyscale, tinted per sprite. Fabric without Fabric API
 * loads no mod assets, so the sheet is read from the jar and registered as a dynamic texture.
 */
final class Sprites {
    static final Identifier SHEET = Identifier.fromNamespaceAndPath("havingablast", "fx/sheet");
    static RenderType type;
    static boolean failed;

    private Sprites() {
    }

    static RenderType type() {
        if (type != null || failed) return type;
        String path = "/assets/havingablast/textures/fx/fx.png";
        try (InputStream in = Sprites.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            NativeImage img = NativeImage.read(in);
            Minecraft.getInstance().getTextureManager().register(SHEET, new DynamicTexture(() -> "havingablast fx", img));
            type = RenderTypes.entityCutout(SHEET);
        } catch (Exception e) {
            failed = true;
            HavingABlast.LOG.error("Having a Blast: cannot load the FX sprites", e);
        }
        return type;
    }
}
