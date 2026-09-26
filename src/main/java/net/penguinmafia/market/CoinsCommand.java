package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Op-only shortcut to grant (or take away) Frozen Coin balance directly,
 * with no physical item, renaming, or /give syntax involved.
 *
 *   /coins <amount>            - adds <amount> to your own balance
 *   /coins <player> <amount>   - adds <amount> to another player's balance
 *
 * <amount> can be negative to remove coins.
 */
public class CoinsCommand implements CommandExecutor {

    private final Economy economy;

    public CoinsCommand(Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Only ops can use /coins.");
            return true;
        }

        OfflinePlayer target;
        long amount;

        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Console must use /coins <player> <amount>.");
                return true;
            }
            target = (Player) sender;
            amount = parseAmount(sender, args[0]);
        } else if (args.length == 2) {
            target = Bukkit.getOfflinePlayer(args[0]);
            amount = parseAmount(sender, args[1]);
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /coins <amount> or /coins <player> <amount>");
            return true;
        }

        if (amount == Long.MIN_VALUE) {
            // parseAmount already sent an error message.
            return true;
        }

        long newBalance = economy.getBalance(target) + amount;
        economy.setBalance(target, newBalance);

        String targetName = target.getName() != null ? target.getName() : args.length == 2 ? args[0] : sender.getName();
        sender.sendMessage(ChatColor.AQUA + (amount >= 0 ? "Gave " : "Removed ") + Math.abs(amount)
                + " Frozen Coin" + (Math.abs(amount) == 1 ? "" : "s") + (amount >= 0 ? " to " : " from ")
                + targetName + ChatColor.GRAY + " (new balance: " + economy.getBalance(target) + ")");

        if (target instanceof Player && target.isOnline() && !target.equals(sender)) {
            ((Player) target).sendMessage(ChatColor.AQUA + "An op " + (amount >= 0 ? "gave you " : "took ")
                    + Math.abs(amount) + " Frozen Coin" + (Math.abs(amount) == 1 ? "" : "s")
                    + (amount >= 0 ? "." : " from you.") + ChatColor.GRAY + " New balance: " + economy.getBalance(target));
        }

        return true;
    }

    /** Returns Long.MIN_VALUE as a sentinel for "invalid, already reported to sender". */
    private long parseAmount(CommandSender sender, String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Amount must be a whole number, got: " + raw);
            return Long.MIN_VALUE;
        }
    }
}
