package io.github.profetgit.havingablast.demo;

import static io.github.profetgit.havingablast.demo.Director.G;
import static io.github.profetgit.havingablast.demo.Director.cmd;

import io.github.profetgit.havingablast.client.Blasts;
import io.github.profetgit.havingablast.client.DebrisRenderer;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/** The demo's scenes: arena reset, staging, camera, trigger, checks. */
final class Scenes {
    /** The blast spot: arena centre on the grass. */
    static final double X = 0.5, Z = 16.5, Y = G + 1;
    static int blastFrame = -1, capturedAtStart, poppedAtStart, blastsAtStart, overflowAtStart, hiddenAtStart, revealedAtStart;

    private Scenes() {
    }

    static int length(String s) {
        return switch (s) {
            case "probe1" -> 120;
            case "tnt10" -> 220;
            case "tnt100" -> 300;
            case "tnt1000" -> 420;
            case "creeper", "creeper_charged" -> 240;
            case "repair", "repair_tnt" -> 330;
            case "sources" -> 470;
            case "window_noise" -> 200;
            case "griefing_off", "lava", "garden", "far", "mobs" -> 200;
            case "bed_nether", "anchor" -> 340;
            case "nether_tnt", "end_tnt" -> 260;
            case "wither" -> 420;
            case "underwater", "cave", "house_tnt" -> 200;
            default -> 170;
        };
    }

    /** Rebuilds the arena (x -24..24, z -8..40): stone, 3 dirt, grass at G, air above; kills loose entities. */
    static void resetArena(Minecraft mc) {
        cmd(mc, "kill @e[type=!player,type=!armor_stand]",
            "fill -24 " + (G - 12) + " -8 24 " + (G - 4) + " 40 minecraft:stone",
            "fill -24 " + (G - 3) + " -8 24 " + (G - 1) + " 40 minecraft:dirt",
            "fill -24 " + G + " -8 24 " + G + " 40 minecraft:grass_block",
            "fill -24 " + (G + 1) + " -8 24 " + (G + 24) + " 40 minecraft:air",
            "kill @e[type=item]");
    }

    /** Puts the camera stand at (x,y,z) looking at (lx,ly,lz). */
    static void camera(Minecraft mc, double x, double y, double z, double lx, double ly, double lz) {
        double dx = lx - x, dy = ly - y, dz = lz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        cmd(mc, String.format(Locale.ROOT, "execute in minecraft:overworld run tp @e[type=armor_stand,name=cam,limit=1] %.2f %.2f %.2f %.2f %.2f", x, y, z, yaw, pitch));
    }

    /** The scene's camera for -Dhavingablast.demo.cam: side (16 blocks, level), hero (low three-quarter), fp (a player 8 blocks away), near (close on the front, for the rebuild). */
    static void camera(Minecraft mc, double tx, double ty, double tz, double dist) {
        switch (Director.CAM) {
            case "hero" -> camera(mc, tx - dist * 0.55, ty + 0.6, tz - dist * 0.62, tx, ty + 1.2, tz);
            case "fp" -> camera(mc, tx, ty + 1.62, tz - dist * 0.55, tx, ty + 0.8, tz);
            case "near" -> camera(mc, tx - dist * 0.22, ty + 1.2, tz - dist * 0.48, tx, ty + 0.6, tz - 1);
            default -> camera(mc, tx - dist, ty + dist * 0.28, tz, tx, ty + 0.8, tz);
        }
    }

    static void start(Minecraft mc, String s) {
        resetArena(mc);
        // nothing picked up in one scene is held in the next (a creative player picks up drops)
        cmd(mc, "clear " + mc.player.getGameProfile().name());
        blastFrame = -1;
        capturedAtStart = Blasts.totalCaptured;
        poppedAtStart = Blasts.totalPopped;
        blastsAtStart = Blasts.totalBlasts;
        serverAtStart = io.github.profetgit.havingablast.dev.BlastCount.total;
        unsentAtStart = io.github.profetgit.havingablast.dev.BlastCount.unsent;
        noiseAt = -1;
        modFrames = modMicros = 0;
        rebuiltAtStart = io.github.profetgit.havingablast.client.Rebuild.rebuilt;
        decorAtStart = io.github.profetgit.havingablast.client.Rebuild.decorPoofs;
        decorKeptAtStart = io.github.profetgit.havingablast.repair.Decor.captured;
        decorBackAtStart = io.github.profetgit.havingablast.repair.Decor.restored;
        // the new repair sources: each scene turns its own on (off by default), so their rebuild is filmed too
        if (NEW_SOURCES.contains(s)) cmd(mc, "havingablast repair bed true", "havingablast repair anchor true", "havingablast repair crystal true",
            "havingablast repair fireball true", "havingablast repair wither true", "havingablast repair tnt true", "havingablast repair creeper true", "havingablast repair delay 3");
        overflowAtStart = Blasts.totalOverflow;
        hiddenAtStart = io.github.profetgit.havingablast.client.Drops.hiddenTotal;
        revealedAtStart = io.github.profetgit.havingablast.client.Drops.revealedTotal;
        switch (s) {
            case "tnt10" -> {
                cmd(mc, "fill -4 " + (int) Y + " 16 5 " + (int) Y + " 16 minecraft:tnt");
                camera(mc, X, Y, Z, 22);
            }
            case "tnt100" -> {
                cmd(mc, "fill -4 " + (int) Y + " 12 5 " + (int) Y + " 21 minecraft:tnt");
                camera(mc, X, Y, Z, 34);
            }
            case "tnt1000" -> {
                cmd(mc, "fill -4 " + (int) Y + " 12 5 " + ((int) Y + 9) + " 21 minecraft:tnt");
                camera(mc, X, Y + 4, Z, 46);
            }
            case "creeper", "creeper_charged", "repair", "repair_tnt" -> {
                cmd(mc, io.github.profetgit.havingablast.dev.House.commands(-3, (int) Y - 1, 16).toArray(new String[0]));
                if (s.startsWith("repair")) cmd(mc, "havingablast repair creeper true", "havingablast repair tnt true", "havingablast repair delay 3");
                if (s.equals("repair")) {
                    // frames and a painting on the front wall, a stand beside the door: kept through the blast, hung back up after the rebuild
                    int y = (int) Y;
                    cmd(mc, String.format(Locale.ROOT, "summon item_frame -1.5 %d.5 15.5 {block_pos:[I;-2,%d,15],Pos:[-1.5d,%d.5d,15.5d],Facing:2b,Item:{id:\"minecraft:diamond_sword\",count:1},Tags:[\"hab_decor\"]}", y + 1, y + 1, y + 1),
                        String.format(Locale.ROOT, "summon glow_item_frame 2.5 %d.5 15.5 {block_pos:[I;2,%d,15],Pos:[2.5d,%d.5d,15.5d],Facing:2b,Item:{id:\"minecraft:clock\",count:1},Tags:[\"hab_decor\"]}", y + 1, y + 1, y + 1),
                        String.format(Locale.ROOT, "summon painting 1.5 %d.5 15.5 {block_pos:[I;1,%d,15],Pos:[1.5d,%d.5d,15.5d],facing:2b,variant:\"minecraft:kebab\",Tags:[\"hab_decor\"]}", y + 2, y + 2, y + 2),
                        String.format(Locale.ROOT, "summon armor_stand -2.5 %d 14.5 {ShowArms:1b,equipment:{head:{id:\"minecraft:iron_helmet\",count:1}},Tags:[\"hab_decor\"]}", y));
                }
                camera(mc, X, Y + 1, Z + 1, 12);
                rebuiltAtStart = io.github.profetgit.havingablast.client.Rebuild.rebuilt;
            }
            case "sources" -> camera(mc, X, Y + 1, Z, 26);
            case "window_noise" -> {
                int y = (int) Y;
                // on obsidian pillars 7 blocks out (inside the capture reach, touching nothing the blast removes): a sticky
                // piston with a block (east), sand and gravel on a dirt block that gets "mined" (west), a block to "mine" (south)
                cmd(mc, "fill 7 " + (y - 1) + " 15 9 " + (y + 1) + " 17 minecraft:obsidian", "setblock 7 " + (y + 2) + " 16 minecraft:sticky_piston[facing=east]",
                    "setblock 8 " + (y + 2) + " 16 minecraft:oak_planks",
                    "fill -7 " + (y - 1) + " 16 -7 " + (y + 1) + " 16 minecraft:obsidian", "setblock -7 " + (y + 2) + " 16 minecraft:dirt",
                    "setblock -7 " + (y + 3) + " 16 minecraft:sand", "setblock -7 " + (y + 4) + " 16 minecraft:gravel",
                    "fill 0 " + (y - 1) + " 24 0 " + (y + 1) + " 24 minecraft:obsidian", "setblock 0 " + (y + 2) + " 24 minecraft:cobblestone");
                camera(mc, X, Y + 1, Z, 18);
            }
            case "bed_nether", "nether_tnt" -> {
                // the player goes first (chunks load around it); the platform, bed and camera are built at t 40
                cmd(mc, "execute in minecraft:the_nether run tp " + mc.player.getGameProfile().name() + (s.equals("bed_nether") ? " 0.5 70 13.8 0 30" : " 0.5 70 -2 0 10"));
            }
            case "end_tnt" -> cmd(mc, "execute in minecraft:the_end run tp " + mc.player.getGameProfile().name() + " 200.5 60 198 0 10");
            case "anchor" -> {
                cmd(mc, "setblock 0 " + (int) Y + " 16 minecraft:respawn_anchor[charges=4]", "tp " + mc.player.getGameProfile().name() + " 0.5 " + (int) Y + " 13.8 0 30");
                camera(mc, X, Y, Z, 16);
            }
            case "wither", "griefing_off" -> {
                if (s.equals("griefing_off")) cmd(mc, "gamerule mob_griefing false");
                camera(mc, X, Y + 2, Z, 20);
            }
            case "lava" -> {
                cmd(mc, "fill 3 " + ((int) Y - 2) + " 13 7 " + ((int) Y - 1) + " 20 minecraft:lava");
                camera(mc, X, Y, Z, 16);
            }
            case "garden" -> {
                int y = (int) Y;
                // leaves (tinted and not), tall and short plants, glass and panes, snow, a log, and sand that falls after
                cmd(mc, "fill -3 " + y + " 13 -3 " + (y + 1) + " 19 minecraft:oak_leaves[persistent=true]", "fill 4 " + y + " 13 4 " + (y + 1) + " 19 minecraft:birch_leaves[persistent=true]",
                    "fill -2 " + y + " 13 3 " + y + " 13 minecraft:glass_pane", "fill -2 " + y + " 19 3 " + (y + 1) + " 19 minecraft:glass",
                    "setblock -1 " + y + " 15 minecraft:tall_grass[half=lower]", "setblock -1 " + (y + 1) + " 15 minecraft:tall_grass[half=upper]",
                    "setblock 2 " + y + " 15 minecraft:rose_bush[half=lower]", "setblock 2 " + (y + 1) + " 15 minecraft:rose_bush[half=upper]",
                    "setblock 1 " + y + " 18 minecraft:poppy", "setblock -1 " + y + " 18 minecraft:short_grass", "fill 1 " + y + " 14 3 " + y + " 14 minecraft:snow[layers=2]",
                    "setblock -2 " + y + " 17 minecraft:oak_log", "fill 5 " + y + " 16 5 " + (y + 2) + " 16 minecraft:sand");
                camera(mc, X, Y, Z, 14);
            }
            case "far" -> camera(mc, X, Y + 6, 40.5, 22);
            case "mobs" -> {
                cmd(mc, "summon zombie 2.5 " + (int) Y + " 14.5 {NoAI:1b,PersistenceRequired:1b,equipment:{head:{id:\"minecraft:stone_button\",count:1}}}",
                    "summon zombie -1.5 " + (int) Y + " 18.5 {NoAI:1b,PersistenceRequired:1b,equipment:{head:{id:\"minecraft:stone_button\",count:1}}}",
                    "summon husk 3.5 " + (int) Y + " 19.5 {NoAI:1b,PersistenceRequired:1b}");
                camera(mc, X, Y, Z, 14);
            }
            case "underwater" -> {
                cmd(mc, "fill -6 " + ((int) Y - 6) + " 11 7 " + ((int) Y - 1) + " 22 minecraft:water", "fill -7 " + ((int) Y - 7) + " 10 8 " + ((int) Y - 7) + " 23 minecraft:sand");
                camera(mc, X, Y, Z, 14);
            }
            case "cave" -> {
                // a tunnel under 4 blocks of stone with a torch: the blast digs a crater underground
                cmd(mc, "fill -12 " + ((int) Y - 9) + " 10 12 " + ((int) Y - 1) + " 23 minecraft:stone", "fill -12 " + ((int) Y - 7) + " 14 12 " + ((int) Y - 5) + " 19 minecraft:air",
                    "setblock -3 " + ((int) Y - 6) + " 14 minecraft:wall_torch[facing=south]", "setblock 4 " + ((int) Y - 6) + " 14 minecraft:wall_torch[facing=south]");
                camera(mc, -9.5, Y - 6, 16.5, X, Y - 6.5, Z);
            }
            case "house_tnt" -> {
                cmd(mc, io.github.profetgit.havingablast.dev.House.commands(-3, (int) Y - 1, 16).toArray(new String[0]));
                camera(mc, X, Y + 1, Z + 1, 13);
            }
            default -> camera(mc, X, Y, Z, 16);
        }
    }

    static void tick(Minecraft mc, String s, int t) {
        switch (s) {
            case "tnt10", "tnt100", "tnt1000" -> {
                if (t == 10) Director.record();
                // one primed TNT beside the stack sets off the chain
                if (t == 20) cmd(mc, String.format(Locale.ROOT, "summon tnt %.1f %d %.1f {fuse:30}", -5.5, (int) Y, Z));
            }
            case "creeper", "creeper_charged", "repair" -> {
                if (t == 10) Director.record();
                // right at the front wall, lit: vanilla's fuse is 30 ticks of swelling
                if (t == 20) cmd(mc, String.format(Locale.ROOT, "summon creeper %.1f %d %.1f {ignited:1b,Rotation:[180f,0f]%s}", 0.5, (int) Y, 14.7, s.equals("creeper_charged") ? ",powered:1b" : ""));
            }
            case "sources" -> {
                if (t == 10) Director.record();
                // one after another along the lane: TNT minecart, charged creeper, end crystal, ghast fireball, wither skull
                if (t == 20) cmd(mc, String.format(Locale.ROOT, "summon tnt_minecart %.1f %d %.1f {fuse:20}", -8.5, (int) Y, Z));
                if (t == 70) cmd(mc, String.format(Locale.ROOT, "summon creeper %.1f %d %.1f {ignited:1b,NoAI:1b,powered:1b}", -2.5, (int) Y, Z + 6));
                if (t == 150) cmd(mc, String.format(Locale.ROOT, "summon end_crystal %.1f %d %.1f {ShowBottom:0b}", 3.5, (int) Y, Z - 4), "damage @e[type=end_crystal,limit=1] 1");
                if (t == 220) cmd(mc, String.format(Locale.ROOT, "summon fireball %.1f %d %.1f {power:[0.0,-0.5,0.0],ExplosionPower:2}", 8.5, (int) Y + 10, Z));
                if (t == 290) cmd(mc, String.format(Locale.ROOT, "summon wither_skull %.1f %d %.1f {power:[0.0,-0.5,0.0],dangerous:1b}", 0.5, (int) Y + 10, Z + 3));
            }
            case "window_noise" -> {
                if (t == 10) Director.record();
                if (t == 20) cmd(mc, String.format(Locale.ROOT, "summon tnt %.1f %d %.1f {fuse:30}", X, (int) Y, Z));
                // right when the blast shows: the other kinds of block removal, inside the capture window
                if (noiseAt < 0 && Blasts.totalBlasts > blastsAtStart) {
                    noiseAt = t;
                    int y = (int) Y;
                    cmd(mc, "setblock 7 " + (y + 3) + " 16 minecraft:redstone_block", "setblock -7 " + (y + 2) + " 16 minecraft:air destroy",
                        "setblock 0 " + (y + 2) + " 24 minecraft:air destroy");
                }
                // unpowered by swapping in glass (a plain /setblock air right there would look like a blast removal)
                if (noiseAt >= 0 && t == noiseAt + 1) cmd(mc, "setblock 7 " + ((int) Y + 3) + " 16 minecraft:glass");
            }
            case "bed_nether", "anchor" -> {
                if (t == 40 && s.equals("bed_nether")) buildNether(mc, true);
                // wait out the dimension change (the client reloads the level), then the player clicks
                boolean there = mc.level.dimension() == (s.equals("anchor") ? net.minecraft.world.level.Level.OVERWORLD : net.minecraft.world.level.Level.NETHER);
                if (t == 60) Director.record();
                if (t == 80 && there) Director.use(mc, s.equals("anchor") ? new net.minecraft.core.BlockPos(0, (int) Y, 16) : new net.minecraft.core.BlockPos(0, 70, 17));
                if (t == 80 && !there) System.out.println("[habdemo] " + s + ": the player isn't in the scene's dimension yet");
            }
            case "nether_tnt" -> {
                if (t == 40) buildNether(mc, false);
                if (t == 50) Director.record();
                if (t == 60) cmd(mc, "execute in minecraft:the_nether run summon tnt 0.5 70 16.5 {fuse:30}");
            }
            case "end_tnt" -> {
                if (t == 40) buildEnd(mc);
                if (t == 50) Director.record();
                if (t == 60) cmd(mc, "execute in minecraft:the_end run summon tnt 200.5 60 217.5 {fuse:30}");
                if (t == 120) cmd(mc, "execute in minecraft:the_end run summon end_crystal 196.5 60 212.5 {ShowBottom:0b}");
                if (t == 125) cmd(mc, "execute in minecraft:the_end positioned 196.5 60 212.5 run damage @e[type=end_crystal,limit=1,sort=nearest,distance=..3] 1");
            }
            case "wither" -> {
                if (t == 10) Director.record();
                // the wither's spawn blast comes after its 220-tick charge-up
                if (t == 20) cmd(mc, String.format(Locale.ROOT, "summon wither %.1f %d %.1f {Invul:220}", X, (int) Y, Z));
            }
            case "griefing_off" -> {
                if (t == 10) Director.record();
                if (t == 20) cmd(mc, String.format(Locale.ROOT, "summon creeper %.1f %d %.1f {ignited:1b,NoAI:1b}", X, (int) Y, Z));
            }
            case "far" -> {
                if (t == 10) Director.record();
                // 70 blocks from the player: past the 64 blocks the server sends explode packets to, inside the
                // simulation distance (5 chunks, so the TNT ticks) and the render distance
                if (t == 20) cmd(mc, String.format(Locale.ROOT, "summon tnt 0.5 %d 58.5 {fuse:30}", (int) Y));
            }
            case "lava", "garden", "mobs" -> {
                if (t == 10) Director.record();
                if (t == 20) cmd(mc, String.format(Locale.ROOT, "summon tnt %.1f %d %.1f {fuse:30}", X, (int) Y, Z));
            }
            case "underwater" -> {
                if (t == 10) Director.record();
                if (t == 20) cmd(mc, String.format(Locale.ROOT, "summon tnt %.1f %d %.1f {fuse:30}", X, (int) Y - 5, Z));
            }
            case "cave" -> {
                if (t == 10) Director.record();
                if (t == 20) cmd(mc, String.format(Locale.ROOT, "summon tnt %.1f %d %.1f {fuse:30}", X, (int) Y - 7, Z));
            }
            case "house_tnt" -> {
                if (t == 10) Director.record();
                if (t == 20) cmd(mc, String.format(Locale.ROOT, "summon tnt %.1f %d %.1f {fuse:30}", 0.5, (int) Y, 18.5));
            }
            case "repair_tnt" -> {
                if (t == 10) Director.record();
                if (t == 20) cmd(mc, String.format(Locale.ROOT, "summon tnt %.1f %d %.1f {fuse:30}", 0.5, (int) Y, 14.5));
            }
            default -> {
                if (t == 10) Director.record();
                if (t == 20) cmd(mc, String.format(Locale.ROOT, "summon tnt %.1f %d %.1f {fuse:30}", X, (int) Y, Z));
            }
        }
    }

    static int rebuiltAtStart, serverAtStart, unsentAtStart, noiseAt = -1, decorAtStart, decorKeptAtStart, decorBackAtStart;
    static final java.util.Set<String> NEW_SOURCES = java.util.Set.of("bed_nether", "anchor", "sources", "wither");

    /** A netherrack platform at y 70 with a camera of its own (and a bed), once the player's chunks are loaded. */
    static void buildNether(Minecraft mc, boolean bed) {
        String in = "execute in minecraft:the_nether run ";
        cmd(mc, in + "fill -14 60 4 14 69 30 minecraft:netherrack", in + "fill -14 70 4 14 90 30 minecraft:air",
            in + "kill @e[type=armor_stand,name=ncam]", in + "summon armor_stand -9.5 74 11 {Invisible:1b,Marker:1b,NoGravity:1b,CustomName:\"ncam\",Rotation:[-60f,14f]}");
        // the player may have fallen while the chunks loaded (1.21.1): back onto the platform
        cmd(mc, in + "tp " + mc.player.getGameProfile().name() + (bed ? " 0.5 70 13.8 0 30" : " 0.5 70 -2 0 10"));
        if (bed) cmd(mc, in + "setblock 0 70 16 minecraft:red_bed[facing=south,part=foot]", in + "setblock 0 70 17 minecraft:red_bed[facing=south,part=head]");
    }

    /** An end stone island with purpur, glass and an end rod around the blasts (end stone shrugs off TNT). */
    static void buildEnd(Minecraft mc) {
        String in = "execute in minecraft:the_end run ";
        cmd(mc, in + "fill 186 50 204 214 59 230 minecraft:end_stone", in + "fill 186 60 204 214 80 230 minecraft:air",
            in + "fill 193 57 209 207 59 223 minecraft:purpur_block", in + "fill 195 60 211 205 61 221 minecraft:purpur_pillar",
            in + "fill 196 60 212 204 61 220 minecraft:air", in + "fill 197 62 213 203 62 219 minecraft:glass", in + "setblock 194 60 210 minecraft:end_rod",
            in + "kill @e[type=armor_stand,name=ecam]", in + "summon armor_stand 187.5 66 203 {Invisible:1b,Marker:1b,NoGravity:1b,CustomName:\"ecam\",Rotation:[-45f,18f]}");
    }
    static long modFrames, modMicros;

    static void finish(Minecraft mc, String s) {
        if (s.equals("griefing_off")) cmd(mc, "gamerule mob_griefing true");
        if (s.equals("wither")) cmd(mc, "kill @e[type=wither]", "kill @e[type=wither_skull]");
        if (s.equals("bed_nether") || s.equals("nether_tnt") || s.equals("end_tnt")) cmd(mc, "execute in minecraft:overworld run tp " + mc.player.getGameProfile().name() + " 0.5 " + (int) Y + " -12.5 0 0");
        if (s.startsWith("repair") || NEW_SOURCES.contains(s)) {
            int n = io.github.profetgit.havingablast.client.Rebuild.rebuilt - rebuiltAtStart;
            Director.check("rebuild_animated", n > 0, n + " blocks flew back in (their updates held for the animation)");
            cmd(mc, "havingablast repair creeper false", "havingablast repair tnt false", "havingablast repair bed false", "havingablast repair anchor false",
                "havingablast repair crystal false", "havingablast repair fireball false", "havingablast repair wither false", "havingablast repair delay 30");
        }
        if (s.equals("repair")) {
            int d = io.github.profetgit.havingablast.client.Rebuild.decorPoofs - decorAtStart;
            int kept = io.github.profetgit.havingablast.repair.Decor.captured - decorKeptAtStart, back = io.github.profetgit.havingablast.repair.Decor.restored - decorBackAtStart;
            Director.check("decor_back", kept > 0 && back == kept && d == back, kept + " frames, paintings and stands kept through the blast, " + back + " hung back up, "
                + d + " popped back in on this client");
            cmd(mc, "kill @e[tag=hab_decor]");
        }

        int captured = Blasts.totalCaptured - capturedAtStart, popped = Blasts.totalPopped - poppedAtStart, blasts = Blasts.totalBlasts - blastsAtStart;
        if (!s.equals("far")) Director.check("blast", blasts > 0, blasts + " blasts seen");
        int server0 = io.github.profetgit.havingablast.dev.BlastCount.total - serverAtStart;
        // under water or without mob griefing a blast breaks nothing; far away there is no explode packet at all
        if (s.equals("underwater") || s.equals("griefing_off")) Director.check("debris", captured == 0, captured + " blocks captured (nothing breaks here)");
        else if (s.equals("far")) Director.check("debris", captured == 0 && blasts == 0 && server0 == 0 && io.github.profetgit.havingablast.dev.BlastCount.unsent > unsentAtStart,
            "no explode packet past 64 blocks: " + blasts + " blasts, " + captured + " captured (the vanilla look); the server broke " + (io.github.profetgit.havingablast.dev.BlastCount.unsent - unsentAtStart) + " there");
        else Director.check("debris", captured > 0, captured + " blocks captured");
        // the mod's own render-thread time per frame, averaged over the recorded scene (budget: 0.5 ms for one TNT)
        double mean = modFrames == 0 ? 0 : modMicros / 1000.0 / modFrames;
        double budget = s.equals("tnt1") || s.startsWith("creeper") || s.equals("house_tnt") ? 0.5 : s.equals("tnt1000") ? 4 : 2;
        Director.check("frame_budget", mean < budget, String.format(Locale.ROOT, "mod %.3f ms per frame on average over %d frames (budget %.1f)", mean, modFrames, budget));
        long left = Blasts.liveCount();
        Director.check("none_left", left == 0 || s.equals("tnt1000"), left + " debris still live at the end of the scene");
        int server = io.github.profetgit.havingablast.dev.BlastCount.total - serverAtStart;
        // a bed or respawn anchor removes itself just before it calls the explosion (outside the blast's block pass,
        // so the server counter doesn't see it); the client rightly flies it as debris too: 2 bed halves, 1 anchor
        int self = s.equals("bed_nether") ? 2 : s.equals("anchor") ? 1 : 0;
        // flung TNT in a big chain goes off past 64 blocks (no packet: nothing drawn); those of its removals within reach
        // of a blast the client does draw fly with that one. So: server <= client <= server + unsent-blast removals
        int unsent = io.github.profetgit.havingablast.dev.BlastCount.unsent - unsentAtStart;
        int extra = captured - self - server;
        if (!s.equals("far")) Director.check("count_match", extra >= 0 && extra <= unsent, "server removed " + server + ", client captured " + captured
            + (self > 0 ? " (+" + self + ": the exploding block itself)" : "") + (unsent > 0 ? " (+" + extra + " of " + unsent + " removed by flung TNT past the 64-block packet range)" : " (same rule both sides)"));
        Director.check("popped", popped == captured - (Blasts.totalOverflow - overflowAtStart), popped + " of " + captured + " popped (" + (Blasts.totalOverflow - overflowAtStart) + " over the cap became crumbs)");
        int hidden = io.github.profetgit.havingablast.client.Drops.hiddenTotal - hiddenAtStart, revealed = io.github.profetgit.havingablast.client.Drops.revealedTotal - revealedAtStart;
        Director.check("continuity", Blasts.maxJump < 0.15, String.format(Locale.ROOT, "largest debris step between 0.05-tick samples %.3f blocks (scorched %d, kept cover %d)", Blasts.maxJump, Blasts.scorched, Blasts.keptCover));
        Blasts.maxJump = 0;
        Director.check("drops", revealed == hidden, revealed + " of " + hidden + " hidden drops revealed");
    }

    static String timingHeader() {
        return "blasts drawn captured popped mod_us | blast_frame " + blastFrame;
    }

    static String timingLine(Minecraft mc, float partial) {
        int blasts = Blasts.totalBlasts - blastsAtStart;
        if (blasts > 0 && blastFrame < 0) blastFrame = Director.frame - 1;
        long us = DebrisRenderer.spentNanos / 1000;
        DebrisRenderer.spentNanos = 0;
        modFrames++;
        modMicros += us;
        return blasts + " " + DebrisRenderer.drawn + " " + (Blasts.totalCaptured - capturedAtStart) + " " + (Blasts.totalPopped - poppedAtStart) + " " + us;
    }
}
