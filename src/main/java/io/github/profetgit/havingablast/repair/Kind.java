package io.github.profetgit.havingablast.repair;

import io.github.profetgit.havingablast.Config;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;

/**
 * What blew up. Each kind has its own repair toggle, all off by default. The thing that exploded is used up and never
 * comes back (TNT, the crystal, the bed, the anchor), so a repair never hands back the ammunition.
 */
public enum Kind {
    CREEPER("creeper", "Creeper"),
    TNT("tnt", "TNT"),
    BED("bed", "Bed"),
    ANCHOR("anchor", "Respawn anchor"),
    CRYSTAL("crystal", "End crystal"),
    FIREBALL("fireball", "Ghast fireball"),
    WITHER("wither", "Wither");

    public final String id, label;

    Kind(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** Set by the bed and respawn anchor mixins around their explosion: it has no source entity. */
    public static final ThreadLocal<Kind> BLOCK_SOURCE = new ThreadLocal<>();

    public static Kind of(Entity direct) {
        if (direct == null) return BLOCK_SOURCE.get();
        if (direct instanceof Creeper) return CREEPER;
        if (direct instanceof PrimedTnt || direct instanceof MinecartTNT) return TNT;
        if (direct instanceof EndCrystal) return CRYSTAL;
        if (direct instanceof LargeFireball) return FIREBALL;
        if (direct instanceof WitherSkull || direct instanceof WitherBoss) return WITHER;
        return null;
    }

    public boolean enabled() {
        Config c = Config.get();
        return switch (this) {
            case CREEPER -> c.creeperRepair;
            case TNT -> c.tntRepair;
            case BED -> c.bedRepair;
            case ANCHOR -> c.anchorRepair;
            case CRYSTAL -> c.crystalRepair;
            case FIREBALL -> c.fireballRepair;
            case WITHER -> c.witherRepair;
        };
    }

    public void set(boolean on) {
        Config c = Config.get();
        switch (this) {
            case CREEPER -> c.creeperRepair = on;
            case TNT -> c.tntRepair = on;
            case BED -> c.bedRepair = on;
            case ANCHOR -> c.anchorRepair = on;
            case CRYSTAL -> c.crystalRepair = on;
            case FIREBALL -> c.fireballRepair = on;
            case WITHER -> c.witherRepair = on;
        }
    }

    public static boolean anyEnabled() {
        for (Kind k : values()) if (k.enabled()) return true;
        return false;
    }
}
