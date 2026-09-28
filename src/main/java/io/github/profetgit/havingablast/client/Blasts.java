package io.github.profetgit.havingablast.client;

import io.github.profetgit.havingablast.mixin.client.BlockModelRenderStateAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * Client-side explosion capture and debris simulation. The server's explode packet has no block list, and the destroyed
 * blocks arrive right after it as ordinary block updates (same tick, probed on 26.3). So the packet opens a capture
 * window; every update inside the blast's reach that turns a real block into air or fluid becomes debris.
 */
public final class Blasts {
    /**
     * The capture window: it closes after 2 client ticks without a new captured update for the blast (a server tick's
     * block updates arrive together at the end of that tick; a huge burst is decoded over several client ticks) or 20
     * ticks after the packet if nothing came. Counting from the packet alone lost blocks under load: in a 100-TNT chain
     * one server tick holds dozens of explosions, their packets go out at once, and that tick's block updates only when
     * the whole tick is done.
     */
    static final int WINDOW_AFTER_FIRST = 2, WINDOW_MAX = 20;
    static final float HOLD = 2.0f, RIPPLE = 3.0f, REST_WAIT = 6.0f;
    static final int MAX_LIVE = 2000, MAX_PER_BLAST = 400;

    static int ticks;
    static boolean warm;
    static final List<Blast> blasts = new ArrayList<>();
    static final List<Debris> live = new ArrayList<>();
    static final Map<BlockState, Model> MODELS = new IdentityHashMap<>();
    static BlockModelResolver resolver;
    static final RandomSource RNG = RandomSource.create();
    /** Dev counters for the demo checks. */
    public static int totalCaptured, totalPopped, totalBlasts, totalOverflow;

    private Blasts() {
    }

    /** A block's resolved model, cached per state: the parts and render type for the allocation-free fast path. */
    static final class Model {
        final BlockModelRenderState rs = new BlockModelRenderState();
        List<BlockStateModelPart> parts;
        RenderType type;
        int[] tints;
        boolean fast;
        int emission;
    }

    /** Positions with a break event in the last ticks (packet order: the event comes before the end-of-tick block update). */
    static final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap BROKE = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

    public static void onBreakEvent(BlockPos pos) {
        BROKE.put(pos.asLong(), ticks);
    }

    /** The push line of a piston that just moved (its block event comes before the block updates): 13 cells out. */
    public static void onPiston(BlockPos base, int direction) {
        net.minecraft.core.Direction d = net.minecraft.core.Direction.from3DDataValue(direction & 7);
        for (int i = 1; i <= 13; i++) BROKE_PISTON.put(base.relative(d, i).asLong(), ticks);
    }

    static final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap BROKE_PISTON = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

    public static long liveCount() {
        return live.stream().filter(d -> !d.dead).count();
    }

    public static float clock(float partial) {
        return ticks + partial;
    }

    static Model model(BlockState state, ClientLevel level, BlockPos at) {
        Model m = MODELS.get(state);
        if (m != null) return m;
        Minecraft mc = Minecraft.getInstance();
        if (resolver == null) resolver = new BlockModelResolver(mc.getModelManager());
        m = new Model();
        resolver.update(m.rs, state, BlockDisplayContext.create());
        BlockModelRenderStateAccessor a = (BlockModelRenderStateAccessor) m.rs;
        // an ObjectArrayList like vanilla's own submit passes: with Sodium an immutable List.copyOf drew nothing
        m.parts = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(a.havingablast$parts() == null ? List.of() : a.havingablast$parts());
        // with Fabric API the resolver puts even vanilla models into its own mesh and leaves no parts, and that mesh
        // never showed from this pass without Sodium (invisible debris, 0.2.3): take the model's parts directly
        if (m.parts.isEmpty() && !m.rs.isEmpty() && a.havingablast$special() == null) {
            mc.getModelManager().getBlockStateModelSet().get(state).collectParts(net.minecraft.util.RandomSource.create(42), m.parts);
        }
        m.type = a.havingablast$renderType();
        m.fast = a.havingablast$special() == null && a.havingablast$transformation() == null && m.type != null;
        // the model's own tint layers (what vanilla's submit passes); world-dependent ones (grass, leaves) are recoloured
        // for where the block was, always opaque: under Sodium a tint source's colour came without alpha, and the
        // cutout pass then dropped the whole block
        m.tints = m.rs.tintLayers().toIntArray();
        List<BlockTintSource> sources = mc.getBlockColors().getTintSources(state);
        for (int i = 0; i < m.tints.length && i < sources.size(); i++) {
            BlockTintSource src = sources.get(i);
            if (src != null && level != null && at != null) m.tints[i] = src.colorInWorld(state, level, at) | 0xFF000000;
        }
        m.emission = state.getLightEmission();
        MODELS.put(state, m);
        return m;
    }

    static BlockState flashState() {
        return Blocks.CONCRETE.white().defaultBlockState();
    }

    public static void clearModels() {
        MODELS.clear();
        resolver = null;
    }

    /** True while handleExplosion runs for a blast this mod draws: vanilla's sprite and block spray are skipped. */
    public static boolean replacing;

    public static void onExplosionPacket(Vec3 center, float radius, int count, net.minecraft.core.particles.ParticleOptions particle) {
        ClientLevel level = Minecraft.getInstance().level;
        var type = particle.getType();
        boolean explosion = BlastClient.visuals() && (type == net.minecraft.core.particles.ParticleTypes.EXPLOSION || type == net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER);
        // with Explosive Enhancement its puff is drawn (never two puffs); the debris, pops and repair are still this mod's
        replacing = explosion && !BlastClient.EXPLOSIVE_ENHANCEMENT;
        if (!explosion || level == null) return;
        Blast b = new Blast(center, radius, ticks, RNG.nextLong());
        // before this tick's block updates: the shockwave finds the ground the blast is about to dig away
        if (replacing) b.fx = new Boom.Params(b, level);
        var camEntity = Minecraft.getInstance().getCameraEntity();
        b.farFromCamera = camEntity != null && camEntity.position().distanceToSqr(center) > 48 * 48;
        blasts.add(b);
        totalBlasts++;
        Drops.claim(b);
    }

    /** Called before a server block update is applied: old is still in the level. */
    public static void onBlockUpdate(ClientLevel level, BlockPos pos, BlockState old, BlockState now) {
        if (blasts.isEmpty()) return;
        long t0 = System.nanoTime();
        capture(level, pos, old, now);
        DebrisRenderer.spentNanos += System.nanoTime() - t0;
    }

    static void capture(ClientLevel level, BlockPos pos, BlockState old, BlockState now) {
        // a real block (not TNT: a blast primes it; not a piston part; not a fluid) turned into air or fluid ...
        if (blasts.isEmpty() || !io.github.profetgit.havingablast.dev.BlastCount.counts(old, now)) return;
        // ... and not sand or gravel that started falling (its entity arrived just before this update), nor a block a piston moved
        if (Drops.justFell(pos)) return;
        if (!BROKE_PISTON.isEmpty() && ticks - BROKE_PISTON.getOrDefault(pos.asLong(), -100) <= 3) return;
        Blast best = null;
        double bestD = Double.MAX_VALUE;
        for (Blast b : blasts) {
            if (b.closed) continue;
            double d = b.center.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (d <= b.reach * b.reach && d < bestD) {
                best = b;
                bestD = d;
            }
        }
        if (best == null) return;
        long key = pos.asLong();
        if (!BROKE.isEmpty() && ticks - BROKE.getOrDefault(key, -100) <= 2) {
            // broke with a break event: debris only beside a block the blast removed (decided at the next bake)
            best.marked.add(new Object[] {pos.immutable(), old, bestD});
            best.lastCapture = ticks;
            return;
        }
        best.removed.add(key);
        best.lastCapture = ticks;
        addDebris(level, best, pos, old, bestD);
    }

    static void addDebris(ClientLevel level, Blast best, BlockPos pos, BlockState old, double bestD) {
        if (Probe.POSITIONS) System.out.println("[habprobe] CAPTURE " + pos.toShortString() + " " + old);
        best.captured++;
        totalCaptured++;
        // far away (48+ blocks from the camera) a third of the pieces fly, the rest just crumble
        boolean far = best.farFromCamera && (best.captured % 3 != 0);
        if (far || best.debris.size() >= MAX_PER_BLAST || live.size() >= io.github.profetgit.havingablast.Config.get().maxDebris) {
            best.overflow++;
            totalOverflow++;
            Fx.crumbs(level, old, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4);
            return;
        }
        Rebuild.remember(pos, old);
        Debris d = new Debris();
        d.state = old;
        d.origin = pos.immutable();
        d.blast = best;
        d.model = model(old, level, pos);
        d.reach = (float) Math.min(1, Math.sqrt(bestD) / best.reach);
        if (best.rng == null) best.rng = RandomSource.create(best.seed);
        d.size = BlastClient.debrisSize(best.rng);
        // the ripple: nearest the centre launches first
        d.launch = HOLD + RIPPLE * d.reach + best.rng.nextFloat() * 0.6f;
        scorch(level, d, best);
        best.debris.add(d);
        best.sorted = false;
    }

    public static void tick(Minecraft mc) {
        long t0 = System.nanoTime();
        try {
            tickTimed(mc);
        } finally {
            DebrisRenderer.spentNanos += System.nanoTime() - t0;
        }
    }

    static void tickTimed(Minecraft mc) {
        if (mc.level == null) {
            blasts.clear();
            live.clear();
            Drops.clear();
            Rebuild.tick(mc);
            return;
        }
        Rebuild.tick(mc);
        if (mc.isPaused()) return;
        if (!warm && BlastClient.visuals()) {
            // load the sprite sheet and the model resolver now, not in the frame of the first blast (a 27 ms hitch)
            warm = true;
            Sprites.type();
            if (resolver == null) resolver = new BlockModelResolver(mc.getModelManager());
            model(Blocks.DIRT.defaultBlockState(), mc.level, null);
        }
        ticks++;
        for (Blast b : blasts) {
            if (!b.closed && (b.lastCapture >= 0 && ticks - b.lastCapture >= WINDOW_AFTER_FIRST || ticks - b.openedTick >= WINDOW_MAX)) b.closed = true;
        }
        bakePending(mc.level, Float.NaN);
        for (Blast b : blasts) {
            if (b.closed && !b.scheduled && b.baked == b.debris.size()) schedule(mc.level, b);
        }
        live.removeIf(d -> d.dead);
        Drops.tick();
        if (!BROKE.isEmpty()) BROKE.values().removeIf(t -> ticks - t > 2);
        if (!BROKE_PISTON.isEmpty()) BROKE_PISTON.values().removeIf(t -> ticks - t > 3);
        blasts.removeIf(b -> {
            float t = Float.isNaN(b.t0) ? 0 : ticks - b.t0;
            boolean over = b.closed && (b.debris.stream().allMatch(d -> d.dead && t - d.popT > 11) && Boom.done(b, t) || ticks - b.openedTick > 400);
            if (over) Drops.revealAll(b.items);
            return over;
        });
    }

    /** Render-thread time the bakes may take per frame; the rest waits for the next frame (the pieces hold in place). */
    static final long BAKE_BUDGET_NANOS = 1_500_000;

    /**
     * Bakes pending flights, earliest launch first, against the world with the updates received so far, within the
     * frame's budget. A piece baked after its launch time launches from where it sits, a moment late, never jumping.
     */
    static void bakePending(ClientLevel level, float now) {
        long deadline = System.nanoTime() + BAKE_BUDGET_NANOS;
        for (Blast b : blasts) {
            if (!b.marked.isEmpty()) {
                // attached blocks that popped off beside the blast fly with it; a lone break (mining) stays vanilla
                java.util.Iterator<Object[]> it = b.marked.iterator();
                while (it.hasNext()) {
                    Object[] m = it.next();
                    BlockPos p = (BlockPos) m[0];
                    if (io.github.profetgit.havingablast.dev.BlastCount.besideAny(p.asLong(), b.removed)) {
                        it.remove();
                        addDebris(level, b, p, (BlockState) m[1], (Double) m[2]);
                    } else if (b.closed) {
                        it.remove();
                    }
                }
            }
            if (b.baked == b.debris.size()) continue;
            if (b.physics == null) b.physics = new Physics(level);
            b.physics.cells.clear();
            if (!b.sorted) {
                List<Debris> rest = b.debris.subList(b.baked, b.debris.size());
                rest.sort(Comparator.comparingDouble(d -> d.launch));
                b.sorted = true;
            }
            while (b.baked < b.debris.size()) {
                if (System.nanoTime() > deadline) return;
                Debris d = b.debris.get(b.baked++);
                if (!Float.isNaN(b.t0) && !Float.isNaN(now)) d.launch = Math.max(d.launch, now - b.t0);
                b.physics.bake(d, b, b.rng);
                d.position(d.restT, b.physics.q);
                Rebuild.rested(d, b.physics.q[0], b.physics.q[1], b.physics.q[2]);
                maxJump = Math.max(maxJump, d.maxJump());
                live.add(d);
            }
        }
    }

    /** Largest step between two 0.05-tick samples of any baked debris path (the demo checks it: no teleports). */
    public static double maxJump;
    public static int scorched, keptCover;

    /**
     * A blast burns the cover off the ground: grass, podzol, mycelium, paths and farmland fly as the dirt they drop
     * (the pop reveals a dirt item). Pieces thrown from the crater's rim (outer third of the blast radius) keep their
     * cover in about half the cases, so the blast still reads as torn-up lawn.
     */
    static void scorch(ClientLevel level, Debris d, Blast b) {
        net.minecraft.world.level.block.Block k = d.state.getBlock();
        if (k != Blocks.GRASS_BLOCK && k != Blocks.PODZOL && k != Blocks.MYCELIUM && k != Blocks.DIRT_PATH && k != Blocks.FARMLAND) return;
        double dist = Math.sqrt(b.center.distanceToSqr(d.origin.getX() + 0.5, d.origin.getY() + 0.5, d.origin.getZ() + 0.5));
        if (dist > b.radius * 0.67 && b.rng.nextFloat() < 0.5f) {
            keptCover++;
            return;
        }
        scorched++;
        d.state = Blocks.DIRT.defaultBlockState();
        d.model = model(d.state, level, d.origin);
    }

    /** The capture window is over and every flight is baked: schedule the pops, one after another, speeding up. */
    static void schedule(ClientLevel level, Blast b) {
        b.scheduled = true;
        Rim.collect(level, b);
        if (b.debris.isEmpty()) {
            Drops.revealAll(b.items);
            return;
        }
        List<Debris> order = new ArrayList<>(b.debris);
        order.sort(Comparator.comparingDouble(d -> d.launch + d.restT));
        float prev = -1;
        for (int i = 0; i < order.size(); i++) {
            Debris d = order.get(i);
            float gap = Math.max(0.15f, 1.4f * (float) Math.pow(0.85, i));
            float at = d.launch + d.restT + REST_WAIT;
            d.popIndex = i;
            if (d.lost) {
                d.popT = d.launch + d.restT;
                continue;
            }
            d.popT = prev < 0 ? at : Math.max(at, prev + gap);
            prev = d.popT;
        }
        Drops.assign(level, b);
    }

    /** Nearest of the 24 axis-aligned orientations to q. */
    static Quaternionf snap(Quaternionf q, Quaternionf out) {
        Matrix3f m = new Matrix3f().rotation(q);
        Matrix3f s = new Matrix3f();
        boolean[] used = new boolean[3];
        for (int c = 0; c < 3; c++) {
            float x = c == 0 ? m.m00 : c == 1 ? m.m10 : m.m20;
            float y = c == 0 ? m.m01 : c == 1 ? m.m11 : m.m21;
            float z = c == 0 ? m.m02 : c == 1 ? m.m12 : m.m22;
            float[] v = {x, y, z};
            int best = -1;
            for (int k = 0; k < 3; k++) if (!used[k] && (best < 0 || Math.abs(v[k]) > Math.abs(v[best]))) best = k;
            used[best] = true;
            float sign = Math.signum(v[best]) == 0 ? 1 : Math.signum(v[best]);
            float[] col = new float[3];
            col[best] = sign;
            if (c == 0) { s.m00 = col[0]; s.m01 = col[1]; s.m02 = col[2]; }
            else if (c == 1) { s.m10 = col[0]; s.m11 = col[1]; s.m12 = col[2]; }
            else { s.m20 = col[0]; s.m21 = col[1]; s.m22 = col[2]; }
        }
        if (s.determinant() < 0) { s.m20 = -s.m20; s.m21 = -s.m21; s.m22 = -s.m22; }
        return out.setFromNormalized(s);
    }

    /**
     * Ballistic flight against the client's world, collided as a box (the piece's size) with the blocks' collision
     * shapes. Each step that ends inside something is bisected to the last free instant; the axis that hits decides
     * floor (bounce or rest), ceiling or wall, and the next segment starts exactly there, so a path never jumps.
     * Debris never stack on each other: landing on a piece that only lands later made pieces hang on nothing.
     */
    static final class Physics {
        final ClientLevel level;
        final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        final double[] q = new double[3];
        /** Collision box per cell for this bake session (cleared every frame: other blasts keep changing the world). */
        final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<net.minecraft.world.phys.AABB> cells = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        static final net.minecraft.world.phys.AABB NONE = new net.minecraft.world.phys.AABB(0, 0, 0, 0, 0, 0);

        Physics(ClientLevel level) {
            this.level = level;
        }

        net.minecraft.world.phys.AABB cell(int bx, int by, int bz) {
            long key = BlockPos.asLong(bx, by, bz);
            net.minecraft.world.phys.AABB bb = cells.get(key);
            if (bb == null) {
                mp.set(bx, by, bz);
                BlockState st = level.getBlockState(mp);
                VoxelShape shape = st.isAir() ? null : st.getCollisionShape(level, mp);
                bb = shape == null || shape.isEmpty() ? NONE : shape.bounds();
                cells.put(key, bb);
            }
            return bb;
        }

        /** Does a box of half size h centred at (x,y,z) overlap any block's collision shape? */
        boolean collides(double x, double y, double z, double h) {
            double x0 = x - h, x1 = x + h, y0 = y - h, y1 = y + h, z0 = z - h, z1 = z + h;
            for (int bx = (int) Math.floor(x0); bx <= (int) Math.floor(x1); bx++) {
                for (int by = (int) Math.floor(y0); by <= (int) Math.floor(y1); by++) {
                    for (int bz = (int) Math.floor(z0); bz <= (int) Math.floor(z1); bz++) {
                        net.minecraft.world.phys.AABB bb = cell(bx, by, bz);
                        if (bb == NONE) continue;
                        if (bx + bb.minX < x1 && bx + bb.maxX > x0 && by + bb.minY < y1 && by + bb.maxY > y0 && bz + bb.minZ < z1 && bz + bb.maxZ > z0) return true;
                    }
                }
            }
            return false;
        }

        void at(double sx, double sy, double sz, double vx, double vy, double vz, double u, double[] out) {
            out[0] = sx + vx * u;
            out[1] = sy + vy * u - 0.5 * Debris.G * u * u;
            out[2] = sz + vz * u;
        }

        void bake(Debris d, Blast b, RandomSource r) {
            double half = d.size / 2, h = half * 0.96;
            double cx = d.origin.getX() + 0.5, cy = d.origin.getY() + 0.5, cz = d.origin.getZ() + 0.5;
            double dx = cx - b.center.x, dy = cy - b.center.y, dz = cz - b.center.z;
            double hd = Math.sqrt(dx * dx + dz * dz);
            double hx, hz;
            if (hd < 0.05) {
                double a = r.nextDouble() * Math.PI * 2;
                hx = Math.cos(a);
                hz = Math.sin(a);
            } else {
                hx = dx / hd;
                hz = dz / hd;
            }
            // radius 4 (TNT): a strong upward kick (apex about 3 blocks) and a moderate fan-out (landing 2 to 7 blocks out)
            double power = BlastClient.power() * Math.max(0.7, Math.min(1.35, 0.7 + b.radius / 10.0));
            double vy = power * (1.0 - 0.35 * d.reach) * (0.8 + 0.35 * r.nextDouble());
            double bias = 0.45 + 0.55 * Math.min(1, hd / Math.max(0.5, Math.abs(dy) + hd));
            double vh = power * (0.22 + 0.36 * r.nextDouble()) * bias * BlastClient.spread();
            double vx = hx * vh + (r.nextDouble() - 0.5) * 0.05, vz = hz * vh + (r.nextDouble() - 0.5) * 0.05;
            d.launchDir.set((float) vx, (float) vy, (float) vz).normalize();
            // (bake is also where the rest of the timeline starts: segment times are relative to d.launch)
            d.launchSpeed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            d.axis.set(r.nextFloat() - 0.5f, r.nextFloat() - 0.5f, r.nextFloat() - 0.5f).normalize();
            d.spin = (0.12f + 0.2f * r.nextFloat()) * (float) (0.6 + d.launchSpeed);
            double sx = cx, sy = cy, sz = cz, svx = vx, svy = vy, svz = vz;
            float segStart = 0, t = 0;
            final float dt = 0.5f;
            d.addSegment(0, sx, sy, sz, svx, svy, svz, true);
            // a piece that starts overlapping something (a partly blown neighbour) flies free until it is clear
            boolean ghost = collides(sx, sy, sz, h);
            int bounces = 0;
            double lowest = Math.max(level.getMinY() - 2, cy - 40);
            for (int step = 0; step < 300 && d.nseg < Debris.MAX_SEG - 1; step++) {
                float t1 = t + dt;
                at(sx, sy, sz, svx, svy, svz, t1 - segStart, q);
                if (q[1] < lowest) {
                    // off an edge into the void or a deep hole: it falls out of sight and ends there, quietly
                    d.lost = true;
                    break;
                }
                boolean hit = collides(q[0], q[1], q[2], h);
                if (ghost) {
                    if (!hit) ghost = false;
                    t = t1;
                    continue;
                }
                if (!hit) {
                    t = t1;
                    continue;
                }
                // last free instant
                float lo = t, hi = t1;
                for (int i = 0; i < 12; i++) {
                    float m = (lo + hi) / 2;
                    at(sx, sy, sz, svx, svy, svz, m - segStart, q);
                    if (collides(q[0], q[1], q[2], h)) hi = m;
                    else lo = m;
                }
                float tc = lo;
                double u = tc - segStart;
                at(sx, sy, sz, svx, svy, svz, u, q);
                double px = q[0], py = q[1], pz = q[2];
                double cvx = svx, cvy = svy - Debris.G * u, cvz = svz;
                double eps = 0.02;
                if (collides(px, py + Math.signum(cvy) * eps, pz, h)) {
                    if (cvy < 0) {
                        // floor
                        float k = (float) Math.min(1, -cvy / 0.7);
                        d.contact(tc, k);
                        double bvy = -cvy * 0.36;
                        bounces++;
                        if (bounces > 2 || bvy < 0.09) {
                            d.addSegment(tc, px, py + 0.002, pz, 0, 0, 0, false);
                            d.restT = tc;
                            snap(new Quaternionf().rotateAxis(d.spin * d.firstContact, d.axis.x, d.axis.y, d.axis.z), d.restRot);
                            return;
                        }
                        cvx *= 0.55;
                        cvz *= 0.55;
                        cvy = bvy;
                    } else {
                        cvy = -cvy * 0.2;
                    }
                } else {
                    if (collides(px + Math.signum(cvx) * eps, py, pz, h)) cvx = -cvx * 0.4;
                    if (collides(px, py, pz + Math.signum(cvz) * eps, h)) cvz = -cvz * 0.4;
                    if (!collides(px + Math.signum(cvx) * eps, py, pz, h) && !collides(px, py, pz + Math.signum(cvz) * eps, h) && Math.abs(cvx) + Math.abs(cvz) < 1e-3) cvy = Math.min(cvy, 0);
                }
                sx = px;
                sy = py;
                sz = pz;
                svx = cvx;
                svy = cvy;
                svz = cvz;
                segStart = tc;
                t = tc;
                d.addSegment(tc, sx, sy, sz, svx, svy, svz, true);
            }
            // out of segments or time (a very long fall): rest where it is
            at(sx, sy, sz, svx, svy, svz, t - segStart, q);
            d.addSegment(t, q[0], q[1], q[2], 0, 0, 0, false);
            d.restT = t;
            if (d.firstContact == Float.MAX_VALUE) d.firstContact = t;
            snap(new Quaternionf().rotateAxis(d.spin * d.firstContact, d.axis.x, d.axis.y, d.axis.z), d.restRot);
        }
    }
}
