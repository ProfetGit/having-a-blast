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
