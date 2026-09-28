-- Having a Blast icon sprites. Run through the aseprite MCP: dofile("<abs>/HavingABlast/dev/icon/draw_sprites.lua")
-- The fire, smoke, star and ring FX come from the mod's own fx.png (dev/icon/fx_sprites.py), not from here.
dofile("/home/emppu/Projects/Minecraft Datapacks/.claude/skills/pack-icon-animation/assets/pixel_art.lua")
local OUT = "/home/emppu/Projects/Minecraft Datapacks/HavingABlast/dev/icon/sprites/"

local leaf = { Z = "#0F3325", p = "#17492D", q = "#236B33", r = "#378F38", s = "#5BB23E", t = "#8ED14E", u = "#C3EA6C" }
local dirt = { X = "#33190F", a = "#4A2A1C", b = "#6B3F27", c = "#8C5A36", d = "#AD7646", e = "#C99760" }
local stone = { Y = "#2C2B40", a = "#3E3D56", b = "#5A5A77", c = "#797C94", d = "#9EA1AC", e = "#C1C3C7", f = "#E2E2E3" }
local red = { Q = "#2E0716", O = "#4A0B24", A = "#8E1530", B = "#C9272E", C = "#E84A3A", D = "#FF7F5E" }
local band = { y = "#4F4868", x = "#736B8A", v = "#9A93B4", g = "#C4BED6", w = "#ECE8F2", W = "#FFFFFF" }
local ink = { M = "#0E0B20", N = "#1A1538", n = "#2A2350" }
local fuse = { k = "#7A766C", L = "#C8C4B8" }
local function merge(...)
  local out = {}
  for _, t in ipairs({ ... }) do for k, v in pairs(t) do out[k] = v end end
  return out
end
-- one and two ramp steps darker, for the left (west) and right (south) faces; the render uses shading:false
local function steps(ramps, n)
  local m = {}
  for _, ro in ipairs(ramps) do
    local ramp, order = ro[1], ro[2]
    for i = 1, #order do
      m[ramp[order:sub(i, i)]] = ramp[order:sub(math.max(1, i - n), math.max(1, i - n))]
    end
  end
  return m
end
local function sides(name, ramps)
  PA.remap(OUT .. name .. ".aseprite", OUT .. name .. "_left", steps(ramps, 1))
  PA.remap(OUT .. name .. ".aseprite", OUT .. name .. "_right", steps(ramps, 2))
end
local function flash(name) PA.whiten(OUT .. name .. ".aseprite", OUT .. name .. "_flash", 0.72) end

-- grass top: flat mid green, a few light tufts and dark specks; 1px bevel (light top/left, dark bottom/right)
PA.sprite_from_grid(OUT .. "grass_top", {
  "tttttttttttttttr",
  "tsutssssssussssr",
  "tsssssrssssssssr",
  "tssssssssssssrsr",
  "tsssussssssssssr",
  "tsrsstssssrsutsr",
  "tssssssssssssssr",
  "tsusssssrssssssr",
  "tsssssussssssssr",
  "tsssssstsssssrsr",
  "tsrsssssssusssur",
  "tssssssssssssssr",
  "tsssusssssssssrr",
  "tssssssrsssussur",
  "tssssssssssssssr",
  "rrrrrrrrrrrrrrrq",
}, leaf)
flash("grass_top")

-- grass side: a green lip with drips over dirt, chunky pebbles (light pair over a dark dot)
PA.sprite_from_grid(OUT .. "grass_side", {
  "tttttttttttttttr",
  "tssstsssssstsssr",
  "tsssssrssssssrsq",
  "rsrsrrcrsrrcrsrq",
  "dcrcccccrcccccrb",
  "dccccdcccccdcccb",
  "dcdecccccccbcccb",
  "dccbcccccccccdeb",
  "dcccccccdecccbcb",
  "dcccccccbccccccb",
  "dcdecccccccccccb",
  "dccbcccccdeccccb",
  "dcccccccccbccccb",
  "dcccdeccccccccdb",
  "dccccbccccccccbb",
  "bbbbbbbbbbbbbbba",
}, merge(leaf, dirt))
sides("grass_side", { { leaf, "Zpqrstu" }, { dirt, "Xabcde" } })
flash("grass_side")

-- stone: flat mid grey, two chunky cracks (dark line, light lip above), light patches
PA.sprite_from_grid(OUT .. "stone", {
  "eeeeeeeeeeeeeeed",
  "eddddddddddddddc",
  "edeedddddddeeddc",
  "eddddcccdddddddc",
  "edddcbeddddddddc",
  "eddddddddddcdddc",
  "edddddddddcbdddc",
  "eddeeddddccdeedc",
  "edddddddcbddddec",
  "eddddddddddddddc",
  "edcddddddddddddc",
  "edbcddddeeddcddc",
  "eddbccddddddbcdc",
  "edddddddddddddcc",
  "eddeddddddeddddc",
  "cccccccccccccccb",
}, stone)
sides("stone", { { stone, "Yabcdef" } })
flash("stone")

-- TNT side: paper sticks (4px period) over and under a white band with bold TNT lettering
PA.sprite_from_grid(OUT .. "tnt_side", {
  "DDDCDDDCDDDCDDDC",
  "DCBADCBADCBADCBA",
  "DCBADCBADCBADCBA",
  "DCBADCBADCBADCBA",
  "DCBADCBADCBADCBA",
  "WWWWWWWWWWWWWWWg",
  "Wwnnnwnwwnwnnnwg",
  "Wwwnwwnnwnwwnwwg",
  "Wwwnwwnwnnwwnwwg",
  "Wwwnwwnwwnwwnwwg",
  "gggggggggggggggv",
  "DCBADCBADCBADCBA",
  "DCBADCBADCBADCBA",
  "DCBADCBADCBADCBA",
  "DCBADCBADCBADCBA",
  "AAAOAAAOAAAOAAAO",
}, merge(red, band, ink))
sides("tnt_side", { { red, "QOABCD" }, { band, "yxvgwW" }, { ink, "MNn" } })
flash("tnt_side")

-- TNT top: stick ends (top-lit 4x4 bumps) around a dark fuse hole with a grey knot
PA.sprite_from_grid(OUT .. "tnt_top", {
  "DCCBDCCBDCCBDCCB",
  "CBBACBBACBBACBBA",
  "CBBACBBACBBACBBA",
  "BAAOBAAOBAAOBAAO",
  "DCCBBAOOOOABDCCB",
  "CBBAAOOOOOOACBBA",
  "CBBAOOOkLOOOCBBA",
  "BAAOOOkLLkOOBAAO",
  "DCCBOOkkLkOODCCB",
  "CBBAOOOkkOOOCBBA",
  "CBBAAOOOOOOACBBA",
  "BAAOBAOOOOABBAAO",
  "DCCBDCCBDCCBDCCB",
  "CBBACBBACBBACBBA",
  "CBBACBBACBBACBBA",
  "BAAOBAAOBAAOBAAO",
}, merge(red, fuse))
flash("tnt_top")

-- debris tiles (4x4 each): grass, dirt, stone, TNT paper
PA.sprite_from_grid(OUT .. "chunks", {
  "tttscccbeeddDDCB",
  "tsurcbcceddeCCBA",
  "srssbccbddedCBBA",
  "rrrqbbbaddddBAAO",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
  "................",
}, merge(leaf, { a = dirt.a, b = dirt.b }, { c = dirt.c }, red, { d = stone.d, e = stone.e }))

-- fuse spark: 4-point twinkle, white core, yellow and orange arms (8x8)
PA.sprite_from_grid(OUT .. "fx_spark", {
  "...o....",
  "...y....",
  "...y....",
  "oyyWWyyo",
  "...W....",
  "...y....",
  "...o....",
  "........",
}, { W = "#FFFFFF", y = "#FFE14D", o = "#FF9A1F" })

-- description-kit items: a lit TNT bundle (strip caps) and a cartoon boom (divider centre)
PA.sprite_from_grid(OUT .. "tnt_item", {
  "......y.....",
  ".....yWy....",
  "......k.....",
  ".....k......",
  ".....k......",
  ".DCBDCBDCBA.",
  ".DCBDCBDCBA.",
  ".WWWWWWWWWg.",
  ".wwwwwwwwwv.",
  ".gggggggggv.",
  ".DCBDCBDCBA.",
  ".DCBDCBDCBA.",
  ".DCBDCBDCBA.",
  ".AAAAAAAAAO.",
}, merge(red, band, fuse, { y = "#FFE14D", W = "#FFFFFF" }))
PA.sprite_from_grid(OUT .. "boom_item", {
  ".......R........",
  "......RoR...R...",
  "..R..RooR..RR...",
  "..RR.RoyoRRoR...",
  "...RRoyyyooR....",
  "....RoyWWyoRRR..",
  ".RRRoyWWWWyooR..",
  "RooooyWWWWyyR...",
  ".RRoyyWWWWyoR...",
  "...RoyyWWyyoRR..",
  "..RRooyyyyoooR..",
  "..RoRRoyyoRRRoR.",
  ".RR...RooR...RR.",
  ".R....RoR.......",
  "......RR........",
  "................",
}, { R = "#B8321A", o = "#FF9A1F", y = "#FFD83A", W = "#FFF7BD" })

-- banner lettering: fire bands (pale yellow core down to orange), a deep red extrude
local FIRE = { bands = { "#FFF7BD", "#FFE14D", "#FFE14D", "#FFE14D", "#FFB02E", "#FFB02E", "#FFB02E", "#FF7A1F", "#FF7A1F", "#FF7A1F" },
               extrude = { "#B8321A", "#6E1224" } }
PA.title_sprite(OUT .. "banner_title_top", "HAVING A", FIRE)
PA.title_sprite(OUT .. "banner_title", "BLAST", FIRE)
PA.label_sprite(OUT .. "banner_tagline", "ONE BOOM. BOUNCY BLOCKS.", function(i) return i > 9 and "#FFB02E" or "#FFFFFF" end)

-- backgrounds: flat dusk blue plus a ground-shadow ellipse under the cast and its landing spots.
-- Placed for make_icon.py's CROP (64px grid, x8 = 512) and make_banner.py's ART_OFFSET; move them if either changes.
local SKY, SHADOW = "#35508A", "#2A4072"
local function shadow_bg(path, w, h, cx, cy, a, b, extra)
  local px = {}
  for y = 0, h - 1 do
    for x = 0, w - 1 do
      local u, v = (x + 0.5 - cx) / a, (y + 0.5 - cy) / b
      px[PA.key(x, y)] = (u * u + v * v <= 1) and SHADOW or SKY
    end
  end
  for k, c in pairs(extra or {}) do px[k] = c end
  PA.save_pixels(path, w, h, px)
end
shadow_bg(OUT .. "bg_flat", 64, 64, 32, 48, 28, 8)

-- banner (192x64, x8): the same shadow under the art, plus a few twinkles away from the lettering and the art
local twinkles = {}
for _, t in ipairs({ { 6, 5, 1 }, { 60, 7, 0 }, { 104, 4, 1 }, { 118, 22, 0 }, { 185, 12, 1 }, { 186, 44, 0 }, { 110, 57, 1 }, { 12, 58, 0 }, { 58, 55, 0 } }) do
  local x, y, big = t[1], t[2], t[3] == 1
  twinkles[PA.key(x, y)] = "#6D8BCB"
  if big then
    for _, d in ipairs({ { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) do twinkles[PA.key(x + d[1], y + d[2])] = "#4E69A6" end
  end
end
shadow_bg(OUT .. "banner_bg", 192, 64, 145.6, 50.7, 31.5, 9, twinkles)
