package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Backs /gen: each op can have at most one item generator active at a time.
 * They start it wherever they're standing and it spawns a fixed amount of a
 * chosen item there - including real, depositable Frozen Coins, built
 * through Economy so they carry the same persistent data tag as a
 * /bm withdraw - every N seconds, forever, at that exact spot, surviving
 * restarts, until that same op runs /gen again (from anywhere) to stop it.
 */
public class GeneratorManager {

    public static class Generator {
        final String world;
        final double x, y, z;
        final boolean coin;
        final String materialName; // null when coin == true
        final int delaySeconds;
        final int amount;
        transient int ticksLeft;

        Generator(String world, double x, double y, double z, boolean coin,
                  String materialName, int delaySeconds, int amount) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.coin = coin;
            this.materialName = materialName;
            this.delaySeconds = delaySeconds;
            this.amount = amount;
            this.ticksLeft = delaySeconds * 20;
        }
    }

    private final PenguinMafiaMarket plugin;
    private final Economy economy;
    private final File file;
    private final YamlConfiguration config;

    private final Map<UUID, Generator> generators = new HashMap<>();

    public GeneratorManager(PenguinMafiaMarket plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.file = new File(plugin.getDataFolder(), "generators.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        load();
        start();
    }

    private void load() {
        ConfigurationSection section = config.getConfigurationSection("generators");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String path = "generators." + key;
            try {
                UUID id = UUID.fromString(key);
                String world = config.getString(path + ".world");
                double x = config.getDouble(path + ".x");
                double y = config.getDouble(path + ".y");
                double z = config.getDouble(path + ".z");
                boolean coin = config.getBoolean(path + ".coin");
                String materialName = config.getString(path + ".material");
                int delaySeconds = Math.max(1, config.getInt(path + ".delay"));
                int amount = Math.max(1, config.getInt(path + ".amount"));
                generators.put(id, new Generator(world, x, y, z, coin, materialName, delaySeconds, amount));
            } catch (IllegalArgumentException ignored) {
                // malformed entry from hand-editing the file; skip it
            }
        }
        plugin.getLogger().info("Restored " + generators.size() + " active /gen generator(s).");
    }

    private void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Generator gen : generators.values()) {
                    gen.ticksLeft -= 20;
                    if (gen.ticksLeft <= 0) {
                        gen.ticksLeft = gen.delaySeconds * 20;
                        spawn(gen);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void spawn(Generator gen) {
        World world = Bukkit.getWorld(gen.world);
        if (world == null) return;
        Location loc = new Location(world, gen.x, gen.y, gen.z);

        int maxStack = gen.coin ? economy.coinItem(1).getMaxStackSize()
                : safeMaterial(gen.materialName).getMaxStackSize();

        int remaining = gen.amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStack);
            ItemStack stack = gen.coin ? economy.coinItem(stackSize) : new ItemStack(safeMaterial(gen.materialName), stackSize);
            world.dropItem(loc, stack);
            remaining -= stackSize;
        }
    }

    private Material safeMaterial(String name) {
        Material material = name == null ? null : Material.matchMaterial(name);
        return material == null ? Material.STONE : material;
    }

    public boolean isActive(Player player) {
        return generators.containsKey(player.getUniqueId());
    }

    public Generator getActive(Player player) {
        return generators.get(player.getUniqueId());
    }

    /** Stops this player's active generator, wherever it is. */
    public void stop(Player player) {
        generators.remove(player.getUniqueId());
        save();
    }

    /**
     * Starts a new generator for this player at their current location and
     * fires it once right away. Only call this when isActive(player) is
     * false - each player may have at most one.
     */
    public Generator start(Player player, boolean coin, String materialName, int delaySeconds, int amount) {
        Location location = player.getLocation();
        Generator gen = new Generator(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                coin, materialName, delaySeconds, amount);
        generators.put(player.getUniqueId(), gen);
        save();
        spawn(gen);
        return gen;
    }

    private void save() {
        config.set("generators", null);
        for (Map.Entry<UUID, Generator> entry : generators.entrySet()) {
            String path = "generators." + entry.getKey();
            Generator gen = entry.getValue();
            config.set(path + ".world", gen.world);
            config.set(path + ".x", gen.x);
            config.set(path + ".y", gen.y);
            config.set(path + ".z", gen.z);
            config.set(path + ".coin", gen.coin);
            config.set(path + ".material", gen.materialName);
            config.set(path + ".delay", gen.delaySeconds);
            config.set(path + ".amount", gen.amount);
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save generators.yml: " + e.getMessage());
        }
    }
}
