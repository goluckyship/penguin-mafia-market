package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /pay <player> <amount> - send some of your own Frozen Coin balance to another player. */
public class PayCommand implements CommandExecutor, TabCompleter {

    private final Economy economy;

    public PayCommand(Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /pay.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /pay <player> <amount>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "No player named '" + args[0] + "' has ever joined this server.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "You can't pay yourself.");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Amount must be a whole number.");
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be greater than 0.");
            return true;
        }

        if (!economy.removeBalance(player, amount)) {
            sender.sendMessage(ChatColor.RED + "You don't have " + amount + " Frozen Coins. Balance: "
                    + economy.getBalance(player));
            return true;
        }

        economy.addBalance(target, amount);

        String targetName = target.getName() != null ? target.getName() : args[0];
        sender.sendMessage(ChatColor.AQUA + "Paid " + amount + " Frozen Coin" + (amount == 1 ? "" : "s")
                + " to " + targetName + ChatColor.GRAY + " (your new balance: " + economy.getBalance(player) + ")");

        Player targetOnline = target.getPlayer();
        if (targetOnline != null) {
            targetOnline.sendMessage(ChatColor.AQUA + player.getName() + " paid you " + amount
                    + " Frozen Coin" + (amount == 1 ? "" : "s") + ChatColor.GRAY
                    + " (your new balance: " + economy.getBalance(target) + ")");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) names.add(p.getName());
            }
            return names;
        }
        return Arrays.asList();
    }
}
