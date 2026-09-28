# Polish log: Having a Blast 0.1.0 (2026-09-27)

A scoped first pass by the polish skill's checklist (`~/.claude/skills/polish/references/checklist.md`). The lab here is
the mod's own demo director (`dev/demo/run.sh`: real client, off-screen, frames at ~120 fps with timestamps, per-frame
timing.txt with debris drawn and the mod's own render-thread time) rather than lablib's lockstep recorder, which is
built around data packs and displays. The whole motion is analytic per frame, so there are no 20 Hz keys, byte
rotations or packet cadences to measure. The per-path continuity check replaces lablib's hiccup metric.

## 1. Motion
- Continuity: every debris path sampled every 0.05 tick. Largest step 0.045–0.054 blocks, which is only the flight
  speed (the `continuity` check, all scenes). Found and fixed on the way: stacking on debris that land later (pieces
  hung on nothing and then jumped), and a footprint floor that snapped pieces up a lip (0.98 and 1.87 block jumps).
  The fix is a swept-box collision bisected to the last free instant.
- Holds are intentional: the 2-tick hit-stop with a white flash before launch, and the rest before the pops.
- Squash and stretch are percent-based, on pieces of 0.38–0.78 blocks (one size class), grounded at the bottom.
- Frame-time hitch on first use: the sprite sheet and the model resolver loaded in the first blast's frame (27 ms).
  They are now pre-warmed on the first client tick.

## 2. Timing and readability
- Anticipation: the TNT pumps over 12 ticks and holds until the server's bang. The client used to drop the TNT up to
  2 ticks early (dead frames), fixed by PrimedTntMixin. The creeper swell is pushed further.
- Payoff within 10 ticks: the launch ripple starts at 2 ticks, the fireball at 0.
- Read from side, hero and first-person cameras. First person showed a dust-skirt puff rolling into the camera and
  covering the bottom of the view for about 10 ticks. Fixed: sprites shrink away within 4 blocks of the camera.

## 3. Impact
- The star impact frame is at full size from the first frame (it used to start at 0, so the first blast frame was
  empty). No camera shake.

## 4. Particles and sound
- Pops: a chicken-egg plop plus the block's break sound, pitch 0.8 → 1.7 across the sequence (≤ 2.0), rate-limited
  to one per 1.2 ticks. Repair: plops falling 1.7 → 0.6. Untested by ear.
- Only block crumbs (short, colour-matched) and the mod's own sprites; no `poof`/`cloud`.

## 5. Visual integrity
- Black debris inside blocks: the light falls back to the brightest open neighbour (cave scene). Light is eased per
  frame, so there are no pops.
- Shaders: the user's profile with Complementary. Found and fixed: with Sodium (the Fabric Rendering API) a direct
  `submitBlockModel` drew nothing, so the debris were invisible. They now go through `BlockModelRenderState.submit`.
- Billboard winding: clockwise quads were culled or darkened. They are counter-clockwise now.
- Frustum culling: a close first-person take shows no holes or pops at the frame edges.

## 6. Variants and edge cases
- Scenes: tnt1/10/100/1000, creeper, charged creeper, sources (minecart, charged creeper, end crystal, ghast
  fireball, wither skull), wither, bed in the Nether, respawn anchor, Nether TNT, End, underwater, cave, lava,
  garden (leaves, tall plants, glass, panes, snow, sand), house, `mob_griefing` off, a blast past 64 blocks, zombies
  (with Mob Reactions), capture-window noise (mining, pistons, falling sand).
- In every scene the server's count equals the client's capture, except bed (+2) and anchor (+1): the exploding block
  removes itself just before the blast and flies as debris too.

## 7. Performance (quiet machine, 26.3 Fabric)
- 1 TNT: mod 0.013 ms per frame on average (budget 0.5). 100 TNT holds 60 fps (max frame 18 ms at the cap).
  1000 TNT: about 45 fps at worst at the cap, with no long hitch.
- Repair: a 313-block chain crater rebuilds with the worst server tick at 2.3 ms, mean 0.04 ms.

## 8. Robustness
- Client matrix (Fabric, NeoForge, Forge × 26.2/26.3), repair matrix (8 servers incl. Quilt), interop both ways on
  both versions, the user's profile with shaders.

## Rejected
- 3D voxel smoke balls (the user: "feels off").
- The air ring (the ground cut it into a white arch).
- The pop flash (the user dislikes brightness flashes).
- Debris stacking on each other.

## Not done in this pass
- lablib's lockstep 60 fps reel and curves. By-ear sound review.

# Polish log: Having a Blast 0.2.3, the explosion (2026-09-28)

The user: "something is off with the explosion animation, maybe the smoke is too much". Lab = the demo director
(`dev/demo/run.sh`, ~120 fps real-time frames), captures in `dev/demo/.work/pol-*` (base = the 0.2.2 jar in
`dev/lab/baseline/`), reels in `dev/demo/out/polish-023/`. The user's profile has Explosive Enhancement disabled, so
they see this effect (through Complementary shaders).

## Findings (baseline, full-size frames; tnt1 / creeper / tnt10 / cave × side, hero, first person, and shaders)
1. Slanted texels: smoke puffs were rolled by `roll * 0.5` of a quarter turn, i.e. 45 degrees, and the fire layers by
   +0.4/+1.1/+2.0 rad plus a slow spin. Rotated pixel art showed dotted diagonal outlines and octagon silhouettes.
2. Sticker pile: every puff had its own dark rim, so the cloud read as a stack of outlined coins, each with its own
   highlight ring.
3. "Fried egg": the smoke puff grew (×1.2 overshoot) behind a fire layer that was still burning, leaving a red or
   orange disc in the middle of a white puff for 2–3 ticks (t+4…7).
4. Too much smoke for too long: puffs up to 5.3 blocks (TNT), life 18–27 ticks, accelerating rise (0.006·tb²) to
   ~7 blocks: the cloud detached and sailed off into the sky while debris were still landing. Fire read for ~4 ticks,
   smoke for ~22.
5. Shockwave ring: 15.5 blocks across for TNT, lit at block light 13, 8 ticks: from first person and in the creeper
   scene it swept under the camera as a huge jagged white band.
6. Light snap: each puff switched from block light 13 to the surroundings at its own tb = 6, so in the dark puffs
   popped darker one by one.
7. Dust skirt: 12 puffs rolling out to ~6.4 blocks, 13–19 ticks, like grey rocks on the grass.
8. Cave: the rising cloud was cut flat by the ceiling.

## Changes (0.2.3)
- Sprites: rimless fills and one-pixel-grown silhouettes for every puff, dust and breakup frame (sheet 256×512, cells
  from y 256). Breakup frames are shifted to fit the cell (some lobes were cut flat by the border before).
- Outline behind: each puff draws its silhouette in ink pushed 1.6·scale blocks further along the view ray and scaled
  by the same factor (same projection, deeper), so overlapping puffs hide each other's outlines: one outline per cloud.
- One puff per billow: the outer fire layer darkens (value first: red → deep red → soot), then cools to grey and takes
  the smoke shading once the inner layers are gone. Inner layers go out at 30 % size instead of shrinking to a dot.
- Quarter turns only; shaded fills never turn or mirror (the light stays upper left).
- Smoke: puffs 1.6–2.4 × scale, life 14–20, rise that slows (≈2.9 blocks max for TNT), gone by ~t+20.
- Smoke light is one value per blast: full glow until t 4, easing to the place's light by t 12.
- Ring: 6 ticks, ≤ 2.2 + 5.4 × scale blocks, stops 2.5 blocks short of the camera, block light ≥ 8 (was 13).
- Dust skirt: reach ~4.8 blocks, life 9–13, rimless fill + outline.
- Ceiling: read at the first frame and at t 3 (after the crater), eased over 3 ticks; puffs slide down to fit under it.
- The star flash no longer spins.
- Taste option: `-Dhavingablast.fx=whitesmoke` keeps the old warm-white smoke ramp with every other fix
  (`smoke-colour-pick.mp4`); it goes peach between fire and smoke. Default: soot to grey.

## Measured
- Mod render time per frame (26.3 Fabric, side cam, load average 9–12, noisy): tnt1 mean 429 → 402 µs, p99 4.7 → 4.0 ms;
  tnt10 mean 426 → 531 µs, p99 2.5 → 2.5 ms. The outline pass adds one quad per puff and per dust puff.
- Every capture's checks pass (frame budget, continuity, count_match, pops, drops).

## Rejected / not done
- Soft (alpha) smoke: the sheet uses a cutout render type; translucency would need sorting and breaks the pixel look.
- Clamping puffs to walls (only the ceiling is handled).
- The pop tail (debris rest ~8 ticks, then pop until ~t+40) is unchanged: approved earlier, not part of this complaint.

# 0.2.4: the round look (2026-09-28, same day)

The user on 0.2.3: "definitely better, but something's still off, maybe too cartoony, maybe too realistic, maybe it
needs some roundness"; they pointed at Explosive Enhancement (MIT, disabled in their profile), which "looked freaking
perfect" with this mod's debris, and asked not to copy it exactly. EE jar decompiled (Vineflower from the Gradle cache)
into the session scratchpad, filmed with our debris as the reference (`dev/demo/.work/ee2-*`).

## What EE does (and why it read better)
- One fireball quad (10 blocks for TNT, centre 0.5 above the blast, so the ground hides its lower half: a dome) that
  animates by flipbook frames at a fixed size: the pixels never change size. Concentric colour bands, no outline.
- A flat ring (14 blocks) flipbook, thick yellow/orange, lasting ~16 ticks.
- Six small 16 px puffs thrown up 6-10 blocks into a mushroom, flipbook yellow → red → grey → specks.
- A white spark star/ray burst late, as the fireball pales. Swell to full takes ~6 ticks.
- Colour is painted into the textures; translucent fades.

## Ours (0.2.4)
- New frames in `dev/fx/make_fx.py` (`round_fx`), colour baked: a lit sphere (bands offset up-left, shade crescent
  lower right, darker rim) growing 0-5, full 6-7, paling 8-9, light lumps 10-11 (lumps instead of a dither dissolve,
  which read as a screen door); puffs on a 16 px grid at 2x cooling yellow → brick → greys → bits; round dust puffs;
  rings with a yellow front and orange trail, thinning, going red and broken. Unused old cells were dropped.
- One dome (sc × 6.8 blocks) at ground + 0.5, swelling over 5 ticks, gone at 12.2; shrinks to fit under a ceiling.
  Two small satellite balls were tried and dropped: they read as eyes.
- Ring sc × 10.5 blocks, 1.8 ticks per frame (14 ticks), scaled down to stop 2.5 blocks short of the camera.
- Mushroom: 2 stem puffs + a wide cap ring + a top puff (heights up to sc × 6.6), exp ease-out launch (τ 2.6 ticks),
  fire colours for the first 22 % of life (a red puff over the pale dome read as an eye).
- Sparks in two waves: yellow at the blast, longer white ones as the dome pales.
- All dust, landing puffs, pop poofs, repair poofs and bubbles use the round dust frames (no rims anywhere).

## Also found: invisible debris with Fabric API (all versions so far)
- With Fabric API and no Sodium the flying blocks were invisible (their poofs showed). Fabric's renderer API puts even
  vanilla models into its own mesh when `BlockModelResolver.update` runs, leaving no vanilla parts, and neither our
  direct submit (no parts) nor `BlockModelRenderState.submit` (the mesh) drew anything from this pass under Indigo.
  Fix: take the parts from `BlockStateModelSet.get(state).collectParts(...)` when the resolver left none, and use
  `BlockModelRenderState.submit` only when Sodium is present. Checked: Fabric API alone, the full Sodium profile, and
  no extra mods all draw the debris (leaves, glass panes, grass tint included, garden scene).
