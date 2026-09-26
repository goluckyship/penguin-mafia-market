package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /ptr (Playtime Rewards) - op-only management of the passive Frozen Coin
 * payout that every online player gets every so often just for playing.
 * /ptr status               - show current settings
 * /ptr interval <minutes>   - how often the payout fires (default 10)
 * /ptr amount <coins>       - how many coins each payout gives (default 10)
 * /ptr on | off             - toggle it
 */
public class PlaytimeRewardCommand implements CommandExecutor {

    private final PlaytimeRewardManager manager;

    public PlaytimeRewardCommand(PlaytimeRewardManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Only ops can manage playtime rewards.");
            return true;
        }

        if (args.length == 0) {
            sendStatus(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status":
                sendStatus(sender);
                return true;
            case "interval": {
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /ptr interval <minutes>");
                    return true;
                }
                int minutes;
                try {
                    minutes = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "That's not a number.");
                    return true;
                }
                manager.setInterval(minutes);
                sender.sendMessage(ChatColor.AQUA + "Playtime rewards now pay out every "
                        + manager.getIntervalMinutes() + " minute" + (manager.getIntervalMinutes() == 1 ? "" : "s") + ".");
                return true;
            }
            case "amount": {
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /ptr amount <coins>");
                    return true;
                }
                long coins;
                try {
                    coins = Long.parseLong(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "That's not a number.");
                    return true;
                }
                manager.setAmount(coins);
                sender.sendMessage(ChatColor.AQUA + "Playtime rewards now pay " + manager.getAmount() + " Frozen Coin"
                        + (manager.getAmount() == 1 ? "" : "s") + " each time.");
                return true;
            }
            case "on":
                manager.setEnabled(true);
                sender.sendMessage(ChatColor.GREEN + "Playtime rewards enabled.");
                return true;
            case "off":
                manager.setEnabled(false);
                sender.sendMessage(ChatColor.RED + "Playtime rewards disabled.");
                return true;
            default:
                sendStatus(sender);
                return true;
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Playtime Rewards " + ChatColor.RESET
                + ChatColor.GRAY + "(" + (manager.isEnabled() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF")
                + ChatColor.GRAY + ")");
        sender.sendMessage(ChatColor.GRAY + "Every online player gets " + ChatColor.WHITE + manager.getAmount()
                + " Frozen Coin" + (manager.getAmount() == 1 ? "" : "s") + ChatColor.GRAY + " every "
                + ChatColor.WHITE + manager.getIntervalMinutes() + " minute" + (manager.getIntervalMinutes() == 1 ? "" : "s")
                + ChatColor.GRAY + ".");
        sender.sendMessage(ChatColor.DARK_GRAY + "/ptr interval <minutes>, /ptr amount <coins>, /ptr on|off");
    }
}
