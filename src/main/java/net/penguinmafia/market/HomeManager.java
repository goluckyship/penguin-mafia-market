package net.penguinmafia.market;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Per-player saved homes (slots 1..MAX_HOMES), persisted in homes.yml. */
public class HomeManager {

    public static final int MAX_HOMES = 18;

    private final Plugin plugin;
    private final File file;
    private final YamlConfiguration config;

    public HomeManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    private String path(UUID id, int slot) {
        return id + "." + slot;
    }

    public boolean has(UUID id, int slot) {
        return config.isConfigurationSection(path(id, slot));
    }

    /** Lowest free slot (1-based), or -1 if all are used. */
    public int nextFree(UUID id) {
        for (int i = 1; i <= MAX_HOMES; i++) if (!has(id, i)) return i;
        return -1;
    }

    public void set(UUID id, int slot, Location loc) {
        String p = path(id, slot);
        config.set(p + ".world", loc.getWorld().getName());
        config.set(p + ".x", loc.getX());
        config.set(p + ".y", loc.getY());
        config.set(p + ".z", loc.getZ());
        config.set(p + ".yaw", (double) loc.getYaw());
        config.set(p + ".pitch", (double) loc.getPitch());
        save();
    }

    public void delete(UUID id, int slot) {
        config.set(path(id, slot), null);
        save();
    }

    /** The saved location, or null if the slot is empty or its world no longer exists. */
    public Location get(UUID id, int slot) {
        ConfigurationSection s = config.getConfigurationSection(path(id, slot));
        if (s == null) return null;
        World world = Bukkit.getWorld(s.getString("world", ""));
        if (world == null) return null;
        return new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    /** World name for display even if the world is unloaded; null if the slot is empty. */
    public String describe(UUID id, int slot) {
        ConfigurationSection s = config.getConfigurationSection(path(id, slot));
        if (s == null) return null;
        return s.getString("world", "?") + " " + (int) s.getDouble("x") + ", " + (int) s.getDouble("y") + ", " + (int) s.getDouble("z");
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save homes.yml: " + e.getMessage());
        }
    }
}
