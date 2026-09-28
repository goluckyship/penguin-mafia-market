package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /afk (op-only): toggles a force-loaded chunk area centered on the op's
 * current location, so the chunks there keep ticking - farms, redstone,
 * anything - even after they log out. Runs it again to turn it back off.
 */
public class AfkCommand implements CommandExecutor {

    private final AfkChunkLoaderManager manager;

    public AfkCommand(AfkChunkLoaderManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /afk.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "Only ops can use /afk.");
            return true;
        }

        int radius = 1; // default: a 3x3 chunk area
        if (args.length > 0) {
            try {
                radius = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Usage: /afk [radius] - radius is in chunks, 0-3.");
                return true;
            }
        }

        AfkChunkLoaderManager.ToggleResult result = manager.toggle(player, radius);
        if (result.enabled) {
            int size = result.radius * 2 + 1;
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "AFK chunk-loading enabled."
                    + ChatColor.RESET + ChatColor.GRAY + " Keeping a " + size + "x" + size + " chunk area loaded around "
                    + result.centerX * 16 + ", " + result.centerZ * 16 + " in " + result.world + ".");
            player.sendMessage(ChatColor.GRAY + "This stays loaded even after you log out - run /afk again to turn it off.");
        } else {
            player.sendMessage(ChatColor.GRAY + "AFK chunk-loading disabled. Those chunks will unload normally now"
                    + " (unless another op's /afk zone still overlaps them).");
        }
        return true;
    }
}
