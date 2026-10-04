package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /afk (op-only): toggles an AFK Farm at the op's current location. While
 * it's on, the chunks there stay force-loaded (so a nearby /gen generator or
 * a normal farm keeps ticking), any dropped Frozen Coins in the area are
 * vacuumed up and credited straight to the op's balance - even while fully
 * disconnected - and everything else collected waits in a mailbox delivered
 * next time they join. Run /afk again, from anywhere, to turn it off.
 */
public class AfkCommand implements CommandExecutor {

    private final AfkFarmManager manager;

    public AfkCommand(AfkFarmManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /afk.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("penguinmafia.afk")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use /afk.");
            return true;
        }

        if (manager.isActive(player)) {
            manager.stop(player);
            player.sendMessage(ChatColor.GRAY + "AFK Farm stopped.");
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

        int applied = manager.start(player, radius);
        int size = applied * 2 + 1;
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "AFK Farm started."
                + ChatColor.RESET + ChatColor.GRAY + " Keeping a " + size + "x" + size + " chunk area loaded here, "
                + "vacuuming up Frozen Coins into your balance and holding everything else for you - even offline.");
        player.sendMessage(ChatColor.GRAY + "Run /afk again, from anywhere, to turn it off.");
        return true;
    }
}
