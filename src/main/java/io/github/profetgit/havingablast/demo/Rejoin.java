package io.github.profetgit.havingablast.demo;

//? if <1.21.2 {
/*import io.github.profetgit.havingablast.HavingABlast;
import net.minecraft.client.Minecraft;

/^* Test harness only. Forge before 1.21.2 starts its event bus after --quickPlaySingleplayer has already started the world:
 * the server's reload and start events are lost, and the first block drop crashes with "Can not retrieve LootModifierManager
 * until resources have loaded once". Leaving and opening the world again once the bus runs avoids it. ^/
final class Rejoin {
    private static int state;
    private static int wait;

    private Rejoin() {
    }

    /^* True while it handles the tick: the scene director has to wait. ^/
    static boolean tick(Minecraft mc) {
        if (state == 2 || !"forge".equals(HavingABlast.loader())) return false;
        if (state == 0) {
            if (mc.level == null || mc.player == null) return false;
            state = 1;
            System.out.println("[habdemo] rejoining the world (Forge starts its event bus late)");
            mc.level.disconnect();
            mc.disconnect();
            return true;
        }
        if (mc.level != null || mc.getSingleplayerServer() != null) return true;
        if (++wait < 60) return true;
        state = 2;
        mc.createWorldOpenFlows().openWorld("scene", () -> { });
        return true;
    }
}
*///?}
