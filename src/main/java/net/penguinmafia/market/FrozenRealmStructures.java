package net.penguinmafia.market;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.Random;

/**
 * Scatters small alpine chalets (stone foundation, spruce log/plank walls, a
 * snow-capped gabled roof, a chimney, each holding 300 Frozen Coins) and
 * standalone spruce bridge/platform structures across the Frozen Realm
 * dimension.
 *
 * This is deliberately plain block placement on chunk generation rather than
 * a vanilla datapack structure (jigsaw + hand-authored NBT template):
 *  - a raw NBT structure template and structure-set/jigsaw json can't be
 *    test-loaded here, and malformed worldgen registry data is exactly what
 *    crashed this server before;
 *  - a vanilla loot table has no way to stamp our plugin's
 *    PersistentDataContainer marker onto its items, so a datapack-spawned
 *    chest would generate fake coins that /bm deposit would reject.
 * Building the cabin directly in Java and filling the chest with real
 * Economy#coinItem() stacks sidesteps both problems.
 *
 * Placement is grid-based and fully deterministic from the world seed, so it
 * never needs to "remember" where anything was built: cabins are confined to
 * the centre of 40x40-chunk cells, which guarantees at least 30 chunks
 * between any two of them (including across a cell boundary).
 */
public class FrozenRealmStructures implements Listener {

    private static final NamespacedKey FROZEN_REALM_KEY = NamespacedKey.fromString("penguinmafia:frozen_realm");

    private static final int CABIN_CELL_CHUNKS = 40;
    private static final int CABIN_JITTER_CHUNKS = 10; // >= (cell - jitter) = 30 chunks apart, guaranteed
    private static final double CABIN_CHANCE = 0.5;
    private static final long CABIN_SALT_DEFAULT = 0xC0FFEEL;
    private static final long CABIN_COINS = 300L;

    private static final int BRIDGE_CELL_CHUNKS = 24;
    private static final int BRIDGE_JITTER_CHUNKS = 12;
    private static final double BRIDGE_CHANCE = 0.4;
    private static final long BRIDGE_SALT_DEFAULT = 0x5B41D9EL;

    private final Economy economy;
    private final File saltFile;

    // Salts are mutable (not the static *_DEFAULT constants above) so
    // /frozenrealm scramble can change them at runtime: XOR-ing the world
    // seed with a different salt before hashing sends every cabin/bridge
    // to a completely different set of grid cells, without touching the
    // world seed itself. Persisted to saltFile so a scramble survives a
    // restart.
    private long cabinSalt = CABIN_SALT_DEFAULT;
    private long bridgeSalt = BRIDGE_SALT_DEFAULT;

    public FrozenRealmStructures(Economy economy, File saltFile) {
        this.economy = economy;
        this.saltFile = saltFile;
        loadSalts();
    }

    private void loadSalts() {
        if (saltFile == null || !saltFile.exists()) return;
        try (FileInputStream in = new FileInputStream(saltFile)) {
            Properties props = new Properties();
            props.load(in);
            cabinSalt = Long.parseLong(props.getProperty("cabinSalt", Long.toString(cabinSalt)));
            bridgeSalt = Long.parseLong(props.getProperty("bridgeSalt", Long.toString(bridgeSalt)));
        } catch (Exception ignored) {
            // Fall back to whatever was already in cabinSalt/bridgeSalt (the defaults).
        }
    }

    private void saveSalts() {
        if (saltFile == null) return;
        try {
            File parent = saltFile.getParentFile();
            if (parent != null) parent.mkdirs();
            Properties props = new Properties();
            props.setProperty("cabinSalt", Long.toString(cabinSalt));
            props.setProperty("bridgeSalt", Long.toString(bridgeSalt));
            try (FileOutputStream out = new FileOutputStream(saltFile)) {
                props.store(out, "Frozen Realm structure placement salts - changed by /frozenrealm scramble");
            }
        } catch (Exception ignored) {
            // Worst case the scramble doesn't survive a restart; not worth crashing the command over.
        }
    }

    /**
     * Op-only: re-randomizes where cabins and bridges land, by picking new
     * salts and persisting them. Only affects chunks generated from here
     * on - it doesn't move anything already built. Combine with wiping
     * the dimension's region files (world/dimensions/penguinmafia/frozen_realm)
     * and restarting for a completely fresh, rescrambled layout.
     */
    public void scramble() {
        Random random = new Random();
        cabinSalt = random.nextLong();
        bridgeSalt = random.nextLong();
        saveSalts();
    }

    /**
     * Finds the nearest cabin grid slot to the given chunk coordinates,
     * searching outward cell by cell - purely deterministic seed math, no
     * generating or teleporting. Returns chunk coordinates, or null in the
     * vanishingly unlikely case nothing turns up within {@code maxRadius}
     * cells (at a 50% chance per cell that's not realistically reachable).
     */
    public int[] nearestCabin(World world, int chunkX, int chunkZ) {
        return nearestStructure(world, chunkX, chunkZ, CABIN_CELL_CHUNKS, CABIN_JITTER_CHUNKS, CABIN_CHANCE, cabinSalt);
    }

    /**
     * Force-builds a cabin at the given world coordinates right now,
     * regardless of whether this chunk has already generated naturally -
     * used to guarantee a cabin exists at a located slot even if that chunk
     * happens not to have loaded for the first time yet. Caller is
     * responsible for loading/generating the chunk first.
     */
    public void forceBuildCabin(World world, int worldX, int worldZ) {
        buildCabin(world, worldX, worldZ);
    }

    private static int[] nearestStructure(World world, int chunkX, int chunkZ, int cellSize, int jitter, double chance, long salt) {
        int originCellX = Math.floorDiv(chunkX, cellSize);
        int originCellZ = Math.floorDiv(chunkZ, cellSize);

        int[] best = null;
        long bestDistSq = Long.MAX_VALUE;
        int maxRadius = 8; // cells - comfortably enough given a 40-50% per-cell chance
        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dcx = -radius; dcx <= radius; dcx++) {
                for (int dcz = -radius; dcz <= radius; dcz++) {
                    if (Math.max(Math.abs(dcx), Math.abs(dcz)) != radius) continue; // only this ring
                    int[] target = gridTargetForCell(world, originCellX + dcx, originCellZ + dcz, cellSize, jitter, chance, salt);
                    if (target == null) continue;
                    long dx = target[0] - chunkX;
                    long dz = target[1] - chunkZ;
                    long distSq = dx * dx + dz * dz;
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = target;
                    }
                }
            }
            if (best != null) return best; // nearest hit in the closest non-empty ring is close enough
        }
        return best;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;
        World world = event.getWorld();
        if (FROZEN_REALM_KEY == null || !FROZEN_REALM_KEY.equals(world.getKey())) return;

        Chunk chunk = event.getChunk();
        maybeBuildCabin(world, chunk);
        maybeBuildBridge(world, chunk);
    }

    private void maybeBuildCabin(World world, Chunk chunk) {
        int[] target = gridTarget(world, chunk.getX(), chunk.getZ(),
                CABIN_CELL_CHUNKS, CABIN_JITTER_CHUNKS, CABIN_CHANCE, cabinSalt);
        if (target == null || target[0] != chunk.getX() || target[1] != chunk.getZ()) return;

        int worldX = (chunk.getX() << 4) + 8;
        int worldZ = (chunk.getZ() << 4) + 8;
        buildCabin(world, worldX, worldZ);
    }

    private void maybeBuildBridge(World world, Chunk chunk) {
        int[] target = gridTarget(world, chunk.getX(), chunk.getZ(),
                BRIDGE_CELL_CHUNKS, BRIDGE_JITTER_CHUNKS, BRIDGE_CHANCE, bridgeSalt);
        if (target == null || target[0] != chunk.getX() || target[1] != chunk.getZ()) return;

        int worldX = (chunk.getX() << 4) + 8;
        int worldZ = (chunk.getZ() << 4) + 8;
        buildBridge(world, worldX, worldZ);
    }

    /**
     * Deterministically decides, purely from the world seed and the cell a
     * chunk falls in, whether that cell has a structure and exactly which
     * chunk within it. Calling this again later for any chunk in the same
     * cell always returns the same answer.
     */
    private int[] gridTarget(World world, int chunkX, int chunkZ, int cellSize, int jitter, double chance, long salt) {
        int cellX = Math.floorDiv(chunkX, cellSize);
        int cellZ = Math.floorDiv(chunkZ, cellSize);
        return gridTargetForCell(world, cellX, cellZ, cellSize, jitter, chance, salt);
    }

    private static int[] gridTargetForCell(World world, int cellX, int cellZ, int cellSize, int jitter, double chance, long salt) {
        Random cellRandom = new Random(hash(world.getSeed() ^ salt, cellX, cellZ));
        if (cellRandom.nextDouble() >= chance) return null;

        int margin = (cellSize - jitter) / 2;
        int offsetX = margin + cellRandom.nextInt(jitter);
        int offsetZ = margin + cellRandom.nextInt(jitter);
        return new int[]{cellX * cellSize + offsetX, cellZ * cellSize + offsetZ};
    }

    private static long hash(long seed, int x, int z) {
        long h = seed;
        h = h * 6364136223846793005L + x;
        h = h * 6364136223846793005L + z;
        h ^= (h >>> 33);
        return h;
    }

    /**
     * A small alpine chalet: a stone foundation/base course, spruce log and
     * plank walls, a snow-dusted gabled roof, and a stone chimney - modest
     * and code-buildable, aimed at the "wood cabin in the snowy mountains"
     * look rather than an ornate hand-built castle.
     */
    private void buildCabin(World world, int centerX, int centerZ) {
        // Sample ground height a little outside the footprint, not dead
        // centre: once a cabin exists here, the centre's "highest block" is
        // its own roof/chimney, which would otherwise stack a rebuilt cabin
        // on top of itself if this ever runs again for the same spot.
        int baseY = groundHeightNear(world, centerX, centerZ) + 1;
        if (baseY >= world.getMaxHeight() - 20) return; // too close to the build limit, skip

        int half = 3; // 7x7 footprint
        int wallHeight = 4;
        int roofHalf = half + 1;

        // Remove any earlier cabin that got stacked above this spot (e.g.
        // from re-running /frozenrealm summon before the height-sampling
        // fix) before placing the new one.
        clearCabinMaterials(world, centerX, centerZ, baseY, roofHalf);

        // Stone foundation
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                world.getBlockAt(centerX + dx, baseY - 1, centerZ + dz).setType(Material.STONE_BRICKS);
            }
        }

        // Walls: stone base course, spruce log corners, spruce plank fill
        for (int y = 0; y < wallHeight; y++) {
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    boolean edge = dx == -half || dx == half || dz == -half || dz == half;
                    if (!edge) continue;
                    boolean corner = (dx == -half || dx == half) && (dz == -half || dz == half);
                    Material mat;
                    if (y == 0) {
                        mat = Material.STONE_BRICKS;
                    } else if (corner) {
                        mat = Material.SPRUCE_LOG;
                    } else {
                        mat = Material.SPRUCE_PLANKS;
                    }
                    world.getBlockAt(centerX + dx, baseY + y, centerZ + dz).setType(mat);
                }
            }
        }

        // Small windows flanking the door
        world.getBlockAt(centerX - 2, baseY + 1, centerZ + half).setType(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        world.getBlockAt(centerX + 2, baseY + 1, centerZ + half).setType(Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        // Doorway on the south wall
        world.getBlockAt(centerX, baseY, centerZ + half).setType(Material.AIR);
        world.getBlockAt(centerX, baseY + 1, centerZ + half).setType(Material.AIR);

        // Gabled, snow-capped roof: a ridge running north-south, peak over centre X
        int roofBaseY = baseY + wallHeight;
        for (int dx = -roofHalf; dx <= roofHalf; dx++) {
            int clampedDx = Math.max(-half, Math.min(half, dx));
            int ridgeHeight = half - Math.abs(clampedDx);
            for (int dz = -roofHalf; dz <= roofHalf; dz++) {
                for (int y = 0; y <= ridgeHeight; y++) {
                    world.getBlockAt(centerX + dx, roofBaseY + y, centerZ + dz).setType(Material.SPRUCE_PLANKS);
                }
                Block snowSpot = world.getBlockAt(centerX + dx, roofBaseY + ridgeHeight + 1, centerZ + dz);
                if (snowSpot.getType() == Material.AIR) {
                    snowSpot.setType(Material.SNOW);
                }
            }
        }

        // Stone chimney poking through the roof
        int chimneyX = centerX + half - 1;
        int chimneyZ = centerZ;
        int chimneyTop = roofBaseY + half + 2;
        for (int y = roofBaseY - 1; y <= chimneyTop; y++) {
            world.getBlockAt(chimneyX, y, chimneyZ).setType(Material.COBBLESTONE);
        }

        // Chest against the back wall, filled with real Frozen Coins
        Block chestBlock = world.getBlockAt(centerX, baseY, centerZ - half + 1);
        chestBlock.setType(Material.CHEST);
        if (chestBlock.getState() instanceof Chest chestState) {
            fillChestWithCoins(chestState.getInventory(), CABIN_COINS);
            chestState.update();
        }
    }

    private void fillChestWithCoins(Inventory inventory, long total) {
        inventory.clear(); // in case this chest already existed (e.g. a re-summon), start fresh
        long remaining = total;
        int slot = 0;
        while (remaining > 0 && slot < inventory.getSize()) {
            int stackAmount = (int) Math.min(remaining, 64);
            inventory.setItem(slot, economy.coinItem(stackAmount));
            remaining -= stackAmount;
            slot++;
        }
    }

    /**
     * Wipes any of our own build materials out of the column range above a
     * freshly-computed baseY, in the structure's footprint - cleans up a
     * cabin that got stacked on top of an earlier one, without touching
     * real terrain (which won't be spruce/cobblestone/chest at these
     * heights in this biome).
     */
    private void clearCabinMaterials(World world, int centerX, int centerZ, int baseY, int roofHalf) {
        int fromY = baseY;
        int toY = Math.min(world.getMaxHeight() - 1, baseY + 60);
        for (int dx = -roofHalf - 3; dx <= roofHalf + 3; dx++) {
            for (int dz = -roofHalf; dz <= roofHalf; dz++) {
                for (int y = fromY; y <= toY; y++) {
                    Block b = world.getBlockAt(centerX + dx, y, centerZ + dz);
                    if (isCabinMaterial(b.getType())) {
                        b.setType(Material.AIR);
                    }
                }
            }
        }
    }

    private boolean isCabinMaterial(Material material) {
        switch (material) {
            case STONE_BRICKS:
            case SPRUCE_LOG:
            case SPRUCE_PLANKS:
            case COBBLESTONE:
            case SNOW:
            case LIGHT_BLUE_STAINED_GLASS_PANE:
            case CHEST:
            case SPRUCE_FENCE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Ground height sampled just outside the structure's own footprint
     * (10 blocks off to the side) so a structure already standing here
     * doesn't get measured as "the terrain" on a later rebuild.
     */
    private int groundHeightNear(World world, int centerX, int centerZ) {
        int a = world.getHighestBlockYAt(centerX + 10, centerZ);
        int b = world.getHighestBlockYAt(centerX, centerZ + 10);
        return Math.min(a, b);
    }

    private void buildBridge(World world, int centerX, int centerZ) {
        int length = 16;
        int startX = centerX - length / 2;
        int deckY = groundHeightNear(world, centerX, centerZ) + 3;
        if (deckY >= world.getMaxHeight() - 4) return;

        for (int i = 0; i < length; i++) {
            int x = startX + i;
            world.getBlockAt(x, deckY, centerZ).setType(Material.SPRUCE_PLANKS);
            world.getBlockAt(x, deckY + 1, centerZ - 1).setType(Material.SPRUCE_FENCE);
            world.getBlockAt(x, deckY + 1, centerZ + 1).setType(Material.SPRUCE_FENCE);
        }
    }
}
