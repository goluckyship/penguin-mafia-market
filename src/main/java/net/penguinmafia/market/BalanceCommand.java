package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /bal            - your own Frozen Coin balance (same as /bm balance)
 * /bal <player>   - anyone's balance - a player's coin count isn't private,
 *                   so no permission check beyond just running the command.
 */
public class BalanceCommand implements CommandExecutor, TabCompleter {

    private final Economy economy;

    public BalanceCommand(Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        OfflinePlayer target;
        boolean self;

        if (args.length == 0) {
            if (!(sender instanceof OfflinePlayer)) {
                sender.sendMessage(ChatColor.RED + "Console must use /bal <player>.");
                return true;
            }
            target = (OfflinePlayer) sender;
            self = true;
        } else {
            target = Bukkit.getOfflinePlayer(args[0]);
            self = sender.getName().equalsIgnoreCase(args[0]);
        }

        String displayName = target.getName() != null ? target.getName() : args[0];
        long balance = economy.getBalance(target);

        String shown = CoinFormat.formatWithExact(balance);

        if (self) {
            sender.sendMessage(ChatColor.AQUA + "Frozen Coin balance: " + ChatColor.BOLD + shown
                    + ChatColor.RESET + ChatColor.AQUA + " coins");
        } else {
            sender.sendMessage(ChatColor.AQUA + displayName + "'s Frozen Coin balance: " + ChatColor.BOLD
                    + shown + ChatColor.RESET + ChatColor.AQUA + " coins");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) names.add(p.getName());
            }
            return names;
        }
        return Arrays.asList();
    }
}
