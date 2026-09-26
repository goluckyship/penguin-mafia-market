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

        int surfaceY = frozenRealm.getHighestBlockYAt(0, 0);
        Location dest = new Location(frozenRealm, 0.5, surfaceY + 1, 0.5);
        player.teleport(dest);
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Welcome to the Frozen Realm."
                + ChatColor.RESET + ChatColor.GRAY + " Use /frozenrealm back to return.");
        return true;
    }
}
