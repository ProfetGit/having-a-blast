package io.github.profetgit.havingablast.client.config;

import io.github.profetgit.havingablast.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * Settings on one page. Labels are plain English: without Fabric API a Fabric mod's language files aren't loaded, and a
 * translation key that isn't found is shown as it is. The repair settings apply in singleplayer and on a server this
 * client hosts; on a dedicated server an operator uses /havingablast repair.
 */
public final class BlastConfigScreen extends OptionsSubScreen {
    public BlastConfigScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.literal("Having a Blast"));
    }

    @Override
    protected void addOptions() {
        Config c = Config.get();
        list.addHeader(Component.literal("Explosions (this client)"));
        list.addSmall(
            OptionInstance.createBoolean("Cartoon explosions", OptionInstance.cachedConstantTooltip(Component.literal("Debris, fireball, smoke and pops. Off: vanilla explosions.")),
                c.visuals, v -> c.visuals = v),
            new OptionInstance<>("Intensity", OptionInstance.cachedConstantTooltip(Component.literal("How big the show is: launch power, fireball and smoke size.")),
                (caption, v) -> Component.literal("Intensity: " + v + "%"), new OptionInstance.IntRange(5, 15), Math.max(5, Math.min(15, c.intensity / 10)), v -> c.intensity = v * 10),
            OptionInstance.createBoolean("Debris pop into drops", OptionInstance.cachedConstantTooltip(Component.literal("Off: debris shrink away quietly, no poof or sound.")),
                c.pops, v -> c.pops = v),
            new OptionInstance<>("Most debris at once", OptionInstance.cachedConstantTooltip(Component.literal("Beyond this, blasted blocks only crumble into particles.")),
                (caption, v) -> Component.literal("Most debris at once: " + v), new OptionInstance.IntRange(1, 40), Math.max(1, c.maxDebris / 100), v -> c.maxDebris = v * 100),
            OptionInstance.createBoolean("Repair animation", OptionInstance.cachedConstantTooltip(Component.literal("Repaired blocks fly back into place. Off: they just reappear.")),
                c.repairAnimation, v -> c.repairAnimation = v));
        list.addHeader(Component.literal("Auto repair (singleplayer; servers: /havingablast repair)"));
        list.addSmall(
            OptionInstance.createBoolean("Creeper blasts repair", OptionInstance.cachedConstantTooltip(Component.literal("Creeper explosions rebuild themselves after the delay. Off by default.")),
                c.creeperRepair, v -> c.creeperRepair = v),
            OptionInstance.createBoolean("TNT blasts repair", OptionInstance.cachedConstantTooltip(Component.literal("TNT and TNT minecart explosions rebuild themselves. Off by default: TNT is often used on purpose.")),
                c.tntRepair, v -> c.tntRepair = v),
            OptionInstance.createBoolean("Bed blasts repair", OptionInstance.cachedConstantTooltip(Component.literal("Beds exploding in the Nether or the End. The bed itself is used up. Off by default.")),
                c.bedRepair, v -> c.bedRepair = v),
            OptionInstance.createBoolean("Anchor blasts repair", OptionInstance.cachedConstantTooltip(Component.literal("Respawn anchors exploding outside the Nether. The anchor itself is used up. Off by default.")),
                c.anchorRepair, v -> c.anchorRepair = v),
            OptionInstance.createBoolean("Crystal blasts repair", OptionInstance.cachedConstantTooltip(Component.literal("End crystal explosions. The crystal itself is used up. Off by default.")),
                c.crystalRepair, v -> c.crystalRepair = v),
            OptionInstance.createBoolean("Fireball blasts repair", OptionInstance.cachedConstantTooltip(Component.literal("Ghast fireballs, and the fire they start. Off by default.")),
                c.fireballRepair, v -> c.fireballRepair = v),
            OptionInstance.createBoolean("Wither damage repairs", OptionInstance.cachedConstantTooltip(Component.literal("The wither's spawn blast, its skulls and the blocks it breaks when hurt. Off by default.")),
                c.witherRepair, v -> c.witherRepair = v),
            new OptionInstance<>("Repair delay", OptionInstance.cachedConstantTooltip(Component.literal("Seconds from the blast until the rebuild starts.")),
                (caption, v) -> Component.literal("Repair delay: " + v + " s"), new OptionInstance.IntRange(1, 300), Math.max(1, c.repairDelaySeconds), v -> c.repairDelaySeconds = v));
    }

    @Override
    public void removed() {
        Config.save();
        super.removed();
    }
}
