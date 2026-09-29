package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs /gen: lets ops drop a standing item generator anywhere in the world
 * that spawns a fixed amount of a chosen item - including real, depositable
 * Frozen Coins, built through Economy so they carry the same persistent
 * data tag as a /bm withdraw - as dropped item entities on a fixed
 * interval, forever, even across restarts, until an op removes it.
 */
public class GeneratorManager {

    public static class Generator {
        final int id;
        final String world;
        final double x, y, z;
        final boolean coin;
        final String materialName; // null when coin == true
        final int delaySeconds;
        final int amount;
        transient int ticksLeft;

        Generator(int id, String world, double x, double y, double z, boolean coin,
                  String materialName, int delaySeconds, int amount) {
            this.id = id;
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
    private final Map<Integer, Generator> generators = new HashMap<>();
    private int nextId = 1;

    public GeneratorManager(PenguinMafiaMarket plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.file = new File(plugin.getDataFolder(), "generators.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        load();
        start();
    }

    private void load() {
        nextId = config.getInt("next-id", 1);
        ConfigurationSection section = config.getConfigurationSection("generators");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String path = "generators." + key;
            try {
                int id = Integer.parseInt(key);
                String world = config.getString(path + ".world");
                double x = config.getDouble(path + ".x");
                double y = config.getDouble(path + ".y");
                double z = config.getDouble(path + ".z");
                boolean coin = config.getBoolean(path + ".coin");
                String materialName = config.getString(path + ".material");
                int delaySeconds = Math.max(1, config.getInt(path + ".delay"));
                int amount = Math.max(1, config.getInt(path + ".amount"));
                generators.put(id, new Generator(id, world, x, y, z, coin, materialName, delaySeconds, amount));
            } catch (NumberFormatException ignored) {
                // malformed entry from hand-editing the file; skip it
            }
        }
        plugin.getLogger().info("Loaded " + generators.size() + " item generator(s).");
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

    /**
     * Creates a new generator at the given location and fires it once right
     * away, OR - if one already exists within 2 blocks - removes that one
     * instead (a toggle, same pattern as /afk). Returns null when it
     * removed an existing one.
     */
    public Generator toggle(Location location, boolean coin, String materialName, int delaySeconds, int amount) {
        Generator nearby = findNear(location);
        if (nearby != null) {
            generators.remove(nearby.id);
            save();
            return null;
        }
        int id = nextId++;
        Generator gen = new Generator(id, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                coin, materialName, delaySeconds, amount);
        generators.put(id, gen);
        save();
        spawn(gen);
        return gen;
    }

    public boolean remove(int id) {
        boolean removed = generators.remove(id) != null;
        if (removed) save();
        return removed;
    }

    public List<Generator> list() {
        return new ArrayList<>(generators.values());
    }

    private Generator findNear(Location location) {
        for (Generator gen : generators.values()) {
            if (!gen.world.equals(location.getWorld().getName())) continue;
            double dx = gen.x - location.getX();
            double dy = gen.y - location.getY();
            double dz = gen.z - location.getZ();
            if (dx * dx + dy * dy + dz * dz <= 4.0) { // within 2 blocks
                return gen;
            }
        }
        return null;
    }

    private void save() {
        config.set("generators", null);
        config.set("next-id", nextId);
        for (Generator gen : generators.values()) {
            String path = "generators." + gen.id;
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
