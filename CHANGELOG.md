# Changelog

## 0.2.5 (unreleased)

- Blocks that pop into their drops no longer leave a smoke puff; they just crumble with their plop. The dust where blocks land and the puff when a repaired block drops in are small again.

## 0.2.4 (unreleased)

- A new, rounder explosion:
  - a big glowing fireball dome that swells up, pales and breaks into puffs;
  - a fiery shockwave ring across the ground;
  - round puffs thrown up into a little mushroom cloud, cooling from yellow through orange and red to grey;
  - white sparks as the fireball fades.
- No more black outlines or pixel art turned to slanted angles; the smoke no longer floats off into the sky, the ring never sweeps under your feet, and in a cave the fireball and smoke fit under the ceiling.
- Fixed: with Fabric API but without Sodium, the flying blocks were invisible (only their poofs showed).

## 0.2.2 (unreleased)

- Repair speed setting: `/havingablast repair speed <25-400>` (percent) and a slider in the settings. 100% is the old speed; 200% rebuilds a 1000-TNT crater (about 27,000 blocks) in about 11 seconds instead of 22.

## 0.2.1 (unreleased)

- The repair animation lands cleanly: no more flickering as blocks drop into place. A block no longer blinks out for a few frames between its fly-in and the real block showing up (a see-through hole), no longer bulges over its neighbours (striped z-fighting) or bares their edges as it settles (thin lines along walls and floors). It lands with one short squash instead of a bouncy wobble.

## 0.2.0 (unreleased)

- Item frames (with their item and rotation), glow item frames, paintings and armor stands (with their equipment) come back with a repair instead of dropping. They hang back up with a puff once the wall is in. One whose spot a player built over drops as usual.
- Repair for more blasts, each with its own toggle (all off by default): beds, respawn anchors, end crystals, ghast fireballs and the wither (its spawn blast, its skulls and the blocks it breaks when hurt). The bed, anchor or crystal that exploded is used up, like TNT. `/havingablast repair bed|anchor|crystal|fireball|wither <true|false>`.
- Fire a repairing blast starts (a bed, an anchor, a fireball) is put out by the repair, and what it burned comes back.
- Fixed: a crater floor that fire had stood on (after a bed or fireball blast) wasn't rebuilt.

## 0.1.0 (unreleased)

First build.

- Cartoon explosions for TNT, creepers and every other block-breaking blast, drawn on the client (works on any server):
  - an anticipation beat: primed TNT pumps through its last ticks and holds until the server's bang; the creeper's swell is pushed further;
  - a star impact flash, a layered pixel-art fireball that cools into a smoke column and breaks apart, spark streaks, a ground shockwave with a dust skirt, embers;
  - the blasted blocks burst out as mixed-size debris in a ripple from the centre, tumble on real arcs, squash and bounce where they land, and pop one after another into their drops (the real item drops stay hidden until then);
  - grass, podzol, mycelium, paths and farmland fly as dirt (scorched), except some pieces from the crater's rim;
  - the crater's rim jiggles; dust puffs where debris land hard;
  - under water: bubbles, no fire;
  - attached blocks (torches, doors, signs) fly with the blast; blocks a player mines or a piston moves nearby are left alone;
  - smoke and dust never cover your view up close; pieces that fall into the void just drop out of sight.
- Creeper and TNT auto repair (server side, both off by default): after a delay the blast rebuilds itself, bottom row first, with no dropped items and no duplication, surviving restarts and unloaded chunks. Players' own blocks in the hole are never overwritten. `/havingablast repair creeper|tnt <true|false>`, `delay <seconds>`, `status`, `now`.
- Repair animation on clients with the mod: blocks hop back out of the ground into their slots.
- Settings screen (Mod Menu on Fabric, the mods list on NeoForge and Forge) and `config/havingablast.json`: cartoon explosions on/off, intensity, most debris at once, pops, repair animation, creeper and TNT repair, repair delay.
- Works with Sodium, Iris shaders and Explosive Enhancement (its puff is used when it's installed).
- Mod icon (shown in Mod Menu and the NeoForge/Forge mods list).
