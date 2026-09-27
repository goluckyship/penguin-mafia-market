package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Sends players to (and back from) the Frozen Realm - a custom dimension
 * added by the penguin_frozen_realm datapack, using the custom
 * "penguinmafia:frozen_wastes" biome everywhere. The dimension only exists
 * once that datapack is installed in world/datapacks and the server has
 * been restarted; until then this command just explains that.
 */
public class FrozenRealmCommand implements CommandExecutor {

    private static final NamespacedKey FROZEN_REALM_KEY = NamespacedKey.fromString("penguinmafia:frozen_realm");

    private final FrozenRealmStructures structures;

    public FrozenRealmCommand(FrozenRealmStructures structures) {
        this.structures = structures;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can travel to the Frozen Realm.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("back")) {
            World overworld = Bukkit.getWorlds().get(0);
            player.teleport(overworld.getSpawnLocation());
            player.sendMessage(ChatColor.AQUA + "Back in the Overworld.");
            return true;
        }

        World frozenRealm = FROZEN_REALM_KEY == null ? null : Bukkit.getWorld(FROZEN_REALM_KEY);
        if (frozenRealm == null) {
            player.sendMessage(ChatColor.RED + "The Frozen Realm isn't loaded on this server yet.");
            player.sendMessage(ChatColor.GRAY + "(An op needs to install the penguin_frozen_realm datapack and restart.)");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("scramble")) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "Only ops can use /frozenrealm scramble.");
                return true;
            }
            return scramble(player);
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("cabin")) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "Only ops can use /frozenrealm cabin.");
                return true;
            }
            return cabin(player, frozenRealm);
        }

        int surfaceY = frozenRealm.getHighestBlockYAt(0, 0);
        Location dest = new Location(frozenRealm, 0.5, surfaceY + 1, 0.5);
        player.teleport(dest);
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Welcome to the Frozen Realm."
                + ChatColor.RESET + ChatColor.GRAY + " Use /frozenrealm back to return.");
        player.sendMessage(ChatColor.GRAY + "Watch for glowing " + ChatColor.AQUA + "Frozen Reavers"
                + ChatColor.GRAY + " - tougher mobs that drop bonus Frozen Coins.");
        return true;
    }

    /**
     * Op-only: re-rolls the salts used by the deterministic cabin/bridge
     * placement grid, so every cabin and bridge generated from this point
     * on lands in a different set of grid cells - without touching the
     * world seed. The new salts are persisted, so this survives a server
     * restart. This only affects chunks generated after the scramble; it
     * doesn't move anything already built in already-generated chunks, so
     * to see a fully rescrambled layout the dimension's existing chunk
     * data needs to be wiped and the world let regenerate fresh.
     */
    private boolean scramble(Player player) {
        structures.scramble();
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Frozen Realm structure layout scrambled."
                + ChatColor.RESET + ChatColor.GRAY + " Cabins and bridges in newly generated chunks will land in "
                + "different spots. Existing chunks won't change until they're regenerated.");
        return true;
    }

    /**
     * Op-only: teleports the player straight to the nearest cabin,
     * force-generating it first if that chunk hasn't naturally loaded yet
     * (so this always finds a real cabin, not just an already-explored
     * one).
     */
    private boolean cabin(Player player, World frozenRealm) {
        if (!frozenRealm.getKey().equals(player.getWorld().getKey())) {
            player.sendMessage(ChatColor.RED + "You need to be in the Frozen Realm to teleport to a cabin.");
            player.sendMessage(ChatColor.GRAY + "Use /frozenrealm to teleport in first.");
            return true;
        }

        int chunkX = player.getLocation().getBlockX() >> 4;
        int chunkZ = player.getLocation().getBlockZ() >> 4;

        int[] cabin = structures.nearestCabin(frozenRealm, chunkX, chunkZ);
        if (cabin == null) {
            player.sendMessage(ChatColor.RED + "Couldn't find a cabin nearby - try again.");
            return true;
        }

        int blockX = (cabin[0] << 4) + 8;
        int blockZ = (cabin[1] << 4) + 8;

        // Force the chunk to actually generate/load, then guarantee the
        // cabin is really there (harmless if it already built naturally).
        frozenRealm.getChunkAt(cabin[0], cabin[1]).load(true);
        structures.forceBuildCabin(frozenRealm, blockX, blockZ);

        int surfaceY = frozenRealm.getHighestBlockYAt(blockX, blockZ);
        player.teleport(new Location(frozenRealm, blockX + 0.5, surfaceY + 2, blockZ + 6.5));
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Teleported to a cabin"
                + ChatColor.RESET + ChatColor.GRAY + " at " + blockX + ", " + surfaceY + ", " + blockZ + ".");
        return true;
    }
}
