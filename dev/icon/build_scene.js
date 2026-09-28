// Having a Blast icon scene + animation. Run inside Blockbench (free format project named PROJECT):
//   eval(require('fs').readFileSync('<this file>', 'utf8')); window.HB = HB; HB.loadTextures()   // then, in a later call:
//   HB.build(); HB.animate(); HB.camera()                                                          // then:
//   HB.render(first, last)                                                                         // 1600px frames -> frames/
// Blocks live in the `vein` group (yawed 45 deg, grid units x16). Everything flat (fire, smoke, star, sparks, poofs,
// crumbs) lives in the `screen` group, tilted to face the orthographic camera: x = right, y = up, z = toward camera.
var HB = (function () {
  const fs = require('fs');
  const DIR = '/home/emppu/Projects/Minecraft Datapacks/HavingABlast/dev/icon/';
  const TEX = DIR + 'sprites/';
  const FPS = 25, DT = 1 / FPS, LEN = 3.2;
  const CAM_POS = [0, 60, 104], CAM_TARGET = [0, 16, 0];
  let CAM_PAN = [0, 10, 0], CAM_ZOOM = 0.2;
  const PITCH = -Math.atan2(CAM_POS[1] - CAM_TARGET[1], CAM_POS[2] - CAM_TARGET[2]) * 180 / Math.PI;
  const O = new THREE.Vector3(...CAM_TARGET);
  const RAD = Math.PI / 180;
  const Z_FX = 60, Z_FIRE = -40, Z_CRUMB = 45;   // fire and smoke sit behind the blocks, so the launch reads

  // ground cast: [grid cell, kind]; the TNT sits on the stone
  const BLOCKS = [[[0, 0, -1], 'stone'], [[0, 0, 0], 'grass'], [[1, 0, 0], 'grass']];
  const TNT = [0, 1, -1];

  const T = {
    pump: [0.36, 0.56, 0.72], boom: 0.88,
    launch: [0.96, 1.00, 1.04], flight: [13, 12, 14],
    pops: [2.00, 2.12, 2.20],
    rise: [2.40, 2.48, 2.56],
    tntDrop: 2.64, tntLand: 2.76, spark: 2.96,
  };
  const P = {
    land: [[-28, 4], [4, 20], [28, 2]],          // landing spots, world X/Z (screen-aligned), per block
    apex: [22, 26, 20],
    bounce: [[6, 4], [2, 3]],
    billows: [[0, 7, 0, 1.0], [-11, -1, 1, 0.9], [11, 0, 0, 0.9]],   // screen offset from the TNT centre, shape, size
    fire: 36,
  };

  const q = t => Math.round(t * FPS) / FPS;
  const worldToScreen = w => new THREE.Vector3(...w).sub(O).applyEuler(new THREE.Euler(-PITCH * RAD, 0, 0)).add(O);
  const veinToWorld = a => new THREE.Vector3(...a).applyEuler(new THREE.Euler(0, 45 * RAD, 0));
  const worldToVein = (X, Z) => [Math.SQRT1_2 * (X - Z), Math.SQRT1_2 * (X + Z)];
  const vs = p => worldToScreen(veinToWorld(p).toArray());
  const blockCentre = b => vs([b[0] * 16, b[1] * 16 + 8, b[2] * 16]);

  // Blockbench is shared with other sessions: every entry point selects this project first, render() holds BB_LOCK
  const PROJECT = 'having_a_blast_icon', ME = 'having-a-blast';
  function own() {
    const p = ModelProject.all.find(m => m.name === PROJECT);
    if (!p) throw new Error('project ' + PROJECT + ' is not open');
    if (Project !== p) p.select();
    return p;
  }
  function lockedBy() {
    const l = window.BB_LOCK;
    return l && l.owner !== ME && l.until > Date.now() ? l.owner : null;
  }

  const tex = {};
  function loadTextures() {
    own();
    Texture.all.slice().forEach(t => t.remove(true));
    for (const f of fs.readdirSync(TEX).filter(f => f.endsWith('.png') && !/^(bg|banner|review)/.test(f))) {
      const url = 'data:image/png;base64,' + fs.readFileSync(TEX + f).toString('base64');
      tex[f.slice(0, -4)] = new Texture({ name: f }).fromDataURL(url).add(false);
    }
    return Object.keys(tex).join(',');
  }
  function ensureTex() {
    for (const k in tex) delete tex[k];
    Texture.all.forEach(t => { tex[t.name.replace('.png', '')] = t; });
  }

  function group(name, origin, parent, rotation) {
    const g = new Group({ name, origin, rotation: rotation || [0, 0, 0] });
    g.addTo(parent); g.init();
    return g;
  }
  const FACES = ['north', 'south', 'east', 'west', 'up', 'down'];
  function cube(name, from, to, parent, faceTex, opts) {
    const c = new Cube(Object.assign({ name, from, to, box_uv: false }, opts || {}));
    c.addTo(parent); c.init();
    for (const f of FACES) {
      const spec = faceTex[f] || faceTex.all;
      if (spec) c.faces[f].extend({ texture: tex[spec[0]].uuid, uv: spec[1] });
      else c.faces[f].extend({ texture: null });
    }
    return c;
  }
  function plane(name, centre, size, parent, texName, mirror) {
    const [x, y, z] = centre, h = size / 2;
    return cube(name, [x - h, y - h, z], [x + h, y + h, z], parent, { south: [texName, mirror ? [16, 0, 0, 16] : [0, 0, 16, 16]] });
  }
  const FULL = [0, 0, 16, 16];
  const faceSet = (top, side, flash) => flash
    ? { up: [top + '_flash', FULL], all: [side + '_flash', FULL] }
    : { up: [top, FULL], down: [side + '_right', FULL], west: [side + '_left', FULL], east: [side + '_left', FULL],
        south: [side + '_right', FULL], north: [side + '_right', FULL] };
  const KIND = { grass: ['grass_top', 'grass_side'], stone: ['stone', 'stone'], tnt: ['tnt_top', 'tnt_side'] };
  const CHUNK = { grass: [0, 0, 4, 4], dirt: [4, 0, 8, 4], stone: [8, 0, 12, 4], tnt: [12, 0, 16, 4] };

  const G = {};
  function block(name, cell, kind, parent) {
    const [cx, cy, cz] = cell.map(v => v * 16);
    const g = G[name] = group(name, [cx, cy, cz], parent);
    const sp = G[name + '_spin'] = group(name + '_spin', [cx, cy + 8, cz], g);
    const [top, side] = KIND[kind];
    cube(name + '_cube', [cx - 8, cy, cz - 8], [cx + 8, cy + 16, cz + 8], sp, faceSet(top, side));
    const fl = G[name + '_flash'] = group(name + '_flash', [cx, cy + 8, cz], sp);
    cube(name + '_flash_cube', [cx - 8, cy, cz - 8], [cx + 8, cy + 16, cz + 8], fl, faceSet(top, side, true), { inflate: 0.35 });
  }
  function fxPlane(name, at, size, texName, mirror) {
    G[name] = group(name, at, G.screen);
    plane(name + '_plane', at, size, G[name], texName, mirror);
  }
  function chunk(name, at, size, tile) {
    G[name] = group(name, at, G.screen);
    const h = size / 2;
    cube(name + '_cube', [at[0] - h, at[1] - h, at[2] - h], [at[0] + h, at[1] + h, at[2] + h], G[name], { all: ['chunks', CHUNK[tile]] });
  }

  const BOOM_CHUNKS = [['grass', 100, 3.4], ['tnt', 70, 3.0], ['stone', 125, 3.2], ['dirt', 40, 2.8], ['tnt', 150, 2.6], ['dirt', 170, 3.0]];
  const POP_CHUNKS = { grass: ['grass', 'dirt', 'dirt'], stone: ['stone', 'stone', 'stone'] };

  function build() {
    own(); ensureTex();
    Animation.all.slice().forEach(a => a.remove(false));
    Outliner.root.slice().forEach(n => n.remove(false));

    G.vein = group('vein', [0, 0, 0], undefined, [0, 45, 0]);
    BLOCKS.forEach(([cell, kind], i) => block('blk_' + i, cell, kind, G.vein));
    block('tnt', TNT, 'tnt', G.vein);
    // ground shock ring, flat on the ground around the TNT's column
    const [rx, rz] = [TNT[0] * 16, TNT[2] * 16];
    for (let f = 0; f < 4; f++) {
      G['ring_' + f] = group('ring_' + f, [rx, 0.4, rz], G.vein);
      cube('ring_plane_' + f, [rx - 16, 0.4, rz - 16], [rx + 16, 0.4, rz + 16], G['ring_' + f], { up: ['fx_ring_' + f, FULL] });
    }

    G.screen = group('screen', O.toArray(), undefined, [PITCH, 0, 0]);
    const L = layout();
    const c = L.tnt;
    fxPlane('spark', [L.fuse.x, L.fuse.y, Z_FX + 20], 7, 'fx_spark');
    for (let f = 0; f < 4; f++) fxPlane('star_' + f, [c.x, c.y, Z_FX + 30], 40, 'fx_star_' + f);
    P.billows.forEach(([dx, dy, shape, k], b) => {
      const at = [c.x + dx, c.y + dy], mir = b === 2;
      fxPlane(`smoke_${b}`, [at[0], at[1], Z_FIRE + b * 5], P.fire * k, 'fx_smoke_' + shape, mir);
      ['red', 'orange', 'yellow', 'white'].forEach((layer, j) =>
        fxPlane(`fire_${layer}_${b}`, [at[0], at[1], Z_FIRE + b * 5 + j + 1], P.fire * k, `fx_fire_${layer}_${shape}`, mir));
    });
    BOOM_CHUNKS.forEach(([tile, , size], k) => chunk('bchunk_' + k, [c.x, c.y, Z_CRUMB + k], size, tile));
    BLOCKS.forEach(([cell, kind], i) => {
      const lc = L.landed[i];
      fxPlane('dust_' + i, [lc.x, lc.y - 8, Z_FX - 10 + i], 16, 'fx_dust');
      fxPlane('poof_' + i, [lc.x, lc.y, Z_FX - 5 + i], 24, 'fx_smoke_' + (i % 2));
      POP_CHUNKS[kind].forEach((tile, k) => chunk(`pchunk_${i}_${k}`, [lc.x, lc.y, Z_CRUMB + 10 + k], 2.6, tile));
      const rb = L.base[i];
      fxPlane('rdust_' + i, [rb.x, rb.y, Z_FX - 15 + i], 20, 'fx_dust', i % 2);
    });
    Canvas.updateAll();
    return Outliner.elements.length;
  }

  function layout() {
    const tnt = blockCentre(TNT);
    const fuse = vs([TNT[0] * 16, TNT[1] * 16 + 16, TNT[2] * 16]);
    const landed = P.land.map(([X, Z]) => worldToScreen([X, 8, Z]));
    const base = BLOCKS.map(([b]) => vs([b[0] * 16, 1, b[2] * 16]));
    return { tnt, fuse, landed, base };
  }

  // ---- animation ----
  let A = null;
  function K(g, ch, t, v, interp) {
    const [x, y, z] = typeof v === 'number' ? [v, v, v] : v;
    A.getBoneAnimator(g).addKeyframe({ channel: ch, time: q(t), interpolation: interp || 'linear', data_points: [{ x, y, z }] });
  }
  const track = (g, ch, keys, interp) => keys.forEach(([t, v, i]) => K(g, ch, t, v, i || interp));
  // linear sample of a key list [[t, v]] (v: number or [x,y,z])
  function sample(keys, t) {
    const vec = v => typeof v === 'number' ? [v, v, v] : v;
    if (t <= keys[0][0]) return vec(keys[0][1]);
    for (let i = 1; i < keys.length; i++) {
      if (t <= keys[i][0] + 1e-9) {
        const [t0, a] = keys[i - 1], [t1, b] = keys[i], u = (t - t0) / (t1 - t0);
        return vec(a).map((v, k) => v + (vec(b)[k] - v) * u);
      }
    }
    return vec(keys[keys.length - 1][1]);
  }
  function pop(g, t, scaleKeys, peak) {
    return [[0, 0], [t - DT, 0], [t, peak * 0.7], [t + DT, peak * 1.2], [t + 2 * DT, peak], ...scaleKeys];
  }

  function ensureG() {
    for (const k in G) delete G[k];
    Group.all.forEach(g => { G[g.name] = g; });
  }
  function animate() {
    own(); ensureTex(); ensureG();
    Animation.all.slice().forEach(a => a.remove(false));
    A = new Animation({ name: 'icon_loop', length: LEN, loop: 'loop', snapping: FPS }).add(false);
    A.select();
    const L = layout(), warnings = [], B = T.boom;
    const z = r => [0, 0, r];

    // TNT: three accelerating pump beats, a fat held breath, gone on the boom; drops back in after the rebuild
    const [b1, b2, b3] = T.pump;
    const tntScale = [
      [0, 1], [b1, 1], [b1 + 0.04, [1.08, 0.9, 1.08]], [b1 + 0.08, [0.94, 1.1, 0.94]], [b1 + 0.12, [1.03, 0.98, 1.03]],
      [b2, [1.03, 0.98, 1.03]], [b2 + 0.04, [1.12, 0.86, 1.12]], [b2 + 0.08, [0.92, 1.16, 0.92]], [b2 + 0.12, [1.05, 0.97, 1.05]],
      [b3, [1.05, 0.97, 1.05]], [b3 + 0.04, [1.16, 0.82, 1.16]], [b3 + 0.08, [1.22, 0.88, 1.22]], [B - 0.04, [1.3, 0.8, 1.3]], [B, 0],
      [T.tntDrop - DT, 0], [T.tntDrop, [0.85, 1.2, 0.85]], [T.tntLand - DT, [0.85, 1.2, 0.85]], [T.tntLand, [1.3, 0.72, 1.3]],
      [T.tntLand + 0.04, [0.9, 1.14, 0.9]], [T.tntLand + 0.08, [1.06, 0.95, 1.06]], [T.tntLand + 0.12, [0.98, 1.02, 0.98]],
      [T.tntLand + 0.20, 1], [LEN, 1],
    ];
    track(G.tnt, 'scale', tntScale);
    const drop = [[0, [0, 0, 0]], [T.tntDrop - DT, [0, 0, 0]]];
    const nd = Math.round((T.tntLand - T.tntDrop) / DT);
    for (let f = 0; f <= nd; f++) drop.push([T.tntDrop + f * DT, [0, 36 * (1 - Math.pow(f / nd, 2)), 0]]);
    track(G.tnt, 'position', drop);
    track(G.tnt_flash, 'scale', [[0, 0], [b1 + 0.08 - DT, 0], [b1 + 0.08, 1], [b1 + 0.08 + DT, 0], [b2 + 0.08 - DT, 0], [b2 + 0.08, 1],
      [b2 + 0.08 + DT, 0], [b3 + 0.08 - DT, 0], [b3 + 0.08, 1], [B - 0.04, 1], [B, 0]]);

    // fuse spark: flickers on the fuse while the TNT is there, riding the pump's height
    const sparkS = [], sparkR = [], sparkP = [];
    const flick = [1, 0.65, 1.15, 0.7];
    for (let f = 0; f <= LEN * FPS; f++) {
      const t = f * DT, on = t < B - 1e-6 || t > T.spark - 1e-6;
      const s = sample(tntScale, t);
      sparkS.push([t, on ? flick[f % 4] : 0]);
      sparkR.push([t, z(f % 2 ? 45 : 0)]);
      sparkP.push([t, [0, 16 * (s[1] - 1) * Math.cos(PITCH * RAD) + 2, 0]]);
    }
    track(G.spark, 'scale', sparkS); track(G.spark, 'rotation', sparkR); track(G.spark, 'position', sparkP);

    // boom: a big star frame shrinking into the fireball
    [1.5, 1.2, 0, 0].forEach((s, f) => track(G['star_' + f], 'scale', [[0, 0], [B + f * DT - DT, 0], [B + f * DT, s], [B + f * DT + DT, 0]]));
    // fireball billows: four flat layers, inner ones collapse first, the red one turns into a smoke puff that rises
    P.billows.forEach(([dx], b) => {
      const d = b ? 0.04 : 0, drift = [Math.sign(dx) * 5, 3, 0];
      const lay = {
        red: [[B + d, 0.8], [B + d + 0.04, 1.1], [B + d + 0.12, 1.2], [B + d + 0.24, 1.12], [B + d + 0.28, 0]],
        orange: [[B + d, 0.7], [B + d + 0.04, 0.95], [B + d + 0.12, 0.92], [B + d + 0.20, 0.55], [B + d + 0.24, 0]],
        yellow: [[B + d, 0.55], [B + d + 0.04, 0.75], [B + d + 0.08, 0.7], [B + d + 0.16, 0]],
        white: [[B + d, 0.45], [B + d + 0.04, 0.5], [B + d + 0.08, 0]],
      };
      for (const [layer, keys] of Object.entries(lay)) {
        const g = G[`fire_${layer}_${b}`];
        track(g, 'scale', [[0, 0], [B + d - DT, 0], ...keys]);
        track(g, 'position', [[B + d, [0, 0, 0]], [B + d + 0.28, drift]]);
      }
      const s0 = B + d + 0.28, sm = G['smoke_' + b];
      track(sm, 'scale', [[0, 0], [s0 - DT, 0], [s0, 1.1], [s0 + 0.12, 1.0], [s0 + 0.28, 0.65], [s0 + 0.40, 0.3], [s0 + 0.44, 0]]);
      track(sm, 'position', [[s0, drift], [s0 + 0.44, [drift[0] * 1.8, drift[1] + 12, 0]]]);
    });
    // ground shock ring
    [0, 1.3, 1.8, 2.2].forEach((s, f) => track(G['ring_' + f], 'scale', [[0, 0], [B + (f - 1) * DT, 0], [B + f * DT, [s, 1, s]], [B + (f + 1) * DT, 0]]));
    // boom crumbs
    BOOM_CHUNKS.forEach(([, ang, size], k) => {
      const g = G['bchunk_' + k], sp = 90 + 20 * (k % 3), grav = 700, t0 = B + DT;
      const vx = Math.cos(ang * RAD) * sp, vy = Math.sin(ang * RAD) * sp + 40;
      const pos = [], rot = [];
      for (let f = 0; f <= 12; f++) {
        const t = f * DT;
        pos.push([t0 + t, [vx * t, vy * t - grav * t * t / 2, 0]]);
        rot.push([t0 + t, [f * 35, 0, f * (k % 2 ? 50 : -50)]]);
      }
      track(g, 'position', pos); track(g, 'rotation', rot);
      track(g, 'scale', [[0, 0], [B, 0], [t0, 1], [t0 + 0.32, 1], [t0 + 0.44, 0.5], [t0 + 0.48, 0]]);
    });

    // blocks: flash + hit-stop on the boom, launch ripple, tumbling arcs, squash-bounce landings, pops, rebuild
    BLOCKS.forEach(([cell, kind], i) => {
      const g = G['blk_' + i], spin = G['blk_' + i + '_spin'], tl = T.launch[i], n = T.flight[i], Tf = n * DT;
      const [lx, lz] = worldToVein(...P.land[i]);
      const d = [lx - cell[0] * 16, lz - cell[2] * 16], H = P.apex[i];
      const grav = 8 * H / (Tf * Tf), vy = 4 * H / Tf;
      const pos = [[0, [0, 0, 0]], [tl, [0, 0, 0]]];
      for (let f = 1; f <= n; f++) {
        const t = f * DT;
        pos.push([tl + t, [d[0] * f / n, vy * t - grav * t * t / 2, d[1] * f / n]]);
      }
      const end = [d[0], 0, d[1]];
      const at = (dy) => [end[0], dy, end[2]];
      let tb = tl + Tf;
      const sc = [[0, 1], [B - DT, 1], [B, [1.08, 0.9, 1.08]], [tl, [1.1, 0.86, 1.1]], [tl + DT, [0.84, 1.3, 0.84]], [tl + 3 * DT, [0.95, 1.08, 0.95]],
        [tl + 5 * DT, 1], [tb - DT, [0.92, 1.1, 0.92]], [tb, [1.3, 0.68, 1.3]]];
      P.bounce.forEach(([h, m], j) => {
        for (let f = 1; f <= m; f++) { const u = f / m; pos.push([tb + f * DT, at(4 * h * u * (1 - u))]); }
        sc.push([tb + DT, j ? [0.96, 1.05, 0.96] : [0.88, 1.16, 0.88]]);
        if (m > 2) sc.push([tb + 2 * DT, 1]);
        tb += m * DT;
        sc.push([tb, j ? [1.1, 0.9, 1.1] : [1.2, 0.8, 1.2]]);
      });
      sc.push([tb + DT, [0.97, 1.03, 0.97]], [tb + 2 * DT, 1]);
      const tp = T.pops[i];
      if (tb + 2 * DT > tp - 0.12 + 1e-6) warnings.push(`block ${i} still bouncing at its pop (${tb.toFixed(2)})`);
      // pop: squash 3 frames, snap stretch 1, vanish 2
      sc.push([tp - 0.12, 1], [tp - 0.04, [1.22, 0.78, 1.22]], [tp, [0.8, 1.32, 0.8]], [tp + 0.04, [0.45, 0.6, 0.45]], [tp + 0.08, 0]);
      // rebuild: out of the ground in its own cell with an elastic boing
      const tr = T.rise[i];
      sc.push([tr, 0], [tr + 0.04, [0.8, 1.3, 0.8]], [tr + 0.08, [1.25, 0.78, 1.25]], [tr + 0.12, [0.92, 1.1, 0.92]],
        [tr + 0.16, [1.04, 0.97, 1.04]], [tr + 0.24, 1], [LEN, 1]);
      pos.push([tp + 0.08, at(0)], [tr - DT, at(0)], [tr, [0, 0, 0]], [LEN, [0, 0, 0]]);
      track(g, 'position', pos);
      track(g, 'scale', sc);
      // tumble one full turn about the axis across the flight, landing square
      const ax = Math.abs(d[0]) > Math.abs(d[1]) ? 2 : 0, sgn = ax === 2 ? -Math.sign(d[0]) : Math.sign(d[1]);
      const rot = [[0, [0, 0, 0]], [tl, [0, 0, 0]]];
      for (let f = 1; f <= n; f++) {
        const r = [0, 0, 0]; r[ax] = sgn * 360 * Math.pow(f / n, 0.8);
        rot.push([tl + f * DT, f === n ? [0, 0, 0] : r]);
      }
      track(spin, 'rotation', rot);
      track(G['blk_' + i + '_flash'], 'scale', [[0, 0], [B - DT, 0], [B, 1], [B + DT, 1], [B + 2 * DT, 0]]);

      const tland = tl + Tf;
      track(G['dust_' + i], 'scale', [[0, 0], [tland - DT, 0], [tland, 0.8], [tland + DT, 1.2], [tland + 2 * DT, 0.9], [tland + 3 * DT, 0.5], [tland + 4 * DT, 0]]);
      track(G['dust_' + i], 'position', [[tland, [0, 0, 0]], [tland + 4 * DT, [0, 3, 0]]]);
      track(G['poof_' + i], 'scale', [[0, 0], [tp, 0], [tp + DT, 0.7], [tp + 2 * DT, 1.2], [tp + 3 * DT, 1.05], [tp + 4 * DT, 0.6], [tp + 5 * DT, 0]]);
      track(G['poof_' + i], 'position', [[tp, [0, 0, 0]], [tp + 5 * DT, [0, 6, 0]]]);
      POP_CHUNKS[kind].forEach((tile, k) => {
        const cg = G[`pchunk_${i}_${k}`], ang = 40 + k * 50 + i * 17, sp = 45 + 12 * k, grav = 700, t0 = tp + DT;
        const vx = Math.cos(ang * RAD) * sp, vyc = Math.sin(ang * RAD) * sp + 30;
        const cp = [], cr = [];
        for (let f = 0; f <= 8; f++) {
          const t = f * DT;
          cp.push([t0 + t, [vx * t, vyc * t - grav * t * t / 2, 0]]);
          cr.push([t0 + t, [f * 40, 0, f * (k % 2 ? 55 : -55)]]);
        }
        track(cg, 'position', cp); track(cg, 'rotation', cr);
        track(cg, 'scale', [[0, 0], [tp, 0], [t0, 1], [t0 + 0.2, 1], [t0 + 0.28, 0.5], [t0 + 0.32, 0]]);
      });
      track(G['rdust_' + i], 'scale', [[0, 0], [tr, 0], [tr + DT, 0.9], [tr + 2 * DT, 1.15], [tr + 3 * DT, 0.8], [tr + 4 * DT, 0]]);
    });
    Animator.preview();
    return warnings.length ? warnings.join('; ') : 'ok';
  }

  // ---- camera + render ----
  function camera(zoom, pan) {
    own();
    const p = Preview.selected;
    p.setProjectionMode(true);
    const pn = pan || CAM_PAN;
    const pos = CAM_POS.map((v, i) => v + pn[i]), tgt = CAM_TARGET.map((v, i) => v + pn[i]);
    p.camera.position.set(...pos);
    p.controls.target.set(...tgt);
    p.camera.lookAt(...tgt);
    p.camera.zoom = zoom || CAM_ZOOM; p.camera.updateProjectionMatrix();
    p.controls.update();
  }
  function setTime(t) {
    Timeline.setTime(t);
    Animator.preview();
  }
  function render(first, last, res, dir) {
    res = res || 1600;
    dir = dir || DIR + 'frames/';
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    const shot = f => new Promise(done => {
      own();
      setTime(f * DT);
      Screencam.advancedScreenshot(Preview.selected, { angle_preset: 'view', resolution: [res, res], anti_aliasing: 'none', shading: false }, url => {
        fs.writeFileSync(dir + 'frame_' + String(f).padStart(3, '0') + '.png', Buffer.from(url.split(',')[1], 'base64'));
        done();
      });
    });
    if (lockedBy()) return 'Blockbench is locked by ' + lockedBy();
    window.BB_LOCK = { owner: ME, until: Date.now() + 300000 };
    return (async () => {
      try { for (let f = first; f <= last; f++) await shot(f); } finally { if (window.BB_LOCK && window.BB_LOCK.owner === ME) delete window.BB_LOCK; }
      return `rendered ${first}..${last}`;
    })();
  }
  function scaleSweep() {
    own();
    const bad = [];
    for (let f = 0; f <= Math.round(LEN * FPS); f++) {
      setTime(f * DT);
      Group.all.forEach(g => { const s = g.mesh.scale; if (s.x < 0 || s.y < 0 || s.z < 0) bad.push(g.name + '@' + f); });
    }
    return bad.length ? bad.join(',') : 'no negative scale';
  }
  function probe(name, t) {
    own(); setTime(t);
    const v = new THREE.Vector3();
    G[name].mesh.getWorldPosition(v);
    return v.toArray().map(n => Math.round(n * 10) / 10);
  }
  function setCam(zoom, pan) { CAM_ZOOM = zoom; CAM_PAN = pan; }

  return { own, lockedBy, FPS, DT, LEN, T, P, PITCH, G, tex, loadTextures, build, animate, camera, setCam, render, setTime,
    scaleSweep, probe, layout, worldToScreen, veinToWorld, worldToVein, blockCentre, q };
})();
