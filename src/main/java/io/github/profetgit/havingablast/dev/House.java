package io.github.profetgit.havingablast.dev;

import java.util.ArrayList;
import java.util.List;

/**
 * A small cottage as commands, for the demo scenes and the repair tests: cobblestone base, oak plank walls on an
 * oak log frame, glass windows, a door, a slab roof, torches in and out, a chest with items, a bed, a sign, a lantern,
 * a carpet and a potted flower. Front (door) faces north (-z). Footprint x..x+6, z..z+5, floor at y.
 */
public final class House {
    private House() {
    }

    public static List<String> commands(int x, int y, int z) {
        return commands(x, y, z, false);
    }

    /** extras: the repair tests' additions: rails, redstone, sand on the roof, a banner, and a pond at the side. */
    public static List<String> commands(int x, int y, int z, boolean extras) {
        List<String> c = new ArrayList<>();
        int x1 = x + 6, z1 = z + 5;
        c.add(fill(x, y, z, x1, y, z1, "cobblestone"));
        c.add(fill(x, y + 1, z, x1, y + 3, z1, "oak_planks") + " hollow");
        for (int[] k : new int[][] {{x, z}, {x1, z}, {x, z1}, {x1, z1}}) c.add(fill(k[0], y + 1, k[1], k[0], y + 3, k[1], "oak_log"));
        c.add(fill(x - 1, y + 4, z - 1, x1 + 1, y + 4, z1 + 1, "oak_slab"));
        // windows
        c.add(fill(x + 1, y + 2, z, x + 2, y + 2, z, "glass"));
        c.add(fill(x + 4, y + 2, z, x + 5, y + 2, z, "glass"));
        c.add(fill(x, y + 2, z + 2, x, y + 2, z + 3, "glass"));
        c.add(fill(x1, y + 2, z + 2, x1, y + 2, z + 3, "glass"));
        // door in the middle of the front wall
        c.add(set(x + 3, y + 1, z, "oak_door[facing=north,half=lower,hinge=left,open=false]"));
        c.add(set(x + 3, y + 2, z, "oak_door[facing=north,half=upper,hinge=left,open=false]"));
        // torches: beside the door outside, on the back wall inside
        c.add(set(x + 2, y + 2, z - 1, "wall_torch[facing=north]"));
        c.add(set(x + 4, y + 2, z - 1, "wall_torch[facing=north]"));
        c.add(set(x + 3, y + 2, z1 - 1, "wall_torch[facing=north]"));
        // furniture
        c.add(set(x + 1, y + 1, z1 - 1, "chest[facing=south]{Items:[{Slot:0b,id:\"minecraft:diamond\",count:3},{Slot:1b,id:\"minecraft:bread\",count:12},{Slot:2b,id:\"minecraft:iron_ingot\",count:7}]}"));
        c.add(set(x + 5, y + 1, z + 3, "red_bed[facing=south,part=head]"));
        c.add(set(x + 5, y + 1, z + 2, "red_bed[facing=south,part=foot]"));
        c.add(set(x + 1, y + 1, z + 1, "oak_sign[rotation=8]{front_text:{messages:[\"\",\"Home\",\"sweet home\",\"\"]}}"));
        c.add(set(x + 3, y + 3, z + 3, "lantern[hanging=true]"));
        c.add(set(x + 3, y + 1, z + 2, "red_carpet"));
        c.add(set(x + 2, y + 1, z + 3, "potted_red_tulip"));
        if (extras) {
            c.add(fill(x - 3, y, z - 3, x + 9, y, z - 2, "stone"));
            c.add(fill(x - 2, y + 1, z - 3, x + 8, y + 1, z - 3, "rail[shape=east_west]"));
            c.add(fill(x - 2, y + 1, z - 2, x + 8, y + 1, z - 2, "redstone_wire[east=side,west=side]"));
            c.add(set(x + 1, y + 5, z + 1, "sand"));
            c.add(set(x + 2, y + 5, z + 1, "gravel"));
            c.add(set(x + 5, y + 2, z - 1, "wall_banner[facing=north]{patterns:[{color:\"red\",pattern:\"minecraft:stripe_center\"}]}"));
            c.add(fill(x + 8, y - 1, z + 1, x + 10, y - 1, z + 4, "stone"));
            c.add(fill(x + 8, y, z + 1, x + 10, y, z + 4, "water"));
        }
        return c;
    }

    static String fill(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
        return "fill " + x0 + " " + y0 + " " + z0 + " " + x1 + " " + y1 + " " + z1 + " minecraft:" + block;
    }

    static String set(int x, int y, int z, String block) {
        return "setblock " + x + " " + y + " " + z + " minecraft:" + block;
    }
}
