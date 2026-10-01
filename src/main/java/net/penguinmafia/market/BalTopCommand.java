package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /baltop (/bt): a leaderboard of the top 10 Frozen Coin balances on the
 * server. Open to anyone, same as /bal - a balance isn't private info here.
 */
public class BalTopCommand implements CommandExecutor {

    private static final int TOP_N = 10;

    private final Economy economy;

    public BalTopCommand(Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<Map.Entry<UUID, Long>> top = economy.getTopBalances(TOP_N);

        sender.sendMessage(ChatColor.LIGHT_PURPLE + "--- Top Frozen Coin Balances ---");
        if (top.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Nobody has any Frozen Coins yet.");
            return true;
        }

        int rank = 1;
        for (Map.Entry<UUID, Long> entry : top) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = entry.getKey().toString().substring(0, 8);

            ChatColor rankColor = rank == 1 ? ChatColor.GOLD : rank == 2 ? ChatColor.GRAY
                    : rank == 3 ? ChatColor.YELLOW : ChatColor.WHITE;

            sender.sendMessage(rankColor + "#" + rank + " " + ChatColor.AQUA + name + ChatColor.GRAY + " - "
                    + ChatColor.WHITE + CoinFormat.formatWithExact(entry.getValue()));
            rank++;
        }
        return true;
    }
}
