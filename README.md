![Having a Blast](https://raw.githubusercontent.com/ProfetGit/having-a-blast/main/docs/banner.gif)

<p align="center">
<a href="https://github.com/ProfetGit"><img src="https://raw.githubusercontent.com/ProfetGit/assets/main/buttons/github.gif" alt="GitHub" width="23.96%"></a>
<a href="https://ko-fi.com/profetgit"><img src="https://raw.githubusercontent.com/ProfetGit/assets/main/buttons/kofi.gif" alt="Ko-fi" width="23.96%"></a>
</p>

**One boom, bouncy blocks.** Having a Blast turns every explosion into a cartoon: the blasted blocks burst out as real blocks, fly, bounce and pop into their drops. And if you want, creeper holes fix themselves. For Minecraft 26.2 and 26.3 on Fabric, Quilt, NeoForge and Forge. Install it on your client for the show, on a server for the auto repair, or on both.

![Features](https://raw.githubusercontent.com/ProfetGit/having-a-blast/main/docs/desc/title-features.png)

- **Cartoon explosions.** A glowing pixel-art fireball dome that swells and fades, a fiery shockwave ring, round puffs thrown up into a little mushroom cloud, and flying sparks.
- **Blocks you can follow.** The blasted blocks fly out on real arcs, tumble, squash and bounce where they land, then pop one after another into their drops.
- **Wind-up.** Lit TNT pumps and holds its breath before the bang, and creepers swell further.
- **Auto repair** for creeper, TNT, bed, respawn anchor, end crystal, ghast fireball and wither blasts. Each has its own switch, and all are off by default. After a delay the hole rebuilds itself, bottom row first, with the blocks hopping back out of the ground.
- **Nothing lost, nothing duplicated.** Blocks that will be repaired drop nothing, chests keep their contents, item frames, paintings and armor stands come back items and all, and a block you placed in the hole is never overwritten.
- **Works on any server.** The visuals are client-side, so they show on vanilla servers and Realms too.
- **Light.** Built for 60 fps and above, even with a hundred TNT going off at once.

![How to use](https://raw.githubusercontent.com/ProfetGit/having-a-blast/main/docs/desc/title-how-to-use.png)

Just blow something up. The cartoon explosions are on from the start. To turn on auto repair, open the settings (singleplayer) or use `/havingablast repair` (servers).

![Settings](https://raw.githubusercontent.com/ProfetGit/having-a-blast/main/docs/desc/title-settings.png)

Open them in Mod Menu (Fabric, Quilt) or the Mods list (NeoForge, Forge): cartoon explosions on or off, intensity, pops, the most debris at once, the repair animation, one repair switch per explosion type, the repair delay (30 seconds by default) and the repair speed. They're saved in `config/havingablast.json`.

![Commands](https://raw.githubusercontent.com/ProfetGit/having-a-blast/main/docs/desc/title-commands.png)

For operators, on a server with the mod:

- `/havingablast repair <creeper|tnt|bed|anchor|crystal|fireball|wither> [true|false]`: turn repair on or off for one explosion type.
- `/havingablast repair delay [seconds]`: how long after the blast the rebuild starts.
- `/havingablast repair speed [percent]`: how fast the hole rebuilds (25 to 400, default 100).
- `/havingablast repair status`: what's switched on and how many blocks are waiting.
- `/havingablast repair now`: rebuild everything that's waiting right away.

![Compatibility](https://raw.githubusercontent.com/ProfetGit/having-a-blast/main/docs/desc/title-compatibility.png)

- Works with Sodium, Iris shaders and Mob Reactions.
- With Explosive Enhancement installed, its fireball is used; the flying blocks and the repair still work.
- A client without the mod can join a server with it: blocks just come back without the animation. A client with the mod can join any server.

![Installation](https://raw.githubusercontent.com/ProfetGit/having-a-blast/main/docs/desc/title-installation.png)

Put the jar for your loader in your `mods` folder: Fabric or Quilt, NeoForge or Forge, for Minecraft 26.2 and 26.3. No other mods needed. To uninstall, remove the jar; blocks still waiting for repair then stay broken.

![Good to know](https://raw.githubusercontent.com/ProfetGit/having-a-blast/main/docs/desc/title-good-to-know.png)

- The thing that exploded is used up: repair brings back the blocks, never the TNT, crystal, bed or anchor.
- Pending repairs survive restarts and unloaded chunks.

![More from Profet](https://raw.githubusercontent.com/ProfetGit/having-a-blast/main/docs/desc/title-more-from-profet.png)

<!-- promo:start -->
<p align="center">
<a href="https://www.curseforge.com/minecraft/mc-mods/tidy-pockets"><img src="https://raw.githubusercontent.com/ProfetGit/assets/main/promo/tidy-pockets.gif" alt="Tidy Pockets: One click. All sorted. Client side." width="49%"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/far-out-zoom"><img src="https://raw.githubusercontent.com/ProfetGit/assets/main/promo/far-out-zoom.gif" alt="Far Out Zoom: The horizon, up close. Client side." width="49%"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/travelers-lantern"><img src="https://raw.githubusercontent.com/ProfetGit/assets/main/promo/travelers-lantern.gif" alt="Traveler's Lantern: Your light. Hands free. Client or server." width="49%"></a>
<a href="https://github.com/ProfetGit/overreacting-mobs"><img src="https://raw.githubusercontent.com/ProfetGit/assets/main/promo/overreacting-mobs.gif" alt="Overreacting Mobs: One hit. Big drama. Client side." width="49%"></a>
</p>
<!-- promo:end -->

![Support](https://raw.githubusercontent.com/ProfetGit/having-a-blast/main/docs/desc/title-support.png)

Having a Blast is free. If it gave you a good laugh, a coffee helps fund the next update.

[![Support me on Ko-fi](https://raw.githubusercontent.com/ProfetGit/assets/main/kofi-banner.gif)](https://ko-fi.com/profetgit)

Want your own server to play on with friends? My BisectHosting affiliate link gives you 25% off the first month, and I get a small commission.

[![Get 25% off your first month at BisectHosting](https://raw.githubusercontent.com/ProfetGit/assets/main/bisecthosting-banner.gif)](https://url-shortener.curseforge.com/Pp2BN)

![License](https://raw.githubusercontent.com/ProfetGit/having-a-blast/main/docs/desc/title-license.png)

All rights reserved, with permissions: you can use it anywhere, include it in modpacks with credit, and show it in videos. Full terms: [LICENSE](https://github.com/ProfetGit/having-a-blast/blob/main/LICENSE).

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.

![](https://raw.githubusercontent.com/ProfetGit/having-a-blast/main/docs/desc/divider.png)
