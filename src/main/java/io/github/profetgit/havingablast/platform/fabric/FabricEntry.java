package io.github.profetgit.havingablast.platform.fabric;

//? fabric {
import io.github.profetgit.havingablast.HavingABlast;
import net.fabricmc.api.ModInitializer;

public final class FabricEntry implements ModInitializer {
    @Override
    public void onInitialize() {
        HavingABlast.init("fabric");
    }
}
//?}
