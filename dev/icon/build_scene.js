// Having a Blast icon scene + animation. Run inside Blockbench (free format project named PROJECT):
//   eval(require('fs').readFileSync('<this file>', 'utf8')); window.HB = HB; HB.loadTextures()   // then, in a later call:
//   HB.build(); HB.animate(); HB.camera()                                                          // then:
//   HB.render(first, last)                                                                         // 1600px frames -> frames/
// The ground is the background itself: every surface that belongs to the floor is textured with the key colour
// (sprites/key.png, #FF00FF), which the compositors turn transparent. So the scene starts as a TNT on plain blue; the
// blast knocks a 3x3 dirt hole into that "floor", small dirt pieces fly out, bounce and pop, and the hole fills back up.
// Blocks live in the `vein` group (yawed 45 deg, grid units x16, floor at y 0). Everything flat (fire, smoke, star,
// sparks, poofs, crumbs) lives in the `screen` group, tilted to face the orthographic camera: x right, y up, z out.
var HB = (function () {
  const fs = require('fs');
  const DIR = '/home/emppu/Projects/Minecraft Datapacks/HavingABlast/dev/icon/';
  const TEX = DIR + 'sprites/';
  const FPS = 25, DT = 1 / FPS, LEN = 3.2;
  const CAM_POS = [0, 75, 96], CAM_TARGET = [0, 16, 0];   // ~31.6 deg down, steep enough to see into the hole
  let CAM_PAN = [0, 4, 0], CAM_ZOOM = 0.2;
  const PITCH = -Math.atan2(CAM_POS[1] - CAM_TARGET[1], CAM_POS[2] - CAM_TARGET[2]) * 180 / Math.PI;
  const O = new THREE.Vector3(...CAM_TARGET);
  const RAD = Math.PI / 180;
  const Z_FX = 60, Z_FIRE = 70, Z_CRUMB = 45;

  // the hole: 3x3 cells, one block deep. Each removed block becomes one small debris piece, like the mod
  // (size in units, landing spot x/z in vein units, launch, flight frames, apex, pop, rise).
  // The ring of floor cells around it is solid ground: key-coloured outside, dirt where it faces the hole.
  const HOLE = [];
  for (let x = -1; x <= 1; x++) for (let z = -1; z <= 1; z++) HOLE.push([x, z]);
  const isHole = (x, z) => Math.abs(x) <= 1 && Math.abs(z) <= 1;
  const PIECES = {
    '0,0':   { s: 9,  land: [-8, -33], launch: 1.04, n: 15, apex: 44, pop: 2.48, rise: 2.80 },
    '0,-1':  { s: 8,  land: [2, -38],   launch: 1.08, n: 14, apex: 36, pop: 2.28, rise: 2.72 },
    '1,0':   { s: 10, land: [36, 5],    launch: 1.08, n: 13, apex: 30, pop: 2.08, rise: 2.72 },
    '0,1':   { s: 8,  land: [5, 36],    launch: 1.08, n: 13, apex: 32, pop: 2.12, rise: 2.72 },
    '-1,0':  { s: 9,  land: [-38, 2],   launch: 1.08, n: 14, apex: 34, pop: 2.20, rise: 2.72 },
    '-1,-1': { s: 7,  land: [-29, -26], launch: 1.12, n: 14, apex: 38, pop: 2.36, rise: 2.64 },
    '1,-1':  { s: 9,  land: [27, -30],  launch: 1.12, n: 13, apex: 34, pop: 2.44, rise: 2.64 },
    '1,1':   { s: 7,  land: [30, 27],   launch: 1.12, n: 12, apex: 28, pop: 2.52, rise: 2.64 },
    '-1,1':  { s: 10, land: [-27, 30],  launch: 1.12, n: 13, apex: 30, pop: 2.56, rise: 2.64 },
  };
  const TNT = [0, 0, 0];

  const T = {
    tntDrop: 0.08, tntLand: 0.24, spark: 0.28,
    pump: [0.52, 0.68, 0.80], boom: 0.96,
  };
  const P = {
    bounce: [[4, 4], [1.4, 3]],
    billows: [[0, 12, 0, 1.0], [-16, 1, 1, 0.9], [16, 2, 0, 0.9]],   // screen offset from the TNT centre, shape, size
    fire: 54,
  };

  const q = t => Math.round(t * FPS) / FPS;
  const worldToScreen = w => new THREE.Vector3(...w).sub(O).applyEuler(new THREE.Euler(-PITCH * RAD, 0, 0)).add(O);
  const veinToWorld = a => new THREE.Vector3(...a).applyEuler(new THREE.Euler(0, 45 * RAD, 0));
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
    for (const f of fs.readdirSync(TEX).filter(f => f.endsWith('.png') && !/^(bg|banner|review|tnt_item|boom_item)/.test(f))) {
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
      const spec = f in faceTex ? faceTex[f] : faceTex.all;
      if (spec) c.faces[f].extend({ texture: tex[spec[0]].uuid, uv: spec[1] });
      else c.faces[f].extend({ texture: null });
    }
    return c;
  }
  function plane(name, centre, size, parent, texName, mirror) {
    const [x, y, z] = centre, h = size / 2;
    return cube(name, [x - h, y - h, z], [x + h, y + h, z], parent, { south: [texName, mirror ? [16, 0, 0, 16] : [0, 0, 16, 16]] });
  }
  const FULL = [0, 0, 16, 16], KEY = ['key', FULL];
  // the side a face shows: west/east one ramp step darker, south/north two (the render uses shading:false)
  const SIDE = { west: '_left', east: '_left', south: '_right', north: '_right', down: '_right' };
  const dirtFace = f => [f === 'up' ? 'dirt' : 'dirt' + SIDE[f], FULL];
  const DIRS = { west: [-1, 0], east: [1, 0], north: [0, -1], south: [0, 1] };
  const tntFaces = (flash) => flash ? { up: ['tnt_top_flash', FULL], all: ['tnt_side_flash', FULL] }
    : { up: ['tnt_top', FULL], down: ['tnt_side_right', FULL], west: ['tnt_side_left', FULL], east: ['tnt_side_left', FULL],
        south: ['tnt_side_right', FULL], north: ['tnt_side_right', FULL] };

  const G = {};
  function fxPlane(name, at, size, texName, mirror) {
    G[name] = group(name, at, G.screen);
    plane(name + '_plane', at, size, G[name], texName, mirror);
  }
  function chunk(name, at, size, tile) {
    G[name] = group(name, at, G.screen);
    const h = size / 2;
    cube(name + '_cube', [at[0] - h, at[1] - h, at[2] - h], [at[0] + h, at[1] + h, at[2] + h], G[name], { all: ['chunks', CHUNK[tile]] });
  }
  const CHUNK = { grass: [0, 0, 4, 4], dirt: [4, 0, 8, 4], stone: [8, 0, 12, 4], tnt: [12, 0, 16, 4] };
  const BOOM_CHUNKS = [['dirt', 100, 3.4], ['tnt', 70, 3.0], ['dirt', 125, 3.2], ['dirt', 40, 2.8], ['tnt', 150, 2.6], ['dirt', 170, 3.0]];
  const POP_CHUNKS = ['dirt', 'dirt', 'dirt'];
  const key = (x, z) => x + ',' + z;

  function build() {
    own(); ensureTex();
    Animation.all.slice().forEach(a => a.remove(false));
    Outliner.root.slice().forEach(n => n.remove(false));
    G.vein = group('vein', [0, 0, 0], undefined, [0, 45, 0]);

    // ground ring, two blocks deep: key everywhere except the faces that look into the hole
    for (let x = -2; x <= 2; x++) for (let z = -2; z <= 2; z++) {
      if (isHole(x, z)) continue;
      const f = { up: KEY, down: KEY };
      for (const [d, [dx, dz]] of Object.entries(DIRS)) f[d] = isHole(x + dx, z + dz) ? dirtFace(d) : KEY;
      const lower = Object.assign({}, f, Object.fromEntries(Object.keys(DIRS).map(d => [d, KEY])));
      cube(`ring_${x}_${z}`, [x * 16 - 8, -16, z * 16 - 8], [x * 16 + 8, 0, z * 16 + 8], G.vein, f);
      cube(`ring_lo_${x}_${z}`, [x * 16 - 8, -32, z * 16 - 8], [x * 16 + 8, -16, z * 16 + 8], G.vein, lower);
    }
    // the hole: its dirt floor one block down, and the blocks that fill it (floor-coloured top while flush)
    for (const [x, z] of HOLE) {
      cube(`bottom_${x}_${z}`, [x * 16 - 8, -32, z * 16 - 8], [x * 16 + 8, -16, z * 16 + 8], G.vein,
        { up: dirtFace('up'), down: KEY, west: KEY, east: KEY, north: KEY, south: KEY });
      const g = G[`fill_${key(x, z)}`] = group(`fill_${key(x, z)}`, [x * 16, -16, z * 16], G.vein);
      const sides = Object.fromEntries(Object.keys(DIRS).map(d => [d, dirtFace(d)]));
      G[`flush_${key(x, z)}`] = group(`flush_${key(x, z)}`, [x * 16, -8, z * 16], g);
      cube(`flush_cube_${x}_${z}`, [x * 16 - 8, -16, z * 16 - 8], [x * 16 + 8, 0, z * 16 + 8], G[`flush_${key(x, z)}`],
        Object.assign({ up: KEY, down: null }, sides));
      G[`raw_${key(x, z)}`] = group(`raw_${key(x, z)}`, [x * 16, -8, z * 16], g);
      cube(`raw_cube_${x}_${z}`, [x * 16 - 8, -16, z * 16 - 8], [x * 16 + 8, 0, z * 16 + 8], G[`raw_${key(x, z)}`],
        Object.assign({ up: dirtFace('up'), down: null }, sides));
    }
    // debris: one small dirt piece per removed block, pivoting at its bottom centre (squash stays grounded)
    for (const [x, z] of HOLE) {
      const p = PIECES[key(x, z)], h = p.s / 2, c = [x * 16, -8 - h, z * 16];
      const g = G[`piece_${key(x, z)}`] = group(`piece_${key(x, z)}`, c, G.vein);
      const sp = G[`spin_${key(x, z)}`] = group(`spin_${key(x, z)}`, [c[0], c[1] + h, c[2]], g);
      const f = Object.fromEntries(FACES.map(d => [d, dirtFace(d)]));
      cube(`piece_cube_${x}_${z}`, [c[0] - h, c[1], c[2] - h], [c[0] + h, c[1] + p.s, c[2] + h], sp, f);
      const fl = G[`pflash_${key(x, z)}`] = group(`pflash_${key(x, z)}`, [c[0], c[1] + h, c[2]], sp);
      cube(`pflash_cube_${x}_${z}`, [c[0] - h, c[1], c[2] - h], [c[0] + h, c[1] + p.s, c[2] + h], fl, { all: ['dirt_flash', FULL] }, { inflate: 0.25 });
    }
    // TNT
    const [tx, , tz] = TNT.map(v => v * 16);
    G.tnt = group('tnt', [tx, 0, tz], G.vein);
    cube('tnt_cube', [tx - 8, 0, tz - 8], [tx + 8, 16, tz + 8], G.tnt, tntFaces());
    G.tnt_flash = group('tnt_flash', [tx, 8, tz], G.tnt);
    cube('tnt_flash_cube', [tx - 8, 0, tz - 8], [tx + 8, 16, tz + 8], G.tnt_flash, tntFaces(true), { inflate: 0.35 });
    // floor shadows: flat discs in the second key colour (#00FFFF), painted in the floor's shadow blue by the compositors
    const shadowDisc = (name, cx, cz, d) => {
      G[name] = group(name, [cx, 0.3, cz], G.vein);
      cube(name + '_plane', [cx - d / 2, 0.3, cz - d / 2], [cx + d / 2, 0.3, cz + d / 2], G[name], { up: ['shadow', FULL] });
    };
    // wider than the footprint and nudged toward the front-right (+z), so they show past the outline
    shadowDisc('shadow_tnt', tx, tz + 5, 38);
    for (const [x, z] of HOLE) { const s = PIECES[key(x, z)].s; shadowDisc(`shadow_${key(x, z)}`, x * 16, z * 16 + s * 0.35, s * 2.3); }
    // shock ring on the floor
    for (let f = 0; f < 4; f++) {
      G['ring_' + f] = group('ring_' + f, [tx, 0.4, tz], G.vein);
      cube('ring_plane_' + f, [tx - 16, 0.4, tz - 16], [tx + 16, 0.4, tz + 16], G['ring_' + f], { up: ['fx_ring_' + f, FULL] });
    }

    G.screen = group('screen', O.toArray(), undefined, [PITCH, 0, 0]);
    const L = layout();
    const c = L.tnt;
    fxPlane('spark', [L.fuse.x, L.fuse.y, Z_FX + 20], 7, 'fx_spark');
    for (let f = 0; f < 2; f++) fxPlane('star_' + f, [c.x, c.y, Z_FX + 30], 40, 'fx_star_' + f);
    P.billows.forEach(([dx, dy, shape, k], b) => {
      const at = [c.x + dx, c.y + dy], mir = b === 2;
      fxPlane(`smoke_${b}`, [at[0], at[1], Z_FIRE + b * 5], P.fire * k, 'fx_smoke_' + shape, mir);
      ['red', 'orange', 'yellow', 'white'].forEach((layer, j) =>
        fxPlane(`fire_${layer}_${b}`, [at[0], at[1], Z_FIRE + b * 5 + j + 1], P.fire * k, `fx_fire_${layer}_${shape}`, mir));
    });
    BOOM_CHUNKS.forEach(([tile, , size], k) => chunk('bchunk_' + k, [c.x, c.y, Z_CRUMB + k], size, tile));
    HOLE.forEach(([x, z], i) => {
      const lc = L.landed[i];
      fxPlane('dust_' + i, [lc.x, lc.bottom, Z_FX - 10 + i], 12, 'fx_dust');
      fxPlane('poof_' + i, [lc.x, lc.y, Z_FX - 5 + i], 18, 'fx_smoke_' + (i % 2));
      POP_CHUNKS.forEach((tile, k) => chunk(`pchunk_${i}_${k}`, [lc.x, lc.y, Z_CRUMB + 10 + k], 2.2, tile));
      const rb = L.base[i];
      fxPlane('rdust_' + i, [rb.x, rb.y, Z_FX - 15 + i], 16, 'fx_dust', i % 2);
    });
    Canvas.updateAll();
    return Outliner.elements.length;
  }

  function layout() {
    const tnt = blockCentre(TNT);
    const fuse = vs([TNT[0] * 16, 16, TNT[2] * 16]);
    const landed = HOLE.map(([x, z]) => {
      const p = PIECES[key(x, z)], m = vs([p.land[0], p.s / 2, p.land[1]]);
      return { x: m.x, y: m.y, bottom: vs([p.land[0], 0.5, p.land[1]]).y };
    });
    const base = HOLE.map(([x, z]) => vs([x * 16, 1, z * 16]));
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
    keys = keys.slice().sort((a, b) => a[0] - b[0]);
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
    const onGrid = (name, t) => { if (Math.abs(t * FPS - Math.round(t * FPS)) > 1e-6) warnings.push(`${name} ${t} is off the frame grid`); };
    for (const [k, v] of Object.entries(T)) [].concat(v).forEach(t => onGrid('T.' + k, t));
    for (const [k, p] of Object.entries(PIECES)) ['launch', 'pop', 'rise'].forEach(f => onGrid(`${k}.${f}`, p[f]));

    // TNT: drops in, three accelerating pump beats, a fat held breath, gone on the boom
    const [b1, b2, b3] = T.pump, tl0 = T.tntLand;
    const tntScale = [
      [0, 0], [T.tntDrop - DT, 0], [T.tntDrop, [0.85, 1.2, 0.85]], [tl0 - DT, [0.85, 1.2, 0.85]], [tl0, [1.3, 0.72, 1.3]],
      [tl0 + 0.04, [0.9, 1.14, 0.9]], [tl0 + 0.08, [1.06, 0.95, 1.06]], [tl0 + 0.12, [0.98, 1.02, 0.98]], [tl0 + 0.20, 1],
      [b1, 1], [b1 + 0.04, [1.08, 0.9, 1.08]], [b1 + 0.08, [0.94, 1.1, 0.94]], [b1 + 0.12, [1.03, 0.98, 1.03]],
      [b2, [1.03, 0.98, 1.03]], [b2 + 0.04, [1.12, 0.86, 1.12]], [b2 + 0.08, [0.92, 1.16, 0.92]], [b2 + 0.12, [1.05, 0.97, 1.05]],
      [b3, [1.05, 0.97, 1.05]], [b3 + 0.04, [1.16, 0.82, 1.16]], [b3 + 0.08, [1.22, 0.88, 1.22]], [B - 0.04, [1.3, 0.8, 1.3]], [B, 0], [LEN, 0],
    ];
    track(G.tnt, 'scale', tntScale);
    const drop = [[0, [0, 40, 0]]];
    const nd = Math.round((T.tntLand - T.tntDrop) / DT);
    for (let f = 0; f <= nd; f++) drop.push([T.tntDrop + f * DT, [0, 40 * (1 - Math.pow(f / nd, 2)), 0]]);
    track(G.tnt, 'position', drop);
    track(G.tnt_flash, 'scale', [[0, 0], [b1 + 0.08 - DT, 0], [b1 + 0.08, 1], [b1 + 0.08 + DT, 0], [b2 + 0.08 - DT, 0], [b2 + 0.08, 1],
      [b2 + 0.08 + DT, 0], [b3 + 0.08 - DT, 0], [b3 + 0.08, 1], [B - 0.04, 1], [B, 0]]);

    // fuse spark: flickers on the fuse while the TNT sits there, riding the pump's height
    const sparkS = [], sparkR = [], sparkP = [];
    const flick = [1, 0.65, 1.15, 0.7];
    for (let f = 0; f <= LEN * FPS; f++) {
      const t = f * DT, on = t > T.spark - 1e-6 && t < B - 1e-6;
      const s = sample(tntScale, t);
      sparkS.push([t, on ? flick[f % 4] : 0]);
      sparkR.push([t, z(f % 2 ? 45 : 0)]);
      sparkP.push([t, [0, 16 * (s[1] - 1) * Math.cos(PITCH * RAD) + 2, 0]]);
    }
    track(G.spark, 'scale', sparkS); track(G.spark, 'rotation', sparkR); track(G.spark, 'position', sparkP);
    // the TNT's shadow: small and faint high up, full size on the floor, riding the pump's squash
    const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
    const tntShadow = [];
    for (let f = 0; f <= LEN * FPS; f++) {
      const t = f * DT, sx = sample(tntScale, t)[0], y = sample(drop, t)[1];
      const k = sx > 0.01 ? sx * clamp(1 - y / 50, 0.3, 1) : 0;
      tntShadow.push([t, [k, 1, k]]);
    }
    track(G.shadow_tnt, 'scale', tntShadow);

    // boom: a big star frame shrinking into the fireball
    [1.5, 1.2].forEach((s, f) => track(G['star_' + f], 'scale', [[0, 0], [B + f * DT - DT, 0], [B + f * DT, s], [B + f * DT + DT, 0]]));
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
    [0, 1.3, 1.8, 2.2].forEach((s, f) => track(G['ring_' + f], 'scale', [[0, 0], [B + (f - 1) * DT, 0], [B + f * DT, [s, 1, s]], [B + (f + 1) * DT, 0]]));
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

    HOLE.forEach(([x, zc], i) => {
      const p = PIECES[key(x, zc)], k = key(x, zc);
      // the floor block: gone on the boom (the hole opens under the star), back in the rebuild with an elastic boing;
      // it shows a dirt top while it moves and the floor colour once it sits flush again
      const tr = p.rise;
      track(G['fill_' + k], 'scale', [[0, 1], [B - DT, 1], [B, 0], [tr, 0], [tr + 0.04, [0.8, 1.3, 0.8]], [tr + 0.08, [1.25, 0.78, 1.25]],
        [tr + 0.12, [0.92, 1.1, 0.92]], [tr + 0.16, [1.04, 0.97, 1.04]], [tr + 0.24, 1], [LEN, 1]]);
      track(G['flush_' + k], 'scale', [[0, 1], [tr - DT, 1], [tr, 0], [tr + 0.24 - DT, 0], [tr + 0.24, 1]]);
      track(G['raw_' + k], 'scale', [[0, 0], [tr - DT, 0], [tr, 1], [tr + 0.24 - DT, 1], [tr + 0.24, 0]]);
      track(G['rdust_' + i], 'scale', [[0, 0], [tr, 0], [tr + DT, 0.9], [tr + 2 * DT, 1.15], [tr + 3 * DT, 0.8], [tr + 4 * DT, 0]]);

      // the debris piece: appears on the boom in its block's middle, flashes and holds, launches, tumbles, bounces, pops
      const g = G['piece_' + k], spin = G['spin_' + k], tl = p.launch, n = p.n, Tf = n * DT, h = p.s / 2;
      const d = [p.land[0] - x * 16, p.land[1] - zc * 16], dy = 0 - (-8 - h), H = p.apex;
      const grav = Math.pow((Math.sqrt(2 * H) + Math.sqrt(2 * (H - dy))) / Tf, 2), vy = Math.sqrt(2 * grav * H);
      const pos = [[0, [0, 0, 0]], [tl, [0, 0, 0]]];
      for (let f = 1; f <= n; f++) {
        const t = f * DT;
        pos.push([tl + t, [d[0] * f / n, vy * t - grav * t * t / 2, d[1] * f / n]]);
      }
      const at = y => [d[0], dy + y, d[1]];
      let tb = tl + Tf;
      const sc = [[0, 0], [B - DT, 0], [B, 1], [tl, [1.1, 0.88, 1.1]], [tl + DT, [0.82, 1.35, 0.82]], [tl + 3 * DT, [0.95, 1.08, 0.95]],
        [tl + 5 * DT, 1], [tb - DT, [0.9, 1.12, 0.9]], [tb, [1.35, 0.65, 1.35]]];
      P.bounce.forEach(([bh, m], j) => {
        for (let f = 1; f <= m; f++) { const u = f / m; pos.push([tb + f * DT, at(4 * bh * u * (1 - u))]); }
        sc.push([tb + DT, j ? [0.96, 1.05, 0.96] : [0.86, 1.18, 0.86]]);
        if (m > 2) sc.push([tb + 2 * DT, 1]);
        tb += m * DT;
        sc.push([tb, j ? [1.1, 0.9, 1.1] : [1.22, 0.78, 1.22]]);
      });
      sc.push([tb + DT, [0.97, 1.03, 0.97]], [tb + 2 * DT, 1]);
      const tp = p.pop;
      if (tb + 2 * DT > tp - 0.12 + 1e-6) warnings.push(`piece ${k} still bouncing at its pop (${tb.toFixed(2)} > ${(tp - 0.12).toFixed(2)})`);
      sc.push([tp - 0.12, 1], [tp - 0.04, [1.22, 0.78, 1.22]], [tp, [0.8, 1.32, 0.8]], [tp + 0.04, [0.45, 0.6, 0.45]], [tp + 0.08, 0], [LEN, 0]);
      pos.push([tp + 0.08, at(0)], [LEN, at(0)]);
      track(g, 'position', pos);
      track(g, 'scale', sc);
      const ax = Math.abs(d[0]) > Math.abs(d[1]) ? 2 : 0, sgn = ax === 2 ? -Math.sign(d[0]) : (Math.sign(d[1]) || 1);
      const rot = [[0, [0, 0, 0]], [tl, [0, 0, 0]]];
      for (let f = 1; f <= n; f++) {
        const r = [0, 0, 0]; r[ax] = sgn * 360 * Math.pow(f / n, 0.8);
        rot.push([tl + f * DT, f === n ? [0, 0, 0] : r]);
      }
      track(spin, 'rotation', rot);
      track(G['pflash_' + k], 'scale', [[0, 0], [B - DT, 0], [B, 1], [B + DT, 1], [B + 2 * DT, 0]]);
      // its shadow follows on the floor, shrinks with height and squashes with the piece; hidden over the hole
      const shPos = [], shScale = [], r = p.s * 1.15;
      for (let f = 0; f <= LEN * FPS; f++) {
        const t = f * DT, pp = sample(pos, t), ps = sample(sc, t);
        const wx = x * 16 + pp[0], wz = zc * 16 + p.s * 0.35 + pp[2], yb = -8 - h + pp[1];
        const ox = Math.max(Math.abs(wx) - 24, 0), oz = Math.max(Math.abs(wz) - 24, 0);
        const vis = ps[0] > 0.01 && yb > -1 && ox * ox + oz * oz >= r * r;
        const kk = vis ? ps[0] * clamp(1 - yb / 50, 0.35, 1) : 0;
        shPos.push([t, [pp[0], 0, pp[2]]]);
        shScale.push([t, [kk, 1, kk]]);
      }
      track(G['shadow_' + k], 'position', shPos);
      track(G['shadow_' + k], 'scale', shScale);

      const tland = tl + Tf;
      track(G['dust_' + i], 'scale', [[0, 0], [tland - DT, 0], [tland, 0.8], [tland + DT, 1.2], [tland + 2 * DT, 0.9], [tland + 3 * DT, 0.5], [tland + 4 * DT, 0]]);
      track(G['dust_' + i], 'position', [[tland, [0, 0, 0]], [tland + 4 * DT, [0, 3, 0]]]);
      popFx(i, tp);
    });
    Animator.preview();
    return warnings.length ? warnings.join('; ') : 'ok';
  }
  function popFx(i, tp) {
    track(G['poof_' + i], 'scale', [[0, 0], [tp, 0], [tp + DT, 0.7], [tp + 2 * DT, 1.2], [tp + 3 * DT, 1.05], [tp + 4 * DT, 0.6], [tp + 5 * DT, 0]]);
    track(G['poof_' + i], 'position', [[tp, [0, 0, 0]], [tp + 5 * DT, [0, 6, 0]]]);
    POP_CHUNKS.forEach((tile, k) => {
      const cg = G[`pchunk_${i}_${k}`], ang = 40 + k * 50 + i * 17, sp = 40 + 10 * k, grav = 700, t0 = tp + DT;
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
    scaleSweep, probe, layout, worldToScreen, veinToWorld, blockCentre, q };
})();
