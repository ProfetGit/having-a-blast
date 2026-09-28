package io.github.profetgit.havingablast.demo;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.profetgit.havingablast.HavingABlast;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Dev-only scene director and check runner; does nothing unless the JVM is started with -Dhavingablast.demo=<dir>
 * (dev/demo/run.sh). In the flat demo world (surface y -10) it resets an arena, stages each scene (TNT, creepers,
 * houses), films it from a fixed armor-stand camera, saves every rendered frame to <dir>/<scene>/, checks what the
 * mod did, writes <dir>/results.json and quits.
 */
public final class Director {
    private static final String DIR = System.getProperty("havingablast.demo");
    public static final boolean ACTIVE = DIR != null;
    static final Path OUT = Path.of(ACTIVE ? DIR : ".");
    static final String[] SCENES = System.getProperty("havingablast.demo.scenes", "tnt1").split(",");
    static final boolean FRAMES = !"false".equals(System.getProperty("havingablast.demo.frames"));
    static final String CAM = System.getProperty("havingablast.demo.cam", "side");
    static final ExecutorService WRITER = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "havingablast-demo-writer");
        t.setDaemon(true);
        return t;
    });
    /** Ground surface of the demo world: grass at y -10, so things stand at y -9. */
    static final int G = -10;
    static int tick = -1, scene = -1, sceneTick, frame;
    static boolean recording, done, stopped, hudHidden;
    static final AtomicInteger pending = new AtomicInteger();
    static long drainUntil;
    static final List<String> timing = new ArrayList<>();
    static final List<String> results = new ArrayList<>();

    private Director() {
    }

    public static void onTick(Minecraft mc) {
        if (done) {
            if (!stopped && (pending.get() == 0 || System.currentTimeMillis() > drainUntil)) {
                stopped = true;
                System.out.println("[habdemo] done" + (pending.get() > 0 ? ", " + pending.get() + " frames not written" : ""));
                mc.stop();
            }
            return;
        }
        if (mc.level == null || mc.player == null) return;
        if (mc.getSingleplayerServer() == null) {
            multiplayer(mc);
            return;
        }
        tick++;
        if (!hudHidden) hideHud(mc);
        if (tick == 10) setup(mc);
        if (tick < 80) return;
        if (scene < 0 || sceneTick >= Scenes.length(SCENES[scene])) {
            if (recording) stopRecording();
            if (scene >= 0) Scenes.finish(mc, SCENES[scene]);
            if (++scene >= SCENES.length) {
                finish(mc);
                return;
            }
            sceneTick = -1;
            Scenes.start(mc, SCENES[scene]);
            System.out.println("[habdemo] scene " + SCENES[scene]);
        }
        sceneTick++;
        Entity cam = find(mc, ArmorStand.class);
        if (cam != null && mc.getCameraEntity() != cam) mc.setCameraEntity(cam);
        Scenes.tick(mc, SCENES[scene], sceneTick);
    }

    /** -Dhavingablast.demo.mp=<seconds>: on a server this client only watches (the server console stages the blasts). */
    static final int MP_SECONDS = Integer.getInteger("havingablast.demo.mp", 90);

    static void multiplayer(Minecraft mc) {
        tick++;
        if (!hudHidden) hideHud(mc);
        if (tick == 1) {
            ModTestHook.audit();
            System.out.println("[habdemo] joined a server: watching for " + MP_SECONDS + " s");
            scene = 0;
            record();
        }
        if (tick % 100 == 0) System.out.println("[habdemo] mp t" + tick + " blasts " + io.github.profetgit.havingablast.client.Blasts.totalBlasts
            + " captured " + io.github.profetgit.havingablast.client.Blasts.totalCaptured + " popped " + io.github.profetgit.havingablast.client.Blasts.totalPopped
            + " rebuilt " + io.github.profetgit.havingablast.client.Rebuild.rebuilt);
        if (tick >= MP_SECONDS * 20) {
            if (recording) stopRecording();
            int blasts = io.github.profetgit.havingablast.client.Blasts.totalBlasts, captured = io.github.profetgit.havingablast.client.Blasts.totalCaptured;
            check("blasts", blasts > 0, blasts + " explosions seen from the server");
            check("debris", captured > 0, captured + " blocks captured as debris");
            check("popped", io.github.profetgit.havingablast.client.Blasts.totalPopped == captured - io.github.profetgit.havingablast.client.Blasts.totalOverflow,
                io.github.profetgit.havingablast.client.Blasts.totalPopped + " popped");
            int rebuilt = io.github.profetgit.havingablast.client.Rebuild.rebuilt;
            System.out.println("[habdemo] rebuilt " + rebuilt);
            finish(mc);
        }
    }

    static void setup(Minecraft mc) {
        ModTestHook.audit();
        cmd(mc, ModTestHook.commands().toArray(String[]::new));
        String p = mc.player.getGameProfile().name();
        cmd(mc, "gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false", "gamerule spawn_monsters false",
            "gamerule max_block_modifications 10000000", "gamerule send_command_feedback false",
            "time set 6000", "weather clear", "difficulty easy", "gamemode creative " + p, "kill @e[type=!player]",
            "tp " + p + " 0.5 " + (G + 1) + " -12.5 0 0",
            "summon armor_stand 0 0 0 {Invisible:1b,Marker:1b,NoGravity:1b,CustomName:\"cam\"}");
    }

    static void record() {
        if (recording) return;
        recording = true;
        frame = 0;
        timing.clear();
    }

    static void stopRecording() {
        recording = false;
        Path dir = OUT.resolve(SCENES[scene]);
        List<String> lines = new ArrayList<>(timing);
        lines.add(0, "# frame nanos scene_tick partial " + Scenes.timingHeader());
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve("timing.txt"), lines);
        } catch (IOException e) {
            System.out.println("[habdemo] timing write failed: " + e);
        }
    }

    /** Called after every rendered frame. */
    public static void onFrame(Minecraft mc) {
        if (!recording || done) return;
        Path dir = OUT.resolve(SCENES[scene]);
        int n = frame++;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        timing.add(n + " " + System.nanoTime() + " " + sceneTick + " " + partial + " " + Scenes.timingLine(mc, partial));
        if (!FRAMES) return;
        Path out = dir.resolve(String.format("f%05d.png", n));
        pending.incrementAndGet();
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), (NativeImage img) -> WRITER.execute(() -> {
            try (img) {
                Files.createDirectories(dir);
                img.writeToFile(out);
            } catch (Exception e) {
                System.out.println("[habdemo] write failed " + out + ": " + e);
            } finally {
                pending.decrementAndGet();
            }
        }));
    }

    static void check(String name, boolean pass, String detail) {
        String id = SCENES[scene] + "/" + name;
        results.add(String.format("{\"name\":\"%s\",\"pass\":%b,\"detail\":\"%s\"}", id, pass, detail.replace("\"", "'")));
        System.out.println("[habdemo] " + (pass ? "PASS " : "FAIL ") + id + "  " + detail);
    }

    static void finish(Minecraft mc) {
        done = true;
        try {
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve("results.json"), "{\"loader\":\"" + HavingABlast.loader() + "\",\"results\":[\n"
                + String.join(",\n", results) + "\n]}\n");
        } catch (IOException e) {
            System.out.println("[habdemo] results write failed: " + e);
        }
        drainUntil = System.currentTimeMillis() + 120_000;
    }

    static <T extends Entity> T find(Minecraft mc, Class<T> type) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (type.isInstance(e) && e.isAlive()) return type.cast(e);
        }
        return null;
    }

    /** The real player right-clicks a block with an empty hand (a bed in the Nether, a charged respawn anchor). */
    static void use(Minecraft mc, net.minecraft.core.BlockPos pos) {
        var r = mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
        System.out.println("[habdemo] use " + pos.toShortString() + " " + mc.level.getBlockState(pos) + " -> " + r + " (player at " + mc.player.blockPosition().toShortString() + ")");
    }

    static void cmd(Minecraft mc, String... commands) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            for (String c : commands) server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c);
        });
    }

    static String f(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    static void hideHud(Minecraft mc) {
        try {
            Field f = mc.gui.hud.getClass().getDeclaredField("isHidden");
            f.setAccessible(true);
            f.setBoolean(mc.gui.hud, true);
        } catch (ReflectiveOperationException e) {
            System.out.println("[habdemo] cannot hide HUD: " + e);
        }
        hudHidden = true;
    }
}
