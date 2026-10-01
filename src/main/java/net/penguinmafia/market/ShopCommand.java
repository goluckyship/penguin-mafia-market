package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * /shop: a straightforward Frozen-Coin-for-building-blocks store. Only
 * plain building/decoration blocks are sold here (wool, terracotta,
 * concrete, planks, stone variants, glass, and the like) - nothing rare or
 * valuable like ore/mineral blocks or netherite, so this is a convenience
 * for builders, not a way to buy power. Everything costs a flat 1,000
 * Frozen Coins per full stack of 64, rounded up for a partial stack.
 *
 * The amount argument accepts shorthand suffixes (k/m/b) so a big order
 * doesn't need a wall of zeros, and is forgiving - leaving it blank, or
 * tab-completing past the "<amount?>" hint without typing a real number,
 * just buys 1. A purchase is always capped to whatever actually fits in
 * the player's inventory, and they're only charged for what they receive -
 * which also means a shorthand like "1b" can never turn into an unbounded
 * giveaway loop.
 */
public class ShopCommand implements CommandExecutor, TabCompleter {

    private static final long PRICE_PER_STACK = 1000L;
    private static final String AMOUNT_HINT = "<amount?>";

    private final Economy economy;

    public ShopCommand(Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /shop.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            sendCatalog(player);
            return true;
        }

        Material material = BuildingBlockShop.lookup(args[0]);
        if (material == null) {
            player.sendMessage(ChatColor.RED + "\"" + args[0] + "\" isn't sold here."
                    + ChatColor.GRAY + " Tab-complete /shop <block>, or see /shop list.");
            return true;
        }

        long requested = args.length >= 2 ? parseAmount(args[1]) : 1L;

        ItemStack sample = new ItemStack(material);
        long capacity = inventoryCapacityFor(player, sample);
        if (capacity <= 0) {
            player.sendMessage(ChatColor.RED + "Your inventory is full - free up space before buying more.");
            return true;
        }

        long toBuy = Math.min(requested, capacity);
        long cost = (long) Math.ceil(toBuy / 64.0) * PRICE_PER_STACK;

        if (economy.getBalance(player) < cost) {
            player.sendMessage(ChatColor.RED + "That's " + CoinFormat.formatWithExact(cost) + " Frozen Coins"
                    + ChatColor.GRAY + " for " + toBuy + "x " + displayName(material) + " - you only have "
                    + CoinFormat.formatWithExact(economy.getBalance(player)) + ".");
            return true;
        }

        economy.removeBalance(player, cost);

        int remaining = (int) toBuy;
        int maxStack = sample.getMaxStackSize();
        while (remaining > 0) {
            int stack = Math.min(remaining, maxStack);
            player.getInventory().addItem(new ItemStack(material, stack));
            remaining -= stack;
        }

        String note = toBuy < requested
                ? ChatColor.GRAY.toString() + " (capped to what your inventory could hold - asked for " + requested + ")"
                : "";
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "[Shop] " + ChatColor.RESET + ChatColor.GRAY
                + "Bought " + ChatColor.WHITE + toBuy + "x " + displayName(material) + ChatColor.GRAY
                + " for " + ChatColor.AQUA + CoinFormat.formatWithExact(cost) + ChatColor.GRAY + " Frozen Coins." + note);
        player.sendMessage(ChatColor.GRAY + "Balance: " + ChatColor.AQUA + CoinFormat.formatWithExact(economy.getBalance(player)));
        return true;
    }

    /**
     * Mirrors MarketCommand's withdraw-capacity check: how many more of the
     * sample item the player's main inventory could actually accept right
     * now. This is what keeps a huge requested amount (shorthand like "1b")
     * from ever turning into an unbounded loop - the real cap is always
     * however many items can physically fit.
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

    /**
     * Turns the amount argument into a quantity. Accepts a plain number or
     * one with a k/m/b suffix (5k = 5000, 2.5m = 2500000, 1b = 1000000000).
     * Anything that isn't a real positive number - blank, the "<amount?>"
     * tab-complete hint, a typo - quietly defaults to 1 instead of erroring,
     * since this is meant to be quick to use.
     */
    private long parseAmount(String raw) {
        if (raw == null) return 1L;
        String s = raw.trim().toLowerCase();
        if (s.isEmpty()) return 1L;

        long multiplier = 1L;
        char last = s.charAt(s.length() - 1);
        if (last == 'k' || last == 'm' || last == 'b') {
            multiplier = last == 'k' ? 1_000L : last == 'm' ? 1_000_000L : 1_000_000_000L;
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) return 1L;

        double value;
        try {
            value = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 1L;
        }
        if (!(value > 0) || Double.isInfinite(value)) return 1L;

        double result = value * multiplier;
        if (!(result > 0) || Double.isInfinite(result)) return 1L;
        if (result > 1_000_000_000_000.0) result = 1_000_000_000_000.0; // 1 trillion hard cap
        return (long) result;
    }

    private String displayName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }

    private void sendCatalog(Player player) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "--- Penguin Mafia /shop ---");
        player.sendMessage(ChatColor.GRAY + "/shop <block> [amount] " + ChatColor.WHITE
                + "- buy a building block with your Frozen Coin balance");
        player.sendMessage(ChatColor.GRAY + "[amount] " + ChatColor.WHITE
                + "- a number, or shorthand like 5k / 2.5m / 1b. Leave it blank (or tab past it) for 1.");
        player.sendMessage(ChatColor.GRAY + "Price: " + ChatColor.AQUA + "1,000 Frozen Coins" + ChatColor.GRAY
                + " per full stack of 64, rounded up.");
        player.sendMessage(ChatColor.GRAY + "Tab-complete " + ChatColor.WHITE + "/shop " + ChatColor.GRAY
                + "to browse what's sold, or see the full list below:");
        for (BuildingBlockShop.Category category : BuildingBlockShop.CATEGORIES) {
            StringBuilder names = new StringBuilder();
            for (Material material : category.blocks) {
                if (names.length() > 0) names.append(", ");
                names.append(material.name().toLowerCase());
            }
            player.sendMessage(ChatColor.AQUA + category.name + " (" + category.blocks.size() + "): "
                    + ChatColor.GRAY + names);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String name : BuildingBlockShop.TAB_NAMES) {
                if (name.startsWith(prefix)) out.add(name);
            }
            if ("list".startsWith(prefix)) out.add("list");
        } else if (args.length == 2) {
            out.add(AMOUNT_HINT);
        }
        return out;
    }
}
