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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /daily - claim a once-per-day login bonus that grows with a consecutive-day streak. */
public class DailyRewardCommand implements CommandExecutor, TabCompleter {

    private final DailyRewardManager rewards;

    public DailyRewardCommand(DailyRewardManager rewards) {
        this.rewards = rewards;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("top")) {
            return handleTop(sender);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can claim the daily reward.");
            return true;
        }

        if (!rewards.canClaim(player.getUniqueId())) {
            long hours = rewards.hoursUntilNextClaim(player.getUniqueId());
            player.sendMessage(ChatColor.GRAY + "You've already claimed today's reward - come back in about "
                    + Math.max(1, hours) + " hour(s). Current streak: " + ChatColor.AQUA
                    + rewards.getStreak(player.getUniqueId()) + ChatColor.GRAY + " day(s).");
            return true;
        }

        DailyRewardManager.ClaimResult result = rewards.claim(player);
        player.sendMessage(ChatColor.GOLD + "[Daily] " + ChatColor.GRAY + "Claimed " + ChatColor.AQUA
                + CoinFormat.format(result.payout) + ChatColor.GRAY + " Frozen Coins - "
                + ChatColor.WHITE + result.streak + " day streak" + ChatColor.GRAY
                + "! Come back tomorrow to keep it going.");
        return true;
    }

    private boolean handleTop(CommandSender sender) {
        List<Map.Entry<UUID, Integer>> top = rewards.getTopStreaks(10);
        if (top.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Nobody has a daily login streak going yet.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "--- Longest Daily Streaks ---");
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : top) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            String name = player.getName() != null ? player.getName() : entry.getKey().toString();
            sender.sendMessage(ChatColor.YELLOW + "" + rank + ". " + ChatColor.WHITE + name
                    + ChatColor.GRAY + " - " + ChatColor.AQUA + entry.getValue() + ChatColor.GRAY + " day(s)");
            rank++;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            if ("top".startsWith(args[0].toLowerCase())) out.add("top");
            return out;
        }
        return new ArrayList<>();
    }
}
