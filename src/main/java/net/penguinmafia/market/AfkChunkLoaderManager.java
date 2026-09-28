package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Backs /afk: an op stands where they want chunks kept loaded, runs /afk,
 * and that area is force-loaded (via Bukkit's chunk force-load API) so it
 * keeps ticking - mob farms, redstone, crops, whatever - even after that op
 * disconnects. Running /afk again in the same session (from anywhere) turns
 * it back off.
 *
 * Force-loaded chunks are refcounted per world, not just flipped on/off,
 * because two ops' zones can overlap - a chunk only actually gets
 * un-force-loaded once nothing else is still claiming it. Zones are also
 * persisted to afk_chunkloaders.yml and re-applied on server start, since
 * the whole point is that they survive well past a log-out, including a
 * restart.
 */
public class AfkChunkLoaderManager {

    private static final int MAX_RADIUS = 3; // 3 -> a 7x7 chunk area, plenty for any farm

    private static class Zone {
        final String world;
        final int centerX;
        final int centerZ;
        final int radius;

        Zone(String world, int centerX, int centerZ, int radius) {
            this.world = world;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
        }
    }

    private final PenguinMafiaMarket plugin;
    private final File file;
    private final YamlConfiguration config;

    private final Map<UUID, Zone> zonesByPlayer = new HashMap<>();
    // world name -> (chunk key -> number of zones currently claiming it)
    private final Map<String, Map<Long, Integer>> refCounts = new HashMap<>();

    public AfkChunkLoaderManager(PenguinMafiaMarket plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "afk_chunkloaders.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        loadAndApply();
    }

    private void loadAndApply() {
        if (!config.contains("zones")) return;
        for (String key : config.getConfigurationSection("zones").getKeys(false)) {
            String path = "zones." + key;
            String world = config.getString(path + ".world");
            int x = config.getInt(path + ".x");
            int z = config.getInt(path + ".z");
            int radius = config.getInt(path + ".radius");
            try {
                UUID id = UUID.fromString(key);
                Zone zone = new Zone(world, x, z, radius);
                zonesByPlayer.put(id, zone);
                apply(zone);
            } catch (IllegalArgumentException ignored) {
                // malformed entry from hand-editing the file; skip it
            }
        }
        plugin.getLogger().info("Restored " + zonesByPlayer.size() + " /afk chunk-loader zone(s).");
    }

    /**
     * Toggles /afk for this player at their current location. Returns true
     * if it's now ON (and where/how big), false if it just got turned off.
     */
    public ToggleResult toggle(Player player, int radius) {
        UUID id = player.getUniqueId();
        Zone existing = zonesByPlayer.remove(id);
        if (existing != null) {
            unapply(existing);
            save();
            return new ToggleResult(false, null, 0, 0, 0);
        }

        int clampedRadius = Math.max(0, Math.min(MAX_RADIUS, radius));
        Chunk chunk = player.getLocation().getChunk();
        Zone zone = new Zone(player.getWorld().getName(), chunk.getX(), chunk.getZ(), clampedRadius);
        zonesByPlayer.put(id, zone);
        apply(zone);
        save();
        return new ToggleResult(true, zone.world, zone.centerX, zone.centerZ, zone.radius);
    }

    public static class ToggleResult {
        public final boolean enabled;
        public final String world;
        public final int centerX;
        public final int centerZ;
        public final int radius;

        ToggleResult(boolean enabled, String world, int centerX, int centerZ, int radius) {
            this.enabled = enabled;
            this.world = world;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
        }
    }

    private void apply(Zone zone) {
        World world = Bukkit.getWorld(zone.world);
        if (world == null) return;
        Map<Long, Integer> counts = refCounts.computeIfAbsent(zone.world, w -> new HashMap<>());
        for (int dx = -zone.radius; dx <= zone.radius; dx++) {
            for (int dz = -zone.radius; dz <= zone.radius; dz++) {
                int x = zone.centerX + dx;
                int z = zone.centerZ + dz;
                long key = chunkKey(x, z);
                int newCount = counts.merge(key, 1, Integer::sum);
                if (newCount == 1) {
                    world.setChunkForceLoaded(x, z, true);
                }
            }
        }
    }

    private void unapply(Zone zone) {
        World world = Bukkit.getWorld(zone.world);
        Map<Long, Integer> counts = refCounts.get(zone.world);
        for (int dx = -zone.radius; dx <= zone.radius; dx++) {
            for (int dz = -zone.radius; dz <= zone.radius; dz++) {
                int x = zone.centerX + dx;
                int z = zone.centerZ + dz;
                long key = chunkKey(x, z);
                if (counts == null) continue;
                Integer remaining = counts.computeIfPresent(key, (k, v) -> v - 1 <= 0 ? null : v - 1);
                if (remaining == null && world != null) {
                    world.setChunkForceLoaded(x, z, false);
                }
            }
        }
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private void save() {
        config.set("zones", null);
        for (Map.Entry<UUID, Zone> entry : zonesByPlayer.entrySet()) {
            String path = "zones." + entry.getKey();
            Zone zone = entry.getValue();
            config.set(path + ".world", zone.world);
            config.set(path + ".x", zone.centerX);
            config.set(path + ".z", zone.centerZ);
            config.set(path + ".radius", zone.radius);
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save afk_chunkloaders.yml: " + e.getMessage());
        }
    }
}
