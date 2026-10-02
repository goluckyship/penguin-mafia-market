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
import java.util.Map;
import java.util.UUID;

/** /bounty - place coins on another player's head, collected by whoever kills them in PvP. */
public class BountyCommand implements CommandExecutor, TabCompleter {

    private final BountyManager bounties;

    public BountyCommand(BountyManager bounties) {
        this.bounties = bounties;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return handleList(sender);
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "place":
                return handlePlace(sender, args);
            case "check":
                return handleCheck(sender, args);
            case "list":
                return handleList(sender);
            case "help":
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handlePlace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player placer)) {
            sender.sendMessage(ChatColor.RED + "Only players can place a bounty.");
            return true;
        }
        if (args.length < 3) {
            placer.sendMessage(ChatColor.RED + "Usage: /bounty place <player> <amount>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            placer.sendMessage(ChatColor.RED + "That player has never joined this server.");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            placer.sendMessage(ChatColor.RED + "Amount must be a whole number.");
            return true;
        }

        BountyManager.PlaceResult result = bounties.place(placer, target, amount);
        switch (result) {
            case OK:
                long total = bounties.getBounty(target.getUniqueId());
                Bukkit.broadcastMessage(ChatColor.DARK_RED + "[Bounty] " + ChatColor.GRAY + placer.getName()
                        + " put " + ChatColor.AQUA + CoinFormat.format(amount) + ChatColor.GRAY
                        + " on " + target.getName() + "'s head! (Total: " + ChatColor.AQUA
                        + CoinFormat.format(total) + ChatColor.GRAY + ")");
                return true;
            case SELF:
                placer.sendMessage(ChatColor.RED + "You can't put a bounty on yourself.");
                return true;
            case TOO_LOW:
                placer.sendMessage(ChatColor.RED + "Amount must be greater than 0.");
                return true;
            case CANT_AFFORD:
                placer.sendMessage(ChatColor.RED + "You don't have " + CoinFormat.format(amount) + " coins.");
                return true;
            default:
                return true;
        }
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /bounty check <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        long total = bounties.getBounty(target.getUniqueId());
        String name = target.getName() != null ? target.getName() : args[1];

        if (total <= 0) {
            sender.sendMessage(ChatColor.GRAY + name + " doesn't have a bounty right now.");
            return true;
        }

        sender.sendMessage(ChatColor.DARK_RED + name + "'s bounty: " + ChatColor.AQUA
                + CoinFormat.format(total) + " Frozen Coins");
        for (BountyManager.Contribution contribution : bounties.getContributions(target.getUniqueId())) {
            sender.sendMessage(ChatColor.GRAY + "  - " + contribution.placerName + ": "
                    + CoinFormat.format(contribution.amount));
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<Map.Entry<UUID, Long>> top = bounties.getTopBounties(10);
        if (top.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No active bounties right now. /bounty place <player> <amount> to start one.");
            return true;
        }

        sender.sendMessage(ChatColor.DARK_RED + "--- Active Bounties ---");
        int rank = 1;
        for (Map.Entry<UUID, Long> entry : top) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            String name = player.getName() != null ? player.getName() : entry.getKey().toString();
            sender.sendMessage(ChatColor.YELLOW + "" + rank + ". " + ChatColor.WHITE + name
                    + ChatColor.GRAY + " - " + ChatColor.AQUA + CoinFormat.format(entry.getValue()));
            rank++;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_RED + "--- Bounties ---");
        sender.sendMessage(ChatColor.GRAY + "/bounty " + ChatColor.WHITE + "- see the current top bounties");
        sender.sendMessage(ChatColor.GRAY + "/bounty place <player> <amount> " + ChatColor.WHITE + "- put coins on someone's head (stacks with existing bounties)");
        sender.sendMessage(ChatColor.GRAY + "/bounty check <player> " + ChatColor.WHITE + "- see a player's bounty and who contributed");
        sender.sendMessage(ChatColor.GRAY + "/bounty list " + ChatColor.WHITE + "- same as no args, the top 10 bounties");
        sender.sendMessage(ChatColor.GRAY + "Collected automatically by whoever kills the target in PvP.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("place", "check", "list", "help"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("place") || args[0].equalsIgnoreCase("check"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(prefix.toLowerCase())) out.add(o);
        }
        return out;
    }
}
