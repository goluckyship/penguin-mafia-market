package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * /sandbox (aliased /sb): an op-only superflat world for testing and
 * building, kept completely separate from the survival Overworld and the
 * Frozen Realm.
 *
 * Deliberately NOT built the way the Frozen Realm is. That dimension comes
 * from a hand-authored datapack (a custom dimension_type/biome registered
 * via JSON), and a single malformed field in one of those files once made
 * the ENTIRE server refuse to boot - datapack registry parsing is all-or-
 * nothing for the whole server, not just the one broken dimension. The
 * sandbox instead is a plain Bukkit world created at runtime with
 * WorldCreator (the same mechanism behind any multi-world plugin) - there's
 * no JSON to get wrong and no registry to fail to parse, so it carries none
 * of that risk.
 */
public class SandboxCommand implements CommandExecutor {

    public static final String WORLD_NAME = "penguinmafia_sandbox";

    /** Creates the sandbox world if it doesn't exist yet, or just loads it if it does. Call from onEnable. */
    public static World createOrLoad(Plugin plugin) {
        World existing = Bukkit.getWorld(WORLD_NAME);
        if (existing != null) return existing;

        World world = new WorldCreator(WORLD_NAME)
                .type(WorldType.FLAT)
                .environment(World.Environment.NORMAL)
                .generateStructures(false)
                .createWorld();

        if (world == null) {
            plugin.getLogger().warning("Failed to create/load the sandbox world!");
            return null;
        }

        // A quiet space to build/test in - no hostile mobs wandering in, no
        // weather, no fire spread or explosions eating builds.
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setSpawnLocation(0, world.getHighestBlockYAt(0, 0) + 1, 0);
        plugin.getLogger().info("Sandbox world ready.");
        return world;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use the sandbox.");
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "Only ops can use /sandbox.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("back")) {
            World overworld = Bukkit.getWorlds().get(0);
            player.teleport(overworld.getSpawnLocation());
            player.sendMessage(ChatColor.AQUA + "Back in the Overworld.");
            return true;
        }

        World sandbox = Bukkit.getWorld(WORLD_NAME);
        if (sandbox == null) {
            player.sendMessage(ChatColor.RED + "The sandbox world isn't loaded - ask an admin to restart the server.");
            return true;
        }

        int surfaceY = sandbox.getHighestBlockYAt(0, 0);
        Location dest = new Location(sandbox, 0.5, surfaceY + 1, 0.5);
        player.teleport(dest);
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Welcome to the sandbox."
                + ChatColor.RESET + ChatColor.GRAY + " Use /sandbox back to return.");
        return true;
    }
}
