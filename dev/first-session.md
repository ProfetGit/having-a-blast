Start a new mod, **Having a Blast**, in `~/Projects/Minecraft Datapacks/mods/HavingABlast/` (name picked by the user on 2026-09-27; Modrinth slug `having-a-blast` was free then, CurseForge not checked; mod id `havingablast`, package `io.github.profetgit.havingablast`, like `tidypockets`/`mobreactions`). It turns TNT, creeper and every other block-breaking explosion into a cartoon: the blasted blocks burst out as real-looking blocks, fly on arcs, squash and bounce where they land, and pop into their drops one after another. Toggleable features, **off by default**, make **creeper** and **TNT** explosions repair themselves with an animation of their own (see "Auto repair" below). The bar is Actions & Stuff quality, smooth at 60 fps and above. It is also meant to carry a YouTube video (1 TNT → 10 → 100 → 1000, vanilla vs mod; a creeper wrecks the house, then the house builds itself back), so keep showcase-ready test scenes from day one.

## Read first
- Workspace `CLAUDE.md`: the Mob Reactions section (Stonecutter setup, the demo director and `run.sh`, the off-screen rule, house style, review tools), Tidy Pockets (build and self-test), Timber internals (the ring ripple, pops, slam effects), Veinminer's chain animation (flying drops, visible caps), "Seeing the real client", and the particle notes (`small_gust` reads clean, `poof`/`cloud` smear the view).
- Memory: `mob-reactions-project`, `animation-pacing`, `always-bump-version`, `polish-skill`, `client-capture`, `showcase-recorder`.
- Skills: `polish` (lablib lab, per-frame probe, curves, perf); later `pack-icon-animation`, `pack-showcase`, `youtube-video`.
- Code to copy from `MobReactions/`: `stonecutter.gradle.kts`, `build.{fabric,forge,neoforge}.gradle.kts`, `settings.gradle.kts`, `versions/`, `gradle.properties`, `LICENSE`, the inert-unless-`-D` demo pattern (`demo/Director`, `mixin/MinecraftMixin`) and `dev/demo/{run.sh,matrix.sh,gif.py,grid.py,sheet.py,timeline.py}`.

## Decided
- **One mod jar per loader, both sides optional**: Fabric + NeoForge + Forge × 26.2 + 26.3 through Stonecutter (6 jars; Quilt runs the Fabric jar), like Mob Reactions.
  - The **cartoon visuals are client-side**: the animation is evaluated every rendered frame instead of 20 Hz keys, every explosion source comes for free, and they work on any server, vanilla included.
  - The **auto repair is server logic**: it runs wherever the mod is on the logical server, so always in singleplayer, and in multiplayer only when the server has the mod. A server with the mod and a client without it must both work (vanilla clients simply see the blocks come back).
  - So the jar must load on a dedicated server without touching client classes (split client entry points and mixins by side), and a client must join a vanilla server without errors.
- No new dependencies without asking (Mob Reactions ships without Fabric API).
- Author Profet, All Rights Reserved with explicit permissions (copy Mob Reactions' `LICENSE`). Nothing leaves the machine (GitHub, Modrinth, CurseForge) without asking.

## Checked on the jars (2026-09-27)
- `ClientboundExplodePacket` has `center`, `radius`, `blockCount`, `playerKnockback`, `explosionParticle`, `explosionSound`, `blockParticles` (`WeightedList<ExplosionParticleInfo>`), and on 26.3 also `playSound` (26.2 lacks it: a Stonecutter difference). **There is no block list.**
- `ServerLevel.explode` sends that packet straight to each nearby player during the explosion. The destroyed blocks reach the client later as ordinary block updates: `ClientPacketListener.handleBlockUpdate` / `handleChunkBlocksUpdate` → `ClientLevel.setServerVerifiedBlockState`. The order within a tick is **unverified**: probe it first.
- Vanilla's `ClientExplosionTracker` (`ClientLevel.trackExplosionEffects` → `track`, ticked by `ClientLevel.tick`) only sprays `blockParticles` inside the sphere, sized by `blockCount`. It never learns which blocks broke.
- So the core idea: `handleExplosion` opens a capture window (center, radius, expected `blockCount`). Every block update in the next few ticks that turns a solid block inside radius + 1 into air or fluid is a destroyed block: grab its old `BlockState` (and block entity, for chests, signs, banners, beds) before it is replaced. `blockCount` tells you when the capture is complete. Prove that the counts match for every source, and that blocks removed by other means inside the window (mining, pistons, falling sand) aren't caught.
- Game rules: `tnt_explosion_drop_decay`, `mob_explosion_drop_decay`, `block_explosion_drop_decay`, `mob_griefing` (creepers break nothing when it's off: blast only). Check whether `tnt_explodes` exists.
- Vanilla already has anticipation: `TntRenderer` flashes and swells the primed TNT, `CreeperRenderer` swells and flashes the creeper. Exaggerate those instead of inventing new tells.
- The user's "Fabric 26.3" profile runs Sodium 0.9.2, Iris 1.11.6 (Complementary shaders), EntityCulling, Punchy, Mob Reactions and EMF/ETF. It must look right with shaders on and off, so render debris through the entity pass (as `FallingBlockRenderer` does) and verify Iris shades it.

## Quality bar (Actions & Stuff)
Reads in one second: anticipation → hit-stop → snap → follow-through → settle. Beats to hit:
1. **Anticipation:** the TNT's last ~10 ticks pump (squash/stretch), the creeper's swell is pushed further.
2. **Detonation:** a 1–2 frame flash and a stylised cartoon puff (chunky smoke balls, not vanilla's grey sprites). The debris appear exactly on their blocks, flashed and frozen for 1–2 frames (hit-stop).
3. **Launch:** radial + upward bursts, faster near the centre, stretched along the velocity at launch, tumbling. Staggered by distance, a ripple, like Timber's rings, never all at once.
4. **Flight and landing:** gravity arcs; collide with the world on the client; squash on contact, 1–2 decaying bounces.
5. **Pop:** squash → stretch → vanish with crumbs, a small gust and a plop that rises in pitch per pop (Veinminer/Timber), rate-limited. Investigate hiding the real item entities spawned in the window until their debris pops, so drops don't show twice.
6. **Crater:** surviving blocks on the rim jiggle for a few frames.
7. Mobs and players thrown by the blast are Mob Reactions' job (its launch clip); make sure the two mods look right together.
- **No camera shake** by default: the user read view kicks in Mob Reactions as micro-stutters.
- The user's style: crisp, heavy, physical, readable, not rubbery. Every look decision is shown as real-client GIFs (side by side, plus slow motion), 2–3 variants, and the user picks.

## Auto repair (toggleable, off by default)
Asked for by the user on 2026-09-27: creeper explosions (charged ones too) and TNT explosions (the TNT block and the TNT minecart) repair themselves after a delay, with a nice animation. Beds, respawn anchors, end crystals, ghast fireballs and withers stay broken unless the user asks otherwise.
- **Toggles:** two, creepers and TNT, **both off by default** (the user's call, 2026-09-27; TNT is often used on purpose for mining and clearing, so never repair it unless switched on). On/off in the settings and by command (check whether a mod can add a real game rule in 26.x without Fabric API; otherwise a server config plus an op command). Also a delay setting.
- **Record on the server:** hook the explosion on the logical server; when the exploder is a creeper or TNT and its toggle is on, store every destroyed position with its `BlockState` and block-entity data (chests, barrels, signs, banners, beds, pots, …) in a ledger per explosion.
- **No duplication:** blocks that will be repaired must not drop items, and a container's contents must not spill (they come back with the block). Prove it: count items before and after in the tests.
- **Persistent:** keep pending repairs in the level's saved data, so a restart, a chunk unload or leaving the area mid-repair loses nothing. Repair unloaded chunks when they load again, or keep them queued.
- **Correct rebuild:**
  - Order: supports first (bottom-up, solid before attached), so torches, ladders, rails, carpets, redstone, doors and beds (both halves), tall plants and falling blocks (sand, gravel, concrete powder) land on something.
  - Set blocks without neighbour updates until a whole batch is in, then update, so nothing pops off or falls mid-rebuild.
  - Never overwrite what a player placed in the hole meanwhile: skip that position. Flowing fluid that ran into the crater may be replaced; a source block a player placed may not (decide and document the rule).
  - Entities standing in a returning block's space get nudged out, never suffocated.
  - Overlapping explosions (a second creeper hits the same spot, a blast breaks a half-repaired wall, a TNT chain where some TNT was primed by a creeper) must never restore stale states or duplicate anything. A block goes back to the state it had before the first blast of the chain.
- **Server cost:** a per-tick restore budget and a memory cap for the ledger; measure the tick time on a big crater.
- **Animation:** the reverse of the blast, like a rewind: blocks hop back out of the ground or fly in from where their debris landed, turn upright in flight, drop into their slot with a squash and settle, a soft puff, and a plop that falls in pitch as the wall closes (the blast's rising plops in reverse). Ripple it (bottom rows first, or outer rim inward) so it reads as a build, never all at once. Show 2–3 variants as real-client GIFs; the user picks.
  - Prefer no custom network packets: a client with the mod can recognise a repair itself (a block update that restores the exact state it recorded as blown up at that spot) and animate it. If the animation needs the server to announce repairs ahead of time, a payload may need Fabric API on Fabric: ask the user before adding that dependency.
  - Decide with the user whether blast debris should linger on the ground until the repair (continuity: the same pieces fly back) or pop as usual, which is cheaper and fine when the delay is long.

## Performance (60 fps+ is a hard requirement)
- Evaluate every curve per frame from partial ticks, never per tick. Check at a 60 fps cap and uncapped (144/240).
- Targets (measure, don't assume): one TNT adds under 0.5 ms per frame; a 100-TNT chain keeps ≥ 60 fps on this PC (5900X, RTX 3080) with shaders off and never hitches more than one frame when it starts; 1000 TNT degrades gracefully.
- Means: a global live-debris cap plus a per-explosion cap; far debris become particles only; distance culling; pooled debris objects and no allocation in the hot path; baked block quads cached per `BlockState`; batched drawing, no entity per debris.
- Measure frame time vanilla vs mod on the same scripted scene (log, histogram, p99), and log the drawn debris per frame for curves, the way the polish lab's probe does.

## Approach
1. **Skeleton:** copy Mob Reactions' Stonecutter build, rename it (mod id, package `io.github.profetgit.<id>`), and boot an empty mod on all 6 targets off-screen.
2. **Research** on both jars with `javap`: the packet/block-update order (a probe that logs `handleExplosion` and every block update with tick and time), how to draw a `BlockState` in the entity pass with Sodium present, when the server's item entities arrive relative to the block updates, and every block-breaking source in 26.x: TNT, TNT minecart, creeper, charged creeper, bed and respawn anchor, end crystal, ghast fireball, wither and wither skull. Wind charges and breezes break no blocks: a puff at most. Underwater TNT breaks nothing. For the repair, read the server side too: `ServerExplosion` (how it picks blocks, where drops and container spills happen, how to tell a creeper exploder), and the saved-data API for the ledger.
3. **Prototype** one TNT on a flat world in the real client with the demo director (scripted scenes, fixed side / hero / first-person cameras). Capture, debris, pop. Film it.
4. **Style pick:** 2–3 variants (for example full-size blocks vs chunky mini-blocks, puff style, pop timing) as side-by-side GIFs. The user picks.
5. **Scale:** 10 / 100 / 1000 TNT chains, caps, LOD, the frame-time report.
6. **Coverage:** all sources; game rules; creative; water, lava, nether, end; caves (sample light at the debris' position every frame, so a block flying through stone never renders black); block entities, two-block doors and beds, tall plants, glass and leaves, snow layers, sand that falls afterwards; render-distance edges; singleplayer and a dedicated server; another player's blast far away; Sodium, Iris with shaders, EntityCulling, Mob Reactions. If Explosive Enhancement is installed, don't draw two puffs.
7. **Auto repair:** the server ledger, no-drop rule, persistence, rebuild order, then the repair animation and its style pick (see "Auto repair" above).
8. **Settings:** a minimal config (intensity, max debris, pops on or off, creeper repair and TNT repair on/off (both off by default), repair delay), the way Tidy Pockets does it (its per-loader entries and Mod Menu screen).
9. **Polish** with the `polish` skill.
10. **Tests:**
    - Visuals: demo checks in `results.json` (captured == `blockCount`, debris spawned, every debris popped within N ticks, none left over, frame-time budget), on Fabric/NeoForge/Forge × 26.2/26.3, three runs at a time.
    - Repair, headless on a dedicated server per loader × version (the loader installs from `TidyPockets/dev/selftest/install_loaders.sh`): a creeper, a TNT block and a TNT minecart each blow up a scripted house (chest with items, sign, bed, door, torches, rails, redstone, sand, glass, a water edge); after the delay every block state and block entity matches the snapshot exactly, the item count is unchanged (no dupes), a block a player placed in the hole survives, a restart mid-repair still finishes, and a mixed creeper + TNT chain comes back right. With the defaults (both off) nothing is repaired; each toggle repairs only its own source; other sources (bed, end crystal, ghast fireball) are never repaired. Also: modded server + vanilla client, and modded client + vanilla server.
11. **Finish:** `./gradlew dist`, version 0.1.0, install the Fabric 26.3 jar into the "Fabric 26.3" profile (move any old jar to `ModJar/.work/replaced/`), add a Having a Blast section to the workspace `CLAUDE.md` and update the `having-a-blast-project` memory. Icon, banner, showcase clips, the video and publishing are later sessions.

## Rules
- **Every test stays off-screen**: each client runs in `run.sh`'s private `kwin_wayland --virtual`, with Forge/NeoForge's early window disabled through `fml.toml`. Never start a client any other way, never run two matrices at once, never edit `run.sh` while a run uses it.
- Captures go to `dev/demo/.work/`, never `/tmp` (16 GB tmpfs; a full run with frames is gigabytes).
- Look at frames at full size before judging; thumbnails hide small debris.
- Premium means clarity and weight, not more motion: fewer, stronger poses and good holds.
