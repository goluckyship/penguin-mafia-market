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

    public MarketCommand(PenguinMafiaMarket plugin, Economy economy, MarketManager market) {
        this.plugin = plugin;
        this.economy = economy;
        this.market = market;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use the Black Market.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            MarketGUI.open(player, market);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "sell":
                return handleSell(player, args);
            case "list":
                return handleList(player);
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

    private boolean handleList(Player player) {
        List<Listing> mine = market.getListingsBy(player.getUniqueId());
        if (mine.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "You have no active listings.");
            return true;
        }
        player.sendMessage(ChatColor.LIGHT_PURPLE + "--- Your Black Market Listings ---");
        for (Listing l : mine) {
            player.sendMessage(ChatColor.GRAY + "#" + l.id + " " + describeItem(l.item)
                    + ChatColor.GRAY + " - " + ChatColor.AQUA + l.price + " coins");
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
        player.sendMessage(ChatColor.GRAY + "/bm " + ChatColor.WHITE + "- open the market GUI");
        player.sendMessage(ChatColor.GRAY + "/bm sell <price> " + ChatColor.WHITE + "- list the item in your hand");
        player.sendMessage(ChatColor.GRAY + "/bm list " + ChatColor.WHITE + "- see your own listings");
        player.sendMessage(ChatColor.GRAY + "/bm cancel <id> " + ChatColor.WHITE + "- cancel a listing");
        player.sendMessage(ChatColor.GRAY + "/bm balance " + ChatColor.WHITE + "- check your coin balance");
        player.sendMessage(ChatColor.GRAY + "/bm deposit " + ChatColor.WHITE + "- turn held coins into balance");
        player.sendMessage(ChatColor.GRAY + "/bm withdraw <amount> " + ChatColor.WHITE + "- turn balance into held coins");
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
            return filter(Arrays.asList("sell", "list", "cancel", "balance", "deposit", "withdraw", "help"), args[0]);
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
