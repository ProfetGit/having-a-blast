package io.github.profetgit.havingablast.platform.forge;

import io.github.profetgit.havingablast.HavingABlast;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(HavingABlast.MOD_ID)
public final class ForgeEntry {
    public ForgeEntry() {
        HavingABlast.init("forge");
        if (FMLEnvironment.dist.isClient()) ForgeClient.register();
    }
}
