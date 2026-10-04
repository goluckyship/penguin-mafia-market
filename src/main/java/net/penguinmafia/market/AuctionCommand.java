package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /auction (alias /ah) - the Auction House. Modeled on /bm and /job's
 * subcommand style: no args shows your own open auctions, everything else is
 * a named subcommand.
 */
public class AuctionCommand implements CommandExecutor, TabCompleter {

    private final AuctionManager auctions;

    public AuctionCommand(AuctionManager auctions) {
        this.auctions = auctions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return handleMine(sender);
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "sell":
                return handleSell(sender, args);
            case "bid":
                return handleBid(sender, args);
            case "list":
                return handleList(sender);
            case "info":
                return handleInfo(sender, args);
            case "cancel":
                return handleCancel(sender, args);
            case "mine":
                return handleMine(sender);
            case "history":
                return handleHistory(sender, args);
            case "help":
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleSell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can sell at the Auction House.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /auction sell <starting price> <minutes>");
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "Hold the item you want to auction and try again.");
            return true;
        }

        long startingPrice;
        long minutes;
        try {
            startingPrice = Long.parseLong(args[1]);
            minutes = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Starting price and minutes both need to be whole numbers.");
            return true;
        }
        if (startingPrice <= 0) {
            player.sendMessage(ChatColor.RED + "Starting price has to be more than 0.");
            return true;
        }
        if (minutes < AuctionManager.MIN_DURATION_MINUTES || minutes > AuctionManager.MAX_DURATION_MINUTES) {
            player.sendMessage(ChatColor.RED + "Duration has to be between " + AuctionManager.MIN_DURATION_MINUTES
                    + " and " + AuctionManager.MAX_DURATION_MINUTES + " minutes.");
            return true;
        }

        ItemStack listed = hand.clone();
        player.getInventory().setItemInMainHand(null);
        Auction auction = auctions.createAuction(player, listed, startingPrice, minutes);

        player.sendMessage(ChatColor.GOLD + "[Auction] " + ChatColor.GRAY + "Listed "
                + describe(listed) + ChatColor.GRAY + " as auction " + ChatColor.WHITE + "#" + auction.id
                + ChatColor.GRAY + ", starting at " + CoinFormat.format(startingPrice) + " coins for "
                + minutes + " minute(s).");
        return true;
    }

    private boolean handleBid(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can bid.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /auction bid <id> <amount>");
            return true;
        }

        int id;
        long amount;
        try {
            id = Integer.parseInt(args[1]);
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Auction id and amount both need to be whole numbers.");
            return true;
        }

        Auction auction = auctions.get(id);
        AuctionManager.BidResult result = auctions.bid(player, id, amount);
        switch (result) {
            case OK:
                player.sendMessage(ChatColor.GOLD + "[Auction] " + ChatColor.GRAY + "Bid "
                        + CoinFormat.format(amount) + " coins on auction #" + id + " - you're the highest bidder!");
                return true;
            case NOT_FOUND:
                player.sendMessage(ChatColor.RED + "No auction with id " + id + ".");
                return true;
            case EXPIRED:
                player.sendMessage(ChatColor.RED + "That auction has already ended.");
                return true;
            case OWN_AUCTION:
                player.sendMessage(ChatColor.RED + "You can't bid on your own auction.");
                return true;
            case TOO_LOW:
                long minimum = auction.hasBid()
                        ? auction.currentBid + auctions.getMinIncrement(auction)
                        : auction.startingPrice;
                player.sendMessage(ChatColor.RED + "Bid too low - needs to be at least "
                        + CoinFormat.format(minimum) + " coins.");
                return true;
            case CANT_AFFORD:
                player.sendMessage(ChatColor.RED + "You don't have " + CoinFormat.format(amount) + " coins.");
                return true;
            default:
                return true;
        }
    }

    private boolean handleList(CommandSender sender) {
        List<Auction> active = auctions.getActiveAuctions();
        if (active.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "The Auction House is empty right now - /auction sell to list something.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "--- Auction House (" + active.size() + " active) ---");
        for (Auction auction : active) {
            sender.sendMessage(formatRow(auction));
        }
        sender.sendMessage(ChatColor.GRAY + "/auction info <id> for details, /auction bid <id> <amount> to bid.");
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /auction info <id>");
            return true;
        }
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Auction id needs to be a number.");
            return true;
        }
        Auction auction = auctions.get(id);
        if (auction == null) {
            sender.sendMessage(ChatColor.RED + "No auction with id " + id + ".");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "--- Auction #" + auction.id + " ---");
        sender.sendMessage(ChatColor.GRAY + "Item: " + ChatColor.WHITE + describe(auction.item));
        sender.sendMessage(ChatColor.GRAY + "Seller: " + ChatColor.WHITE + auction.sellerName);
        sender.sendMessage(ChatColor.GRAY + "Starting price: " + ChatColor.WHITE + CoinFormat.format(auction.startingPrice));
        if (auction.hasBid()) {
            sender.sendMessage(ChatColor.GRAY + "Current bid: " + ChatColor.WHITE + CoinFormat.format(auction.currentBid)
                    + ChatColor.GRAY + " by " + ChatColor.WHITE + auction.currentBidderName);
            sender.sendMessage(ChatColor.GRAY + "Next bid must be at least: " + ChatColor.WHITE
                    + CoinFormat.format(auction.currentBid + auctions.getMinIncrement(auction)));
        } else {
            sender.sendMessage(ChatColor.GRAY + "No bids yet - next bid must be at least "
                    + ChatColor.WHITE + CoinFormat.format(auction.startingPrice));
        }
        sender.sendMessage(ChatColor.GRAY + "Time left: " + ChatColor.WHITE + formatTimeLeft(auction));
        return true;
    }

    private boolean handleCancel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can cancel their own auctions.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /auction cancel <id>");
            return true;
        }
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Auction id needs to be a number.");
            return true;
        }

        boolean canForceCancel = player.hasPermission("penguinmafia.auction.forcecancel");
        AuctionManager.CancelResult result = auctions.cancel(player, id, canForceCancel);
        switch (result) {
            case OK:
                player.sendMessage(ChatColor.GRAY + "Auction #" + id + " cancelled - the item has been returned.");
                return true;
            case NOT_FOUND:
                player.sendMessage(ChatColor.RED + "No auction with id " + id + ".");
                return true;
            case NOT_OWNER:
                player.sendMessage(ChatColor.RED + "That's not your auction.");
                return true;
            case HAS_BIDS:
                player.sendMessage(ChatColor.RED + "Can't cancel - it already has a bid. Ask an op if this really needs pulling.");
                return true;
            default:
                return true;
        }
    }

    private boolean handleMine(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendHelp(sender);
            return true;
        }
        List<Auction> mine = auctions.getAuctionsBySeller(player.getUniqueId());
        if (mine.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "You don't have any auctions running. /auction sell <price> <minutes> while holding an item.");
            return true;
        }
        player.sendMessage(ChatColor.GOLD + "--- Your auctions ---");
        for (Auction auction : mine) {
            player.sendMessage(formatRow(auction));
        }
        return true;
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        String name;
        if (args.length >= 2) {
            name = args[1];
        } else if (sender instanceof Player player) {
            name = player.getName();
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /auction history <player>");
            return true;
        }

        List<AuctionManager.HistoryEntry> entries = auctions.getHistoryFor(name, 10);
        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + name + " has no completed auctions on record.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "--- " + name + "'s auction history (" + entries.size() + ") ---");
        for (AuctionManager.HistoryEntry entry : entries) {
            if (entry.sold()) {
                sender.sendMessage(ChatColor.GRAY + "#" + entry.id + " " + ChatColor.WHITE + entry.itemDescription
                        + ChatColor.GRAY + " - sold by " + entry.sellerName + " to " + entry.winnerName
                        + " for " + ChatColor.AQUA + CoinFormat.format(entry.finalPrice));
            } else {
                sender.sendMessage(ChatColor.GRAY + "#" + entry.id + " " + ChatColor.WHITE + entry.itemDescription
                        + ChatColor.GRAY + " - listed by " + entry.sellerName + ", no bids (returned)");
            }
        }
        return true;
    }

    private String formatRow(Auction auction) {
        String bidPart = auction.hasBid()
                ? CoinFormat.format(auction.currentBid) + " (" + auction.currentBidderName + ")"
                : CoinFormat.format(auction.startingPrice) + " (no bids)";
        return ChatColor.YELLOW + "#" + auction.id + " " + ChatColor.WHITE + describe(auction.item)
                + ChatColor.GRAY + " - " + bidPart + ChatColor.GRAY + " - " + formatTimeLeft(auction)
                + ChatColor.GRAY + " left - by " + auction.sellerName;
    }

    private String describe(ItemStack item) {
        String name = item.getType().name().toLowerCase().replace('_', ' ');
        return (item.getAmount() > 1 ? item.getAmount() + "x " : "") + name;
    }

    private String formatTimeLeft(Auction auction) {
        long totalSeconds = auction.millisRemaining() / 1000L;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- Auction House ---");
        sender.sendMessage(ChatColor.GRAY + "/auction " + ChatColor.WHITE + "- see your own running auctions");
        sender.sendMessage(ChatColor.GRAY + "/auction sell <price> <minutes> " + ChatColor.WHITE + "- auction the item in your hand");
        sender.sendMessage(ChatColor.GRAY + "/auction list " + ChatColor.WHITE + "- see every active auction");
        sender.sendMessage(ChatColor.GRAY + "/auction info <id> " + ChatColor.WHITE + "- details on one auction");
        sender.sendMessage(ChatColor.GRAY + "/auction bid <id> <amount> " + ChatColor.WHITE + "- place a bid");
        sender.sendMessage(ChatColor.GRAY + "/auction cancel <id> " + ChatColor.WHITE + "- cancel your own auction (before any bids)");
        sender.sendMessage(ChatColor.GRAY + "/auction history [player] " + ChatColor.WHITE + "- past completed auctions");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("sell", "bid", "list", "info", "cancel", "mine", "history", "help"), args[0]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.startsWith(prefix.toLowerCase())) out.add(o);
        }
        return out;
    }
}
