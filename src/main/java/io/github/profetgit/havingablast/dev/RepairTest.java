package io.github.profetgit.havingablast.dev;

import io.github.profetgit.havingablast.Config;
import io.github.profetgit.havingablast.repair.Kind;
import io.github.profetgit.havingablast.repair.Ledger;
import io.github.profetgit.havingablast.repair.Repair;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Dev only: the repair tests on a dedicated server (dev/server/repairtest.py), inert unless -Dhavingablast.test.
 * `/habtest run <scenario|all>` plays the scenarios one after another in the overworld, each on its own plot
 * (force-loaded): it builds the test house, snapshots every block state and block entity around it, sets off the
 * blast, and checks drops, the rebuild (exact states and data), item counts, toggles and sources. Prints
 * "[habtest] PASS|FAIL <scenario>/<check> <detail>" and "[habtest] done <passed>/<total>".
 */
public final class RepairTest {
    public static final boolean ACTIVE = System.getProperty("havingablast.test") != null;
    // where a hanging entity is fixed: block_pos since 1.21.5, TileX/Y/Z before
    static String tile(int x, int y, int z) {
        //? if >=1.21.5 {
        return "block_pos:[I;" + x + "," + y + "," + z + "]";
        //?} else {
        /*return "TileX:" + x + ",TileY:" + y + ",TileZ:" + z;
        *///?}
    }

    static final String[] ALL = {"defaults_off", "creeper", "creeper_charged", "tnt", "minecart", "only_own_toggle", "other_sources", "player_block", "mixed_chain", "big_crater", "decor",
        "crystal", "fireball", "bed", "anchor", "wither", "restart_a"};
    static final int Y = -60, DELAY = 2;
    static final List<String> queue = new ArrayList<>();
    static Scenario cur;
    static int passed, total, plot;
    static MinecraftServer server;

    private RepairTest() {
    }

    public static void start(MinecraftServer s, String what) {
        server = s;
        queue.clear();
        if (what.equals("all")) queue.addAll(List.of(ALL));
        else queue.addAll(List.of(what.split(",")));
        passed = total = 0;
        cmd("gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false", "gamerule spawn_monsters false",
            "gamerule mob_griefing true", "gamerule send_command_feedback false", "gamerule random_tick_speed 0", "difficulty easy", "time set 6000", "weather clear");
    }

    public static void tick(ServerLevel level) {
        if (!ACTIVE || server == null || level != server.overworld()) return;
        if (cur == null) {
            if (queue.isEmpty()) {
                if (total > 0 || passed > 0) {
                    System.out.println("[habtest] done " + passed + "/" + total);
                    total = passed = 0;
                    server = null;
                }
                return;
            }
            cur = new Scenario(queue.remove(0), plot++);
            cur.begin(level);
        }
        cur.t++;
        if (cur.step(level)) {
            cur.c("forceload remove " + (cur.x - 16) + " " + (cur.z - 16) + " " + (cur.x + 24) + " " + (cur.z + 24));
            cur = null;
        }
    }

    static void check(String scenario, String name, boolean ok, String detail) {
        total++;
        if (ok) passed++;
        System.out.println("[habtest] " + (ok ? "PASS " : "FAIL ") + scenario + "/" + name + "  " + detail);
    }

    static void cmd(String... cs) {
        for (String c : cs) server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), c);
    }

    /** One scenario on its own plot: x = 64 * plot, z = 0. The house's floor is at Y - 1 (on the flat world's grass). */
    static final class Scenario {
        final String name;
        int x;
        final int z;
        int t, phase;
        Map<Long, Object[]> snap;
        int chestItemsBefore;
        long waitUntil;

        Scenario(String name, int plot) {
            this.name = name;
            this.x = 64 * (plot + 1);
            this.z = 0;
        }

        final java.util.List<String> tntSpots = new java.util.ArrayList<>();
        /** The plot's ground level and dimension: the bed needs the Nether (high up, inside a netherrack shell). */
        int Y = RepairTest.Y;
        ServerLevel lv;
        String prefix = "";
        /** Spots of the thing that exploded (used up, stays empty). */
        final java.util.List<String> usedUp = new java.util.ArrayList<>();
        Map<java.util.UUID, CompoundTag> decorBefore;
        int capturedBefore, triggerT;

        void c(String... cs) {
            for (String c1 : cs) cmd(prefix + c1);
        }
        int restoredBefore;

        AABB box() {
            return name.equals("big_crater") ? new AABB(x - 12, Y - 10, z - 6, x + 18, Y + 12, z + 32) : new AABB(x - 6, Y - 4, z - 6, x + 14, Y + 10, z + 12);
        }

        void begin(ServerLevel level) {
            lv = level;
            if (name.equals("bed")) {
                lv = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
                Y = 105;
                prefix = "execute in minecraft:the_nether run ";
            }
            boolean creeperOn = !name.equals("defaults_off") && !name.equals("tnt") && !name.equals("minecart");
            boolean tntOn = name.equals("tnt") || name.equals("minecart") || name.equals("mixed_chain") || name.equals("other_sources") || name.equals("big_crater");
            if (name.equals("only_own_toggle")) {
                creeperOn = true;
                tntOn = false;
            }
            if (name.equals("restart_b")) {
                creeperOn = true;
            }
            boolean single = List.of("crystal", "fireball", "bed", "anchor", "wither").contains(name);
            for (Kind k : Kind.values()) k.set(false);
            Config.get().creeperRepair = creeperOn && !single;
            Config.get().tntRepair = tntOn;
            if (single) Kind.valueOf(name.toUpperCase(java.util.Locale.ROOT)).set(true);
            Config.get().repairDelaySeconds = name.equals("big_crater") ? 8 : DELAY;
            c("forceload add " + (x - 16) + " " + (z - 16) + " " + (x + 24) + " " + (z + 24));
            System.out.println("[habtest] scenario " + name + " at " + x + " " + z + " (creeper repair " + creeperOn + ", tnt repair " + tntOn + ")");
        }

        /** Returns true when the scenario is over. */
        boolean step(ServerLevel level) {
            if (name.equals("restart_b")) return restartB(level);
            level = lv;
            switch (phase) {
                case 0 -> {
                    // chunks load, then build
                    if (t < 30) return false;
                    // the Nether plot sits in a closed netherrack shell (8 thick below: a bed's power-5 blast cuts through 5), and the
                    // world's own mobs around it go, so their loot can't pass for blast drops (seen once: 131 items on one seed)
                    if (!prefix.isEmpty()) c(fill(x - 10, Y - 8, z - 10, x + 18, Y + 13, z + 16, "netherrack"),
                        "kill @e[type=!player,x=" + (x - 26) + ",y=" + (Y - 24) + ",z=" + (z - 26) + ",dx=60,dy=50,dz=60]");
                    c(fill(x - 8, Y, z - 8, x + 16, Y + 12, z + 14, "air"), fill(x - 8, Y - 3, z - 8, x + 16, Y - 2, z + 14, "dirt"),
                        fill(x - 8, Y - 1, z - 8, x + 16, Y - 1, z + 14, "grass_block"));
                    for (String c : House.commands(x, Y - 1, z, true)) c(c);
                    if (name.equals("mixed_chain")) c(set(x + 2, Y, z + 3, "tnt"), set(x + 4, Y, z + 4, "tnt"));
                    if (name.equals("big_crater")) {
                        // a hill of dirt on stone with 12 TNT buried in it, beside the house: one chain, one big crater
                        c(fill(x - 5, Y - 1, z + 7, x + 11, Y + 3, z + 7 + 16, "dirt"), fill(x - 5, Y - 5, z + 7, x + 11, Y - 2, z + 7 + 16, "stone"));
                        for (int i = 0; i < 12; i++) c(set(x - 3 + (i % 4) * 4, Y + (i / 4) - 1, z + 10 + (i % 3) * 5, "tnt"));
                        tntSpots.clear();
                        for (int i = 0; i < 12; i++) tntSpots.add((x - 3 + (i % 4) * 4) + "," + (Y + (i / 4) - 1) + "," + (z + 10 + (i % 3) * 5) + " ");
                    }
                    if (name.equals("anchor")) {
                        c(set(x + 3, Y, z - 2, "respawn_anchor[charges=4]"));
                        usedUp.add((x + 3) + "," + Y + "," + (z - 2) + " ");
                    }
                    if (name.equals("bed")) {
                        usedUp.add((x + 5) + "," + Y + "," + (z + 3) + " ");
                        usedUp.add((x + 5) + "," + Y + "," + (z + 2) + " ");
                    }
                    if (name.equals("decor")) {
                        c(summon("item_frame", x + 1, Y, z - 1, "{" + tile((x + 1), Y, (z - 1)) + ",Pos:[" + ((x + 1) + 0.5) + "d," + (Y + 0.5) + "d," + ((z - 1) + 0.5) + "d],Facing:2b,ItemRotation:3b,Item:{id:\"minecraft:diamond_sword\",count:1,components:{\"minecraft:custom_name\":\"Blade\"}}}"),
                            summon("glow_item_frame", x + 5, Y, z - 1, "{" + tile((x + 5), Y, (z - 1)) + ",Pos:[" + ((x + 5) + 0.5) + "d," + (Y + 0.5) + "d," + ((z - 1) + 0.5) + "d],Facing:2b,Item:{id:\"minecraft:clock\",count:1}}"),
                            summon("item_frame", x + 3, Y + 1, z + 4, "{" + tile((x + 3), (Y + 1), (z + 4)) + ",Pos:[" + ((x + 3) + 0.5) + "d," + ((Y + 1) + 0.5) + "d," + ((z + 4) + 0.5) + "d],Facing:2b,Item:{id:\"minecraft:apple\",count:1}}"),
                            summon("painting", x + 4, Y + 2, z - 1, "{" + tile((x + 4), (Y + 2), (z - 1)) + ",Pos:[" + ((x + 4) + 0.5) + "d," + ((Y + 2) + 0.5) + "d," + ((z - 1) + 0.5) + "d],facing:2b,variant:\"minecraft:kebab\"}"),
                            summon("armor_stand", x + 0.5, Y, z - 2.5, "{ShowArms:1b,equipment:{head:{id:\"minecraft:iron_helmet\",count:1},chest:{id:\"minecraft:golden_chestplate\",count:1},mainhand:{id:\"minecraft:stick\",count:1}}}"));
                    }
                    c("kill @e[type=item]");
                    phase = 1;
                    waitUntil = t + 10;
                }
                case 1 -> {
                    if (t < waitUntil) return false;
                    // plants that lost their soil to the build pop a tick after it: gone before the blast, not counted as its drops
                    c("kill @e[type=item]");
                    snap = snapshot(level);
                    chestItemsBefore = containerItems(level);
                    decorBefore = decor(level);
                    capturedBefore = io.github.profetgit.havingablast.repair.Decor.captured;
                    triggerT = t;
                    if (name.equals("restart_a")) save(level);
                    trigger(level);
                    phase = 2;
                    waitUntil = t + (name.equals("big_crater") ? 90 : 0) + (name.equals("creeper") || name.startsWith("creeper") || name.equals("defaults_off") || name.equals("only_own_toggle") || name.equals("decor") || name.equals("wither")
                        || name.equals("player_block") || name.equals("mixed_chain") || name.equals("restart_a") ? 45 : 25);
                }
                case 2 -> {
                    if (name.equals("wither") && t == triggerT + 3) c("damage @e[type=wither,limit=1] 1 minecraft:generic");
                    if (name.equals("wither") && t == triggerT + 32) c("tp @e[type=wither] " + x + " -250 " + z);
                    if (t < waitUntil) return false;
                    int broken = diff(level, snap).size();
                    int items = items(level);
                    boolean repairs = expectsRepair();
                    check(name, "blast", broken > 0, broken + " positions changed by the blast");
                    if (repairs) check(name, "no_drops", items == 0, items + " item entities after the blast (drops are held back for the repair)" + (items > 0 ? ": " + itemKinds(level) : ""));
                    else check(name, "vanilla_drops", items > 0, items + " item entities after the blast (not repaired: vanilla drops)");
                    if (name.equals("decor")) {
                        int gone = 0;
                        for (java.util.UUID u : decorBefore.keySet()) if (level.getEntity(u) == null) gone++;
                        int kept = io.github.profetgit.havingablast.repair.Decor.captured - capturedBefore;
                        check(name, "decor_held", gone > 0 && kept == gone, gone + " of " + decorBefore.size() + " frames, paintings and stands gone after the blast, " + kept + " kept for the repair");
                    }
                    if (name.equals("player_block")) {
                        // a player fills one of the holes with stone before the repair
                        BlockPos hole = firstHole(level);
                        Repair.playerAction(true);
                        level.setBlockAndUpdate(hole, Blocks.STONE.defaultBlockState());
                        Repair.playerAction(false);
                        playerHole = hole;
                        playerHoleBefore = (BlockState) snap.get(hole.asLong())[0];
                    }
                    if (name.equals("restart_a")) {
                        phase = 10;
                        waitUntil = t + DELAY * 20L + 8;
                        return false;
                    }
                    phase = 3;
                    waitUntil = t + (name.equals("big_crater") ? 8 * 20L + 900 : DELAY * 20L + 300);
                }
                case 3 -> {
                    int pending = Ledger.of(level).pending();
                    if (expectsRepair() && pending > 0 && t < waitUntil) return false;
                    // water that ran into the crater drains once the ground is back (flowing water recedes a level per 5 ticks)
                    if (expectsRepair() && settle < 0) settle = t + 60;
                    if (expectsRepair() && t < settle) return false;
                    if (!expectsRepair() && t < DELAY * 20L + 100 + 45) return false;
                    List<String> d = diff(level, snap);
                    if (expectsRepair()) {
                        if (name.equals("big_crater")) {
                            // the buried TNT went off: used up, those spots stay empty
                            d.removeIf(s2 -> tntSpots.stream().anyMatch(s2::startsWith));
                            check(name, "server_cost", Repair.tickNanosMax < 10_000_000L, String.format(java.util.Locale.ROOT,
                                "rebuild of %d blocks: worst tick %.2f ms, mean %.3f ms over %d ticks", Repair.restoredTotal - restoredBefore, Repair.tickNanosMax / 1e6,
                                Repair.tickCount == 0 ? 0 : Repair.tickNanosTotal / 1e6 / Repair.tickCount, Repair.tickCount));
                        }
                        if (!usedUp.isEmpty()) d.removeIf(s2 -> usedUp.stream().anyMatch(s2::startsWith));
                        if (name.equals("decor")) {
                            Map<java.util.UUID, CompoundTag> now = decor(level);
                            List<String> dd = new ArrayList<>();
                            for (Map.Entry<java.util.UUID, CompoundTag> e : decorBefore.entrySet()) {
                                CompoundTag n = now.get(e.getKey());
                                if (n == null) dd.add("missing " + io.github.profetgit.havingablast.repair.Nbt.stringOr(e.getValue(), "id", "?"));
                                else if (!n.equals(e.getValue())) dd.add("changed " + e.getValue() + " -> " + n);
                            }
                            if (now.size() != decorBefore.size()) dd.add(decorBefore.size() + " decorations before, " + now.size() + " after");
                            check(name, "decor_back", dd.isEmpty(), dd.isEmpty() ? decorBefore.size() + " frames, paintings and stands back, items and all" : String.join("; ", dd.subList(0, Math.min(3, dd.size()))));
                        }
                        if (name.equals("mixed_chain")) {
                            // the TNT the creeper set off is used up: those two spots stay empty
                            d.removeIf(s -> s.startsWith((x + 2) + "," + Y + "," + (z + 3) + " ") || s.startsWith((x + 4) + "," + Y + "," + (z + 4) + " "));
                        }
                        if (name.equals("player_block")) {
                            String key = playerHole.getX() + "," + playerHole.getY() + "," + playerHole.getZ() + " ";
                            boolean kept = level.getBlockState(playerHole).is(Blocks.STONE);
                            check(name, "player_block_kept", kept, "the stone placed in the hole at " + key.trim() + " is " + (kept ? "still there" : "gone"));
                            d.removeIf(s -> s.startsWith(key));
                            int dropped = items(level);
                            check(name, "skipped_drops_once", dropped == (playerHoleBefore.isAir() ? 0 : 1) || dropped > 0,
                                dropped + " item entities: the block that couldn't go back (" + playerHoleBefore + ") dropped instead");
                        }
                        check(name, "rebuilt", d.isEmpty(), d.isEmpty() ? "every block state and block entity matches the snapshot (pending " + pending + ")"
                            : d.size() + " differences, e.g. " + String.join("; ", d.subList(0, Math.min(4, d.size()))));
                        int after = containerItems(level);
                        check(name, "no_dupes", after == chestItemsBefore && (name.equals("player_block") || items(level) == 0),
                            "container items " + chestItemsBefore + " -> " + after + ", loose item entities " + items(level));
                    } else {
                        check(name, "not_rebuilt", !d.isEmpty() && Ledger.of(level).pending() == 0, d.size() + " positions still differ after the delay, ledger " + Ledger.of(level).pending());
                    }
                    c("kill @e[type=item]", "kill @e[type=creeper]", "kill @e[type=tnt_minecart]", "kill @e[type=item_frame]", "kill @e[type=glow_item_frame]",
                        "kill @e[type=painting]", "kill @e[type=armor_stand]", "kill @e[type=wither]", "kill @e[type=item]");
                    return true;
                }
                case 10 -> {
                    // restart_a: stop once the rebuild has begun; restart_b (after the reboot) checks the end result
                    if (t < waitUntil) return false;
                    int pending = Ledger.of(level).pending();
                    System.out.println("[habtest] restart-ready pending " + pending);
                    check(name, "mid_repair", pending > 0 && diff(level, snap).size() > 0, pending + " blocks still pending when the server stops");
                    return true;
                }
                default -> {
                    return true;
                }
            }
            return false;
        }

        long settle = -1;
        BlockPos playerHole;
        BlockState playerHoleBefore;

        boolean restartB(ServerLevel level) {
            if (phase == 0) {
                snap = load(level);
                if (snap == null) {
                    check(name, "snapshot", false, "no snapshot from restart_a");
                    return true;
                }
                // the house from restart_a (its x came with the snapshot); its pending blocks wait until these chunks load
                c("forceload add " + (x - 16) + " " + (z - 16) + " " + (x + 24) + " " + (z + 24));
                phase = 1;
                waitUntil = t + 400;
                return false;
            }
            if (Ledger.of(level).pending() > 0 && t < waitUntil) return false;
            List<String> d = diff(level, snap);
            check(name, "rebuilt_after_restart", d.isEmpty(), d.isEmpty() ? "the repair finished after the restart" : d.size() + " differences, e.g. " + String.join("; ", d.subList(0, Math.min(4, d.size()))));
            check(name, "no_dupes", items(level) == 0 && containerItems(level) == savedItems, "container items " + savedItems + " -> " + containerItems(level) + ", loose " + items(level));
            return true;
        }

        int savedItems;

        boolean expectsRepair() {
            return switch (name) {
                case "defaults_off", "only_own_toggle", "other_sources" -> false;
                default -> true;
            };
        }

        void trigger(ServerLevel level) {
            switch (name) {
                case "tnt", "only_own_toggle" -> c(summon("tnt", x + 3.5, Y, z - 1.5, "{fuse:5}"));
                case "minecart" -> c(summon("tnt_minecart", x + 3.5, Y + 1, z - 3.5, "{fuse:5}"));
                case "big_crater" -> {
                    Repair.tickNanosMax = Repair.tickNanosTotal = Repair.tickCount = 0;
                    restoredBefore = Repair.restoredTotal;
                    c(summon("tnt", x - 3.5, Y - 1, z + 10.5, "{fuse:5}"));
                }
                case "other_sources" -> c(summon("end_crystal", x + 3.5, Y, z - 1.5, "{ShowBottom:0b}"), "damage @e[type=end_crystal,limit=1,sort=nearest] 1",
                    summon("fireball", x + 1.5, Y + 8, z + 2.5, "{power:[0.0,-0.4,0.0],ExplosionPower:2}"));
                case "creeper_charged" -> c(summon("creeper", x + 3.5, Y, z - 1.5, "{ignited:1b,NoAI:1b,powered:1b}"));
                case "crystal" -> c(summon("end_crystal", x + 3.5, Y, z - 1.5, "{ShowBottom:0b}"), "damage @e[type=end_crystal,limit=1,sort=nearest] 1");
                case "fireball" -> c(summon("fireball", x + 2.5, Y + 8, z + 2.5, "{Motion:[0.0,-0.8,0.0],acceleration_power:0.05d,ExplosionPower:2b}"));
                case "wither" -> c(summon("wither_skull", x + 4.5, Y + 8, z + 2.5, "{Motion:[0.0,-0.8,0.0],acceleration_power:0.05d,dangerous:1b}"),
                    summon("wither", x + 1.5, Y, z + 1.5, "{}"));
                case "bed", "anchor" -> {
                    BlockPos at = name.equals("bed") ? new BlockPos(x + 5, Y, z + 3) : new BlockPos(x + 3, Y, z - 2);
                    net.minecraft.server.level.ServerPlayer p = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "HabTester"), net.minecraft.server.level.ClientInformation.createDefault());
                    p.setPos(at.getX() + 0.5, at.getY() + 1, at.getZ() - 1.5);
                    level.getBlockState(at).useWithoutItem(level, p, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at), net.minecraft.core.Direction.UP, at, false));
                }
                default -> c(summon("creeper", x + 3.5, Y, z - 1.5, "{ignited:1b,NoAI:1b}"));
            }
        }

        String summon(String type, double sx, double sy, double sz, String nbt) {
            return String.format(java.util.Locale.ROOT, "summon %s %.1f %.1f %.1f %s", type, sx, sy, sz, nbt);
        }

        /** Frames, paintings and stands on the plot, by UUID, with the fields that must come back unchanged. */
        Map<java.util.UUID, CompoundTag> decor(ServerLevel level) {
            Map<java.util.UUID, CompoundTag> m = new HashMap<>();
            for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null, box().inflate(2),
                e -> e instanceof net.minecraft.world.entity.decoration.HangingEntity || e instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
                if (e instanceof net.minecraft.world.entity.decoration.LeashFenceKnotEntity) continue;
                CompoundTag t = io.github.profetgit.havingablast.repair.EntityIo.save(level, e);
                for (String k : new String[] {"Motion", "fall_distance", "FallDistance", "Air", "OnGround", "Fire", "PortalCooldown", "HurtTime", "HurtByTimestamp", "DeathTime"}) t.remove(k);
                t.putString("id", net.minecraft.world.entity.EntityType.getKey(e.getType()).toString());
                // the attribute list comes out in hash order: same data, any order
                net.minecraft.nbt.ListTag l = io.github.profetgit.havingablast.repair.Nbt.list(t, "attributes");
                if (!l.isEmpty()) {
                    List<Tag> sorted = new ArrayList<>(l);
                    sorted.sort(java.util.Comparator.comparing(x -> io.github.profetgit.havingablast.repair.Nbt.stringOr((CompoundTag) x, "id", "")));
                    net.minecraft.nbt.ListTag nl = new net.minecraft.nbt.ListTag();
                    nl.addAll(sorted);
                    t.put("attributes", nl);
                }
                m.put(e.getUUID(), t);
            }
            return m;
        }

        BlockPos firstHole(ServerLevel level) {
            for (Map.Entry<Long, Object[]> e : snap.entrySet()) {
                BlockPos p = BlockPos.of(e.getKey());
                BlockState was = (BlockState) e.getValue()[0];
                if (was.is(Blocks.OAK_PLANKS) && level.getBlockState(p).isAir()) return p;
            }
            for (Map.Entry<Long, Object[]> e : snap.entrySet()) {
                BlockPos p = BlockPos.of(e.getKey());
                if (!((BlockState) e.getValue()[0]).isAir() && level.getBlockState(p).isAir()) return p;
            }
            return new BlockPos(x + 3, Y, z);
        }

        Map<Long, Object[]> snapshot(ServerLevel level) {
            Map<Long, Object[]> m = new HashMap<>();
            AABB b = box();
            for (int bx = (int) b.minX; bx < b.maxX; bx++) {
                for (int by = (int) b.minY; by < b.maxY; by++) {
                    for (int bz = (int) b.minZ; bz < b.maxZ; bz++) {
                        BlockPos p = new BlockPos(bx, by, bz);
                        BlockState s = level.getBlockState(p);
                        BlockEntity be = level.getBlockEntity(p);
                        m.put(p.asLong(), new Object[] {s, be == null ? null : be.saveWithFullMetadata(level.registryAccess())});
                    }
                }
            }
            return m;
        }

        List<String> diff(ServerLevel level, Map<Long, Object[]> snap) {
            List<String> out = new ArrayList<>();
            for (Map.Entry<Long, Object[]> e : snap.entrySet()) {
                BlockPos p = BlockPos.of(e.getKey());
                BlockState now = level.getBlockState(p);
                BlockState was = (BlockState) e.getValue()[0];
                String at = p.getX() + "," + p.getY() + "," + p.getZ() + " ";
                // water may settle differently (flowing levels): compare fluids by kind only
                if (now != was && !(now.getBlock() == Blocks.WATER && was.getBlock() == Blocks.WATER)) {
                    out.add(at + was + " -> " + now);
                    continue;
                }
                CompoundTag wasBe = (CompoundTag) e.getValue()[1];
                BlockEntity be = level.getBlockEntity(p);
                CompoundTag nowBe = be == null ? null : be.saveWithFullMetadata(level.registryAccess());
                if (wasBe == null ? nowBe != null : !wasBe.equals(nowBe)) out.add(at + "block entity " + wasBe + " -> " + nowBe);
            }
            return out;
        }

        String itemKinds(ServerLevel level) {
            Map<String, Integer> m = new java.util.TreeMap<>();
            for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, box().inflate(16))) m.merge(it.getItem().getItem().toString() + "@" + it.blockPosition().toShortString(), it.getItem().getCount(), Integer::sum);
            return m.toString();
        }

        int items(ServerLevel level) {
            int n = 0;
            for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, box().inflate(16))) n += it.getItem().getCount();
            return n;
        }

        int containerItems(ServerLevel level) {
            int n = 0;
            AABB b = box();
            for (int bx = (int) b.minX; bx < b.maxX; bx++) {
                for (int by = (int) b.minY; by < b.maxY; by++) {
                    for (int bz = (int) b.minZ; bz < b.maxZ; bz++) {
                        if (level.getBlockEntity(new BlockPos(bx, by, bz)) instanceof Container c) {
                            for (int i = 0; i < c.getContainerSize(); i++) n += c.getItem(i).getCount();
                        }
                    }
                }
            }
            return n;
        }

        Path file(ServerLevel level) {
            return level.getServer().getServerDirectory().resolve("habtest-snapshot.nbt");
        }

        void save(ServerLevel level) {
            CompoundTag root = new CompoundTag();
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (Map.Entry<Long, Object[]> e : snap.entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putLong("p", e.getKey());
                t.put("s", NbtUtils.writeBlockState((BlockState) e.getValue()[0]));
                if (e.getValue()[1] != null) t.put("be", (CompoundTag) e.getValue()[1]);
                list.add(t);
            }
            root.put("snap", list);
            root.putInt("items", chestItemsBefore);
            root.putInt("x", x);
            try {
                NbtIo.write(root, file(level));
            } catch (Exception ex) {
                System.out.println("[habtest] cannot save the snapshot: " + ex);
            }
        }

        Map<Long, Object[]> load(ServerLevel level) {
            try {
                CompoundTag root = NbtIo.read(file(level));
                if (root == null) return null;
                Map<Long, Object[]> m = new HashMap<>();
                for (Tag tag : io.github.profetgit.havingablast.repair.Nbt.list(root, "snap")) {
                    CompoundTag t = (CompoundTag) tag;
                    m.put(io.github.profetgit.havingablast.repair.Nbt.longOr(t, "p", 0), new Object[] {NbtUtils.readBlockState(BuiltInRegistries.BLOCK, io.github.profetgit.havingablast.repair.Nbt.compoundOrEmpty(t, "s")), io.github.profetgit.havingablast.repair.Nbt.compound(t, "be")});
                }
                savedItems = io.github.profetgit.havingablast.repair.Nbt.intOr(root, "items", 0);
                x = io.github.profetgit.havingablast.repair.Nbt.intOr(root, "x", x);
                return m;
            } catch (Exception ex) {
                System.out.println("[habtest] cannot load the snapshot: " + ex);
                return null;
            }
        }
    }

    static String fill(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
        return "fill " + x0 + " " + y0 + " " + z0 + " " + x1 + " " + y1 + " " + z1 + " minecraft:" + block;
    }

    static String set(int x, int y, int z, String block) {
        return "setblock " + x + " " + y + " " + z + " minecraft:" + block;
    }
}
