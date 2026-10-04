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

public class MarketCommand implements CommandExecutor, TabCompleter {

    private final PenguinMafiaMarket plugin;
    private final Economy economy;
    private final MarketManager market;
    private final MarketGUI gui;
    private final MarketBotManager marketBotManager;
    private final TransactionLedger ledger;

    public MarketCommand(PenguinMafiaMarket plugin, Economy economy, MarketManager market, MarketGUI gui,
                          MarketBotManager marketBotManager, TransactionLedger ledger) {
        this.plugin = plugin;
        this.economy = economy;
        this.market = market;
        this.gui = gui;
        this.marketBotManager = marketBotManager;
        this.ledger = ledger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use the Black Market.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            gui.open(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "sell":
                return handleSell(player, args);
            case "list":
                gui.openMyListings(player, 0);
                return true;
            case "search":
                return handleSearch(player, args);
            case "restock":
                return handleRestock(player);
            case "admin":
                return handleAdmin(player, args);
            case "history":
                return handleHistory(player, args);
            case "top":
                return handleTop(player);
            case "cancel":
                return handleCancel(player, args);
            case "balance":
            case "bal":
                player.sendMessage(ChatColor.AQUA + "Frozen Coin balance: " + ChatColor.BOLD
                        + economy.getBalance(player) + ChatColor.RESET + ChatColor.AQUA + " coins");
                return true;
            case "deposit":
                return handleDeposit(player);
            case "withdraw":
                return handleWithdraw(player, args);
            case "help":
            default:
                sendHelp(player);
                return true;
        }
    }

    private boolean handleSell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /bm sell <price>");
            return true;
        }
        long price;
        try {
            price = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Price must be a whole number.");
            return true;
        }
        if (price <= 0) {
            player.sendMessage(ChatColor.RED + "Price must be greater than 0.");
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "Hold the item you want to sell in your main hand first.");
            return true;
        }
        if (economy.isCoinItem(hand)) {
            player.sendMessage(ChatColor.RED + "You can't list Frozen Coins themselves - use /bm deposit instead.");
            return true;
        }

        long activeListings = market.getListingsBy(player.getUniqueId()).size();
        if (activeListings >= 10) {
            player.sendMessage(ChatColor.RED + "You already have 10 active listings - cancel one with /bm cancel <id> first.");
            return true;
        }

        ItemStack toSell = hand.clone();
        player.getInventory().setItemInMainHand(null);
        Listing listing = market.createListing(player, toSell, price);

        player.sendMessage(ChatColor.LIGHT_PURPLE + "[Black Market] " + ChatColor.GRAY
                + "Listed " + describeItem(toSell) + ChatColor.GRAY + " for " + ChatColor.AQUA
                + price + " Frozen Coins" + ChatColor.GRAY + " (listing #" + listing.id + ").");
        return true;
    }

    /**
     * Command-line shortcut for the GUI's Compass search - jumps straight to
     * results instead of clicking Search and typing in chat. The in-GUI search
     * stays exactly as it was (compass click -> type in chat -> "cancel" to
     * back out), this is just a faster path to the same place for anyone who
     * prefers typing the whole thing on one line.
     */
    private boolean handleSearch(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /bm search <item or seller name>");
            return true;
        }
        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        gui.openWithSearch(player, query);
        player.sendMessage(ChatColor.GRAY + "Searching the Black Market for: " + ChatColor.WHITE + query);
        return true;
    }

    /**
     * Op-only manual trigger for the Black Market Dealer's restock - runs
     * the exact same top-up logic as the automatic 10-minute timer
     * (MarketBotManager.refresh()), immediately, without touching any
     * existing listing that hasn't sold yet.
     */
    private boolean handleRestock(Player player) {
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "Only ops can force a Black Market restock.");
            return true;
        }
        // refresh() already guards its own internal steps individually, but
        // this catch-all is the last line of defense: without it, anything
        // that still slipped through would propagate all the way out of
        // onCommand() and Bukkit would swallow it as its own generic
        // "An unexpected error occurred trying to execute that command"
        // message - which tells nobody, including whoever's looking at the
        // console, anything about what actually broke. Logging the real
        // exception here means the next failure (if any) is diagnosable
        // instead of a dead end.
        try {
            int restocked = marketBotManager.refresh();
            player.sendMessage(ChatColor.LIGHT_PURPLE + "[Black Market] " + ChatColor.GRAY
                    + "Restocked the dealer - added stock to " + ChatColor.AQUA + restocked + ChatColor.GRAY
                    + " item type(s).");

            // Report, by exact Material name, anything still priced wrong
            // right now - so a bad price can be diagnosed from what's
            // printed in chat instead of a screenshot of a GUI tooltip that
            // can't be matched back to a specific pricing rule on sight.
            List<String> problems = marketBotManager.auditUnderpricedListings();
            if (!problems.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "[Black Market] " + problems.size()
                        + " listing(s) still look underpriced after this restock:");
                for (String problem : problems) {
                    player.sendMessage(ChatColor.GRAY + " - " + problem);
                }
            }
        } catch (Throwable t) {
            // Throwable, not just Exception - a restock this size (every
            // non-banned material in the game, up to MIN_STOCK_STACKS
            // listings each) is heavy enough that even something like a
            // StackOverflowError during YAML serialization shouldn't be
            // able to slip past this and come out as Bukkit's own opaque
            // "unexpected error" message with nothing useful in the log.
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Black Market Dealer restock failed.", t);
            player.sendMessage(ChatColor.RED + "[Black Market] Restock failed - see the server console for details.");
        }
        return true;
    }

    /**
     * Op-only admin tools for the gap the README used to call out explicitly:
     * no way to wipe a stuck listing or refund someone without going through
     * the database by hand. All three act on a listing id, same as /bm cancel.
     */
    private boolean handleAdmin(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "Only ops can use /bm admin.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /bm admin <wipe|refund|setprice> <id> [price]");
            return true;
        }

        String action = args[1].toLowerCase();
        int id;
        try {
            id = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Listing id must be a number.");
            return true;
        }

        switch (action) {
            case "wipe": {
                boolean ok = market.adminWipe(id);
                player.sendMessage(ok
                        ? ChatColor.GRAY + "Listing #" + id + " wiped - the item is gone, nobody was refunded."
                        : ChatColor.RED + "No listing #" + id + " found.");
                return true;
            }
            case "refund": {
                boolean ok = market.adminRefund(id);
                if (ok) {
                    player.sendMessage(ChatColor.GRAY + "Listing #" + id + " refunded - the seller (must have been online) got the item back.");
                } else {
                    Listing listing = market.getListing(id);
                    player.sendMessage(listing == null
                            ? ChatColor.RED + "No listing #" + id + " found."
                            : ChatColor.RED + "Seller " + listing.sellerName + " needs to be online to receive the item - try again once they're on.");
                }
                return true;
            }
            case "setprice": {
                if (args.length < 4) {
                    player.sendMessage(ChatColor.RED + "Usage: /bm admin setprice <id> <price>");
                    return true;
                }
                long price;
                try {
                    price = Long.parseLong(args[3]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Price must be a whole number.");
                    return true;
                }
                boolean ok = market.adminSetPrice(id, price);
                player.sendMessage(ok
                        ? ChatColor.GRAY + "Listing #" + id + " price updated to " + ChatColor.AQUA + price + ChatColor.GRAY + " coins."
                        : ChatColor.RED + "No listing #" + id + " found, or price wasn't greater than 0.");
                return true;
            }
            default:
                player.sendMessage(ChatColor.RED + "Usage: /bm admin <wipe|refund|setprice> <id> [price]");
                return true;
        }
    }

    /**
     * /bm history [player] - recent completed sales involving a player
     * (buyer or seller side), for settling the "I never got paid" kind of
     * dispute with an actual record instead of memory. Anyone can check
     * their own history; checking someone else's requires op, same spirit
     * as /bm admin.
     */
    private boolean handleHistory(Player player, String[] args) {
        if (ledger == null) {
            player.sendMessage(ChatColor.RED + "Transaction history isn't available right now.");
            return true;
        }

        java.util.UUID targetId;
        String targetName;
        if (args.length >= 2) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "Only ops can check another player's history.");
                return true;
            }
            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
            targetId = target.getUniqueId();
            targetName = target.getName() != null ? target.getName() : args[1];
        } else {
            targetId = player.getUniqueId();
            targetName = player.getName();
        }

        List<TransactionLedger.Sale> history = ledger.getHistoryFor(targetId, 10);
        if (history.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + targetName + " has no recorded Black Market sales yet.");
            return true;
        }

        player.sendMessage(ChatColor.LIGHT_PURPLE + "--- " + targetName + "'s last " + history.size() + " sale(s) ---");
        for (TransactionLedger.Sale sale : history) {
            boolean wasSeller = sale.sellerId.equals(targetId);
            String role = wasSeller ? ChatColor.GREEN + "SOLD" : ChatColor.RED + "BOUGHT";
            String counterparty = wasSeller ? sale.buyerName : sale.sellerName;
            player.sendMessage(ChatColor.GRAY + "[" + TransactionLedger.formatTimestamp(sale.timestampMillis) + " UTC] "
                    + role + ChatColor.GRAY + " " + sale.itemDescription + " for " + ChatColor.AQUA
                    + sale.price + ChatColor.GRAY + " coins " + (wasSeller ? "to " : "from ") + counterparty);
        }
        return true;
    }

    /** /bm top - the 10 highest-earning sellers on the Black Market, by total coin revenue across every recorded sale. */
    private boolean handleTop(Player player) {
        if (ledger == null) {
            player.sendMessage(ChatColor.RED + "Seller rankings aren't available right now.");
            return true;
        }

        List<java.util.Map.Entry<java.util.UUID, Long>> top = ledger.getTopSellers(10);
        if (top.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Nobody's sold anything on the Black Market yet.");
            return true;
        }

        player.sendMessage(ChatColor.LIGHT_PURPLE + "--- Top Black Market Sellers ---");
        int rank = 1;
        for (java.util.Map.Entry<java.util.UUID, Long> entry : top) {
            org.bukkit.OfflinePlayer seller = org.bukkit.Bukkit.getOfflinePlayer(entry.getKey());
            String name = seller.getName() != null ? seller.getName() : entry.getKey().toString();
            player.sendMessage(ChatColor.YELLOW + "" + rank + ". " + ChatColor.WHITE + name
                    + ChatColor.GRAY + " - " + ChatColor.AQUA + entry.getValue() + ChatColor.GRAY + " coins earned");
            rank++;
        }
        return true;
    }

    private boolean handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /bm cancel <id>");
            return true;
        }
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Listing id must be a number.");
            return true;
        }
        boolean ok = market.cancelListing(id, player);
        if (ok) {
            player.sendMessage(ChatColor.GRAY + "Listing #" + id + " cancelled - item returned to you.");
        } else {
            player.sendMessage(ChatColor.RED + "No listing #" + id + " belonging to you was found.");
        }
        return true;
    }

    private boolean handleDeposit(Player player) {
        int total = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (economy.isCoinItem(item)) {
                total += item.getAmount();
                player.getInventory().setItem(i, null);
            }
        }
        if (total == 0) {
            player.sendMessage(ChatColor.RED + "You aren't carrying any physical Frozen Coins.");
            return true;
        }
        economy.addBalance(player, total);
        player.sendMessage(ChatColor.AQUA + "Deposited " + total + " Frozen Coins. New balance: "
                + economy.getBalance(player));
        return true;
    }

    private boolean handleWithdraw(Player player, String[] args) {
        if (economy.isAutoDepositEnabled(player)) {
            player.sendMessage(ChatColor.RED + "You can't withdraw while Auto-Deposit is on."
                    + ChatColor.GRAY + " Turn it off first with /ad.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /bm withdraw <amount>");
            return true;
        }
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Amount must be a whole number.");
            return true;
        }
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            player.sendMessage(ChatColor.RED + "Invalid amount.");
            return true;
        }
        if (amount > economy.getBalance(player)) {
            player.sendMessage(ChatColor.RED + "You don't have that many Frozen Coins in your balance.");
            return true;
        }

        long capacity = inventoryCapacityFor(player, economy.coinItem(1));
        if (capacity <= 0) {
            player.sendMessage(ChatColor.RED + "Your inventory is full - free up space before withdrawing.");
            return true;
        }

        // Never hand out more physical coins than the inventory can actually
        // hold - addItem() silently drops (or Bukkit-drops on the ground,
        // depending on version) whatever doesn't fit, and either way it's
        // not honest about it, so cap the withdrawal up front instead.
        long toWithdraw = Math.min(amount, capacity);
        if (!economy.removeBalance(player, toWithdraw)) {
            player.sendMessage(ChatColor.RED + "Something went wrong withdrawing your balance - try again.");
            return true;
        }

        int remaining = (int) toWithdraw;
        int maxStack = economy.coinItem(1).getMaxStackSize();
        while (remaining > 0) {
            int stack = Math.min(remaining, maxStack);
            player.getInventory().addItem(economy.coinItem(stack));
            remaining -= stack;
        }

        if (toWithdraw < amount) {
            player.sendMessage(ChatColor.AQUA + "Withdrew " + toWithdraw + " Frozen Coins" + ChatColor.RESET
                    + ChatColor.GRAY + " - that's all your inventory could hold right now (asked for " + amount + ").");
        } else {
            player.sendMessage(ChatColor.AQUA + "Withdrew " + amount + " Frozen Coins as physical items.");
        }
        return true;
    }

    /**
     * How many more of the sample item the player's main inventory (not
     * armor or offhand) could actually accept right now - counting empty
     * slots at the item's real max stack size and any room left in
     * existing compatible partial stacks, rather than assuming a fixed
     * 64-per-slot (some items have smaller max stacks, and existing
     * partial stacks have less room than a full one).
     */
    private long inventoryCapacityFor(Player player, ItemStack sample) {
        int maxStack = sample.getMaxStackSize();
        long capacity = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null) {
                capacity += maxStack;
            } else if (slot.isSimilar(sample) && slot.getAmount() < slot.getMaxStackSize()) {
                capacity += slot.getMaxStackSize() - slot.getAmount();
            }
        }
        return capacity;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "--- Penguin Mafia Black Market ---");
        player.sendMessage(ChatColor.GRAY + "/bm " + ChatColor.WHITE + "- open the market GUI (search, sort by price, and category filters, all remembered for you)");
        player.sendMessage(ChatColor.GRAY + "/bm sell <price> " + ChatColor.WHITE + "- list the item in your hand");
        player.sendMessage(ChatColor.GRAY + "/bm list " + ChatColor.WHITE + "- manage your listings (cancel with a click)");
        player.sendMessage(ChatColor.GRAY + "/bm search <item> " + ChatColor.WHITE + "- jump straight to a search (same as the GUI's Search button)");
        player.sendMessage(ChatColor.GRAY + "/bm restock " + ChatColor.WHITE + "- (op only) force the Black Market Dealer to add another round of stock now");
        player.sendMessage(ChatColor.GRAY + "/bm admin <wipe|refund|setprice> <id> [price] " + ChatColor.WHITE + "- (op only) fix a stuck or mispriced listing");
        player.sendMessage(ChatColor.GRAY + "/bm history [player] " + ChatColor.WHITE + "- recent completed sales (another player's requires op)");
        player.sendMessage(ChatColor.GRAY + "/bm top " + ChatColor.WHITE + "- the top 10 highest-earning sellers");
        player.sendMessage(ChatColor.GRAY + "/bm cancel <id> " + ChatColor.WHITE + "- cancel a listing");
        player.sendMessage(ChatColor.GRAY + "/bm balance " + ChatColor.WHITE + "- check your coin balance");
        player.sendMessage(ChatColor.GRAY + "/bm deposit " + ChatColor.WHITE + "- turn held coins into balance");
        player.sendMessage(ChatColor.GRAY + "/bm withdraw <amount> " + ChatColor.WHITE + "- turn balance into held coins");
        player.sendMessage(ChatColor.GRAY + "/ad " + ChatColor.WHITE + "- toggle Auto-Deposit (coins you pick up go straight to your balance)");
    }

    private String describeItem(ItemStack item) {
        String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : item.getType().toString().toLowerCase().replace('_', ' ');
        return ChatColor.WHITE.toString() + item.getAmount() + "x " + name;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("sell", "list", "search", "restock", "admin", "history", "top", "cancel", "balance", "deposit", "withdraw", "help"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return filter(Arrays.asList("wipe", "refund", "setprice"), args[1]);
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
