package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Extracts the bundled "penguin_mafia" data pack (biomes, mobs, structures,
 * loot, etc.) out of this plugin's jar and installs it into the server's
 * world/datapacks folder, so the whole server pack is just one file to
 * install: this plugin's jar.
 *
 * Minecraft only loads world-generation registries (new biomes, new
 * structures) once, at world/server startup, so a data pack that wasn't
 * present yet when the server started can't be "reloaded" in - that's a
 * limitation of vanilla Minecraft itself, not something a plugin can work
 * around. So the first time this installs the pack, it needs one full
 * restart to take effect (the same restart you'd need anyway after adding
 * any new plugin). Every time after that, everything is already in place.
 */
public class DatapackInstaller {

    // Every file inside the bundled data pack, relative to
    // src/main/resources/datapack/penguin_mafia/, i.e. also relative to
    // world/datapacks/penguin_mafia/ once installed.
    private static final String[] FILES = {
        "data/minecraft/tags/function/load.json",
        "data/minecraft/tags/function/tick.json",
        "data/minecraft/tags/worldgen/biome/is_overworld.json",
        "data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json",
        "data/penguin_mafia/advancement/looted_cabin.json",
        "data/penguin_mafia/advancement/meet_the_don.json",
        "data/penguin_mafia/advancement/reached_peaks.json",
        "data/penguin_mafia/advancement/root.json",
        "data/penguin_mafia/advancement/tame_penguin.json",
        "data/penguin_mafia/function/blizzard/apply.mcfunction",
        "data/penguin_mafia/function/blizzard/apply_single.mcfunction",
        "data/penguin_mafia/function/blizzard/end.mcfunction",
        "data/penguin_mafia/function/blizzard/start.mcfunction",
        "data/penguin_mafia/function/blizzard/tick.mcfunction",
        "data/penguin_mafia/function/blizzard/toggle.mcfunction",
        "data/penguin_mafia/function/don/attempt_spawn.mcfunction",
        "data/penguin_mafia/function/don/spawn.mcfunction",
        "data/penguin_mafia/function/don/tick.mcfunction",
        "data/penguin_mafia/function/load.mcfunction",
        "data/penguin_mafia/function/market/give_coins.mcfunction",
        "data/penguin_mafia/function/market/summon_fence.mcfunction",
        "data/penguin_mafia/function/rival/do_spawn.mcfunction",
        "data/penguin_mafia/function/rival/spawn.mcfunction",
        "data/penguin_mafia/function/rival/tick.mcfunction",
        "data/penguin_mafia/function/tag_penguin.mcfunction",
        "data/penguin_mafia/function/tame/check.mcfunction",
        "data/penguin_mafia/function/tame/do_tame.mcfunction",
        "data/penguin_mafia/function/tame/follow.mcfunction",
        "data/penguin_mafia/function/tame/progress.mcfunction",
        "data/penguin_mafia/function/tame/snap_to_owner.mcfunction",
        "data/penguin_mafia/function/tick.mcfunction",
        "data/penguin_mafia/loot_table/chests/hideout_cabin.json",
        "data/penguin_mafia/loot_table/chests/hideout_cabin_food.json",
        "data/penguin_mafia/loot_table/entities/don_death.json",
        "data/penguin_mafia/loot_table/entities/rival_death.json",
        "data/penguin_mafia/structure/hideout_cabin.nbt",
        "data/penguin_mafia/structure/mountain_bridge.nbt",
        "data/penguin_mafia/worldgen/biome/mafia_peaks.json",
        "data/penguin_mafia/worldgen/biome/mafia_tundra.json",
        "data/penguin_mafia/worldgen/structure/hideout_cabin.json",
        "data/penguin_mafia/worldgen/structure/mountain_bridge.json",
        "data/penguin_mafia/worldgen/structure_set/hideout_cabin.json",
        "data/penguin_mafia/worldgen/structure_set/mountain_bridge.json",
        "data/penguin_mafia/worldgen/template_pool/hideout_cabin/main.json",
        "data/penguin_mafia/worldgen/template_pool/mountain_bridge/main.json",
        "pack.mcmeta",
    };

    /**
     * Installs the bundled data pack if it isn't already present (or is
     * from an older version of this plugin). Returns true if files were
     * freshly written (meaning the server needs a restart for them to take
     * effect), false if everything was already up to date.
     */
    public static boolean installIfNeeded(JavaPlugin plugin) {
        File datapacksDir = findDatapacksDir();
        if (datapacksDir == null) {
            plugin.getLogger().warning("Could not locate world/datapacks folder automatically. "
                    + "The bundled data pack was not installed - you may need to install it manually.");
            return false;
        }

        File packDir = new File(datapacksDir, "penguin_mafia");
        File markerFile = new File(packDir, ".installed-by-plugin");
        String version = plugin.getDescription().getVersion();

        boolean upToDate = false;
        if (markerFile.exists()) {
            try {
                String installedVersion = new String(Files.readAllBytes(markerFile.toPath())).trim();
                upToDate = installedVersion.equals(version);
            } catch (IOException ignored) {
                upToDate = false;
            }
        }

        if (upToDate) {
            return false;
        }

        int written = 0;
        for (String relativePath : FILES) {
            try {
                copyResource(plugin, "datapack/penguin_mafia/" + relativePath,
                        new File(packDir, relativePath));
                written++;
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to install data pack file " + relativePath
                        + ": " + e.getMessage());
            }
        }

        try {
            Files.write(markerFile.toPath(), version.getBytes());
        } catch (IOException ignored) {
            // Non-fatal - worst case we re-copy the files next boot.
        }

        plugin.getLogger().info("Installed/updated the bundled Penguin Mafia data pack ("
                + written + " files) into " + packDir.getPath());
        return true;
    }

    private static void copyResource(JavaPlugin plugin, String resourcePath, File destination) throws IOException {
        destination.getParentFile().mkdirs();
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                throw new IOException("resource not found in jar: " + resourcePath);
            }
            try (OutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
        }
    }

    /**
     * Finds the world/datapacks folder for the server's primary (default)
     * world, e.g. "world/datapacks". Falls back to Bukkit.getWorldContainer()
     * plus the configured level-name if no world is loaded yet at this
     * point (data pack installation runs from onLoad(), before worlds are
     * loaded).
     */
    private static File findDatapacksDir() {
        File worldContainer = Bukkit.getWorldContainer();

        String worldName = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getName();
        if (worldName == null) {
            // No world loaded yet (normal this early in startup) - fall
            // back to the conventional default world folder name, which
            // matches server.properties' level-name unless the server
            // owner changed it.
            worldName = "world";
        }

        File worldFolder = new File(worldContainer, worldName);
        return new File(worldFolder, "datapacks");
    }
}
