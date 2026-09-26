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

import java.util.Random;

/**
 * Scatters small spruce cabins (each holding 300 Frozen Coins) and standalone
 * spruce bridge/platform structures across the Frozen Realm dimension.
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
    private static final long CABIN_SALT = 0xC0FFEEL;
    private static final long CABIN_COINS = 300L;

    private static final int BRIDGE_CELL_CHUNKS = 24;
    private static final int BRIDGE_JITTER_CHUNKS = 12;
    private static final double BRIDGE_CHANCE = 0.4;
    private static final long BRIDGE_SALT = 0x5B41D9EL;

    private final Economy economy;

    public FrozenRealmStructures(Economy economy) {
        this.economy = economy;
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
                CABIN_CELL_CHUNKS, CABIN_JITTER_CHUNKS, CABIN_CHANCE, CABIN_SALT);
        if (target == null || target[0] != chunk.getX() || target[1] != chunk.getZ()) return;

        int worldX = (chunk.getX() << 4) + 8;
        int worldZ = (chunk.getZ() << 4) + 8;
        buildCabin(world, worldX, worldZ);
    }

    private void maybeBuildBridge(World world, Chunk chunk) {
        int[] target = gridTarget(world, chunk.getX(), chunk.getZ(),
                BRIDGE_CELL_CHUNKS, BRIDGE_JITTER_CHUNKS, BRIDGE_CHANCE, BRIDGE_SALT);
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
        Random cellRandom = new Random(hash(world.getSeed() ^ salt, cellX, cellZ));
        if (cellRandom.nextDouble() >= chance) return null;

        int margin = (cellSize - jitter) / 2;
        int offsetX = margin + cellRandom.nextInt(jitter);
        int offsetZ = margin + cellRandom.nextInt(jitter);
        return new int[]{cellX * cellSize + offsetX, cellZ * cellSize + offsetZ};
    }

    private long hash(long seed, int x, int z) {
        long h = seed;
        h = h * 6364136223846793005L + x;
        h = h * 6364136223846793005L + z;
        h ^= (h >>> 33);
        return h;
    }

    private void buildCabin(World world, int centerX, int centerZ) {
        int baseY = world.getHighestBlockYAt(centerX, centerZ) + 1;
        if (baseY >= world.getMaxHeight() - 8) return; // too close to the build limit, skip

        int half = 3; // 7x7 footprint

        // Floor
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                world.getBlockAt(centerX + dx, baseY - 1, centerZ + dz).setType(Material.SPRUCE_PLANKS);
            }
        }

        // Walls (4 tall)
        for (int y = 0; y < 4; y++) {
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    boolean edge = dx == -half || dx == half || dz == -half || dz == half;
                    if (!edge) continue;
                    boolean corner = (dx == -half || dx == half) && (dz == -half || dz == half);
                    Material mat = corner ? Material.SPRUCE_LOG : Material.SPRUCE_PLANKS;
                    world.getBlockAt(centerX + dx, baseY + y, centerZ + dz).setType(mat);
                }
            }
        }

        // Doorway on the south wall
        world.getBlockAt(centerX, baseY, centerZ + half).setType(Material.AIR);
        world.getBlockAt(centerX, baseY + 1, centerZ + half).setType(Material.AIR);

        // Flat overhanging roof
        for (int dx = -half - 1; dx <= half + 1; dx++) {
            for (int dz = -half - 1; dz <= half + 1; dz++) {
                world.getBlockAt(centerX + dx, baseY + 4, centerZ + dz).setType(Material.SPRUCE_PLANKS);
            }
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
        long remaining = total;
        int slot = 0;
        while (remaining > 0 && slot < inventory.getSize()) {
            int stackAmount = (int) Math.min(remaining, 64);
            inventory.setItem(slot, economy.coinItem(stackAmount));
            remaining -= stackAmount;
            slot++;
        }
    }

    private void buildBridge(World world, int centerX, int centerZ) {
        int length = 16;
        int startX = centerX - length / 2;
        int deckY = world.getHighestBlockYAt(centerX, centerZ) + 3;
        if (deckY >= world.getMaxHeight() - 4) return;

        for (int i = 0; i < length; i++) {
            int x = startX + i;
            world.getBlockAt(x, deckY, centerZ).setType(Material.SPRUCE_PLANKS);
            world.getBlockAt(x, deckY + 1, centerZ - 1).setType(Material.SPRUCE_FENCE);
            world.getBlockAt(x, deckY + 1, centerZ + 1).setType(Material.SPRUCE_FENCE);
        }
    }
}
