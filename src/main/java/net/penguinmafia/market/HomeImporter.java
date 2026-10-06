package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /homeimport [folder] - copies homes saved by another homes plugin (e.g.
 * DonutHomes, EssentialsX) into this plugin's /home slots so nobody loses
 * theirs. It doesn't know any one plugin's exact file layout, so it reads
 * every .yml under plugins/<folder>/ and picks out anything that looks like
 * a saved location (a section with world/x/y/z, a serialized Location, or a
 * "world,x,y,z" string), owned by the player whose UUID names the file (or a
 * UUID key above it). Safe to run repeatedly: spots a player already has are
 * skipped, and the old plugin's files are only read, never changed.
 */
public final class HomeImporter {

    private HomeImporter() {}

    private static final class Found {
        final UUID owner;
        final String name;
        final Location loc;
        Found(UUID owner, String name, Location loc) {
            this.owner = owner;
            this.name = name;
            this.loc = loc;
        }
    }

    public static boolean run(CommandSender sender, HomeManager homes, String[] args) {
        if (!sender.hasPermission("penguinmafia.homes.import")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use /homeimport.");
            return true;
        }
        File plugins = Bukkit.getPluginsFolder();
        List<File> roots = new ArrayList<>();
        File[] dirs = plugins.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                String name = dir.getName().toLowerCase();
                boolean ours = name.equals("penguinmafiamarket");
                boolean wanted = args.length > 0 ? name.equals(args[0].toLowerCase()) : name.contains("home");
                if (!ours && wanted) roots.add(dir);
            }
        }
        if (roots.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No old homes folder found" + (args.length > 0 ? " named " + args[0] : " (looking for a plugins/ folder with 'home' in its name)")
                    + ". Use /homeimport <exact folder name>, e.g. /homeimport DonutHomes.");
            return true;
        }

        List<Found> found = new ArrayList<>();
        int files = 0;
        for (File root : roots) {
            files += scan(root, 0, found);
        }

        int imported = 0, duplicates = 0, full = 0, noWorld = 0;
        java.util.Set<UUID> players = new java.util.HashSet<>();
        for (Found f : found) {
            if (homes.hasNear(f.owner, f.loc)) { duplicates++; continue; }
            int slot = homes.nextFree(f.owner);
            if (slot < 0) { full++; continue; }
            homes.set(f.owner, slot, f.loc, f.name);
            imported++;
            players.add(f.owner);
        }
        homes.saveNow();

        StringBuilder folders = new StringBuilder();
        for (File r : roots) folders.append(folders.length() > 0 ? ", " : "").append(r.getName());
        sender.sendMessage(ChatColor.GREEN + "Home import finished (" + folders + ", " + files + " files read).");
        sender.sendMessage(ChatColor.GRAY + "Imported " + imported + " homes for " + players.size() + " players. Skipped: "
                + duplicates + " already imported, " + full + " over the 18-home limit.");
        if (found.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Found no homes in those files - tell me the folder name and a sample file's contents and I'll adapt the importer.");
        }
        return true;
    }

    private static int scan(File dir, int depth, List<Found> out) {
        int count = 0;
        File[] children = dir.listFiles();
        if (children == null || depth > 3) return 0;
        for (File child : children) {
            if (child.isDirectory()) {
                count += scan(child, depth + 1, out);
            } else if (child.getName().toLowerCase().endsWith(".yml")) {
                try {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(child);
                    String base = child.getName().substring(0, child.getName().length() - 4);
                    UUID owner = parseUuid(base);
                    if (owner == null && child.getParentFile() != null) owner = parseUuid(child.getParentFile().getName());
                    walk(yaml, owner, out);
                    count++;
                } catch (Exception ignored) {
                    // an unreadable file just gets skipped
                }
            }
        }
        return count;
    }

    private static void walk(ConfigurationSection section, UUID owner, List<Found> out) {
        if (owner == null) {
            String own = section.getString("uuid", section.getString("owner", null));
            owner = own == null ? null : parseUuid(own);
        }
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            UUID keyOwner = parseUuid(key);
            UUID nextOwner = keyOwner != null ? keyOwner : owner;

            if (value instanceof Location loc && owner != null) {
                if (loc.getWorld() != null) out.add(new Found(owner, key, loc));
            } else if (value instanceof ConfigurationSection child) {
                Location loc = fromSection(child);
                if (loc != null && owner != null) {
                    out.add(new Found(owner, child.getString("name", key), loc));
                } else {
                    walk(child, nextOwner, out);
                }
            } else if (value instanceof String text && owner != null) {
                Location loc = fromString(text);
                if (loc != null) out.add(new Found(owner, key, loc));
            }
        }
    }

    private static Location fromSection(ConfigurationSection s) {
        ConfigurationSection src = s.isConfigurationSection("location") ? s.getConfigurationSection("location") : s;
        if (src == null) return null;
        String worldName = src.getString("world", src.getString("World", null));
        if (worldName == null || !(src.contains("x") || src.contains("X"))) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                src.getDouble("x", src.getDouble("X")), src.getDouble("y", src.getDouble("Y")), src.getDouble("z", src.getDouble("Z")),
                (float) src.getDouble("yaw", src.getDouble("Yaw")), (float) src.getDouble("pitch", src.getDouble("Pitch")));
    }

    /** "world,x,y,z[,yaw,pitch]" separated by commas, semicolons, colons or spaces. */
    private static Location fromString(String text) {
        String[] parts = text.trim().split("[,; ]+");
        if (parts.length < 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
