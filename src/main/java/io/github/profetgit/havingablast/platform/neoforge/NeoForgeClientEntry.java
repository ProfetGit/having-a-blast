package io.github.profetgit.havingablast.platform.neoforge;

//? neoforge {
/*import io.github.profetgit.havingablast.HavingABlast;
import io.github.profetgit.havingablast.client.config.BlastConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/^* The client half: the settings screen in NeoForge's mod list. *^/
@Mod(value = HavingABlast.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClientEntry {
    public NeoForgeClientEntry(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new BlastConfigScreen(parent));
    }
}
*///?}
