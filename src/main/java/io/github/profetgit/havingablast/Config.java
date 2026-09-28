package io.github.profetgit.havingablast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Settings in config/havingablast.json (the game directory; on a server, the server directory). Repair is server
 * side and every kind is off by default (TNT is often used on purpose for mining and clearing). The visual settings
 * only matter on a client.
 */
public final class Config {
    /** Creeper explosions (charged ones too) repair themselves. */
    public boolean creeperRepair = false;
    /** TNT explosions (the block and the TNT minecart) repair themselves. */
    public boolean tntRepair = false;
    /** Beds that explode (the Nether, the End) repair the blast; the bed itself is used up. */
    public boolean bedRepair = false;
    /** Respawn anchors that explode repair the blast; the anchor itself is used up. */
    public boolean anchorRepair = false;
    /** End crystal explosions repair themselves. */
    public boolean crystalRepair = false;
    /** Ghast fireball explosions repair themselves (and the fire they start). */
    public boolean fireballRepair = false;
    /** The wither's blasts, skulls and the blocks it breaks when hurt repair themselves. */
    public boolean witherRepair = false;
    /** Seconds from the blast until its repair starts. */
    public int repairDelaySeconds = 30;
    /** Most blocks the ledger holds; a blast beyond it breaks as in vanilla. */
    public int maxPendingBlocks = 200_000;
    /** Client: the cartoon explosion (debris, puff, pops). */
    public boolean visuals = true;
    /** Client: how big the show is, in percent (50 to 150): launch power and the size of the fireball and smoke. */
    public int intensity = 100;
    /** Client: most debris flying at once; the rest become crumbs. */
    public int maxDebris = 2000;
    /** Client: debris pop into their drops; off, they sink away quietly. */
    public boolean pops = true;
    /** Client: the repair animation (blocks fly back into place). */
    public boolean repairAnimation = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config current;
    private static Path file;

    public static synchronized Config get() {
        if (current == null) load(Path.of("."));
        return current;
    }

    public static synchronized void load(Path gameDir) {
        Path f = gameDir.resolve("config").resolve("havingablast.json").toAbsolutePath().normalize();
        if (current != null && f.equals(file)) return;
        file = f;
        Config c = null;
        try {
            if (Files.isRegularFile(f)) c = GSON.fromJson(Files.readString(f), Config.class);
        } catch (Exception e) {
            HavingABlast.LOG.warn("Having a Blast: cannot read {}, using defaults ({})", f, e.toString());
        }
        current = c == null ? new Config() : c;
        save();
    }

    public static synchronized void save() {
        if (current == null || file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(current));
        } catch (Exception e) {
            HavingABlast.LOG.warn("Having a Blast: cannot write {} ({})", file, e.toString());
        }
    }
}
