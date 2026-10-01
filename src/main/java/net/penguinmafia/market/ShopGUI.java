package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The /shop GUI: a paged catalog of every block BuildingBlockShop sells.
 * Hovering an item's tooltip shows its price. Left-click buys a stack;
 * right-click opens a quantity menu with bigger options (multiple stacks,
 * or "max I can afford and hold"). Every purchase - whatever the amount -
 * routes through purchase(), which caps to inventory capacity the same way
 * /bm withdraw does, so a player is never charged for more than they
 * actually receive.
 */
public class ShopGUI {

    static final int SIZE = 54;
    static final int ITEMS_PER_PAGE = 45; // bottom row reserved for navigation
    static final long PRICE_PER_STACK = 1000L;
    static final int STACK_SIZE = 64;

    private static final int PREV_SLOT = 45;
    private static final int CLOSE_SLOT = 48;
    private static final int PAGE_INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private static final int QTY_BACK_SLOT = 45;
    private static final int QTY_CLOSE_SLOT = 53;

    private final Economy economy;
    private final List<Material> allBlocks;

    public ShopGUI(Economy economy) {
        this.economy = economy;
        this.allBlocks = new ArrayList<>();
        for (BuildingBlockShop.Category category : BuildingBlockShop.CATEGORIES) {
            allBlocks.addAll(category.blocks);
        }
    }

    public int getTotalPages() {
        return Math.max(1, (int) Math.ceil(allBlocks.size() / (double) ITEMS_PER_PAGE));
    }

    /** Marks a catalog page inventory and remembers which material sits in which slot. */
    public static class CatalogHolder implements InventoryHolder {
        final int page;
        final Material[] slotMaterials = new Material[SIZE];

        CatalogHolder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    /** Marks a quantity-picker inventory for one material, and the amount each slot buys (-1 = "max"). */
    public static class QuantityHolder implements InventoryHolder {
        final Material material;
        final int originPage;
        final long[] slotAmounts = new long[27];

        QuantityHolder(Material material, int originPage) {
            this.material = material;
            this.originPage = originPage;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public void openCatalog(Player player, int page) {
        int totalPages = getTotalPages();
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        CatalogHolder holder = new CatalogHolder(page);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Penguin Mafia Shop "
                        + ChatColor.GRAY + "(" + (page + 1) + "/" + totalPages + ")");

        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = ITEMS_PER_PAGE; i < SIZE; i++) inv.setItem(i, filler);

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, allBlocks.size());
        for (int i = start; i < end; i++) {
            Material material = allBlocks.get(i);
            int slot = i - start;
            holder.slotMaterials[slot] = material;
            inv.setItem(slot, catalogItem(player, material));
        }

        if (page > 0) {
            inv.setItem(PREV_SLOT, namedItem(Material.ARROW, ChatColor.YELLOW + "" + ChatColor.BOLD + "<- Previous Page"));
        }
        if (page < totalPages - 1) {
            inv.setItem(NEXT_SLOT, namedItem(Material.ARROW, ChatColor.YELLOW + "" + ChatColor.BOLD + "Next Page ->"));
        }
        inv.setItem(PAGE_INFO_SLOT, namedItem(Material.BOOK, ChatColor.AQUA + "Page " + (page + 1) + " of " + totalPages));
        inv.setItem(CLOSE_SLOT, namedItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Close"));

        player.openInventory(inv);
    }

    private ItemStack catalogItem(Player player, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + displayName(material));
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Price: " + ChatColor.WHITE + CoinFormat.formatWithExact(PRICE_PER_STACK)
                        + ChatColor.GRAY + " per stack of " + STACK_SIZE,
                "",
                ChatColor.YELLOW + "Left-click " + ChatColor.GRAY + "to buy a stack",
                ChatColor.YELLOW + "Right-click " + ChatColor.GRAY + "for more quantities",
                "",
                ChatColor.GRAY + "Your balance: " + ChatColor.AQUA + CoinFormat.formatWithExact(economy.getBalance(player))
        ));
        item.setItemMeta(meta);
        return item;
    }

    public void openQuantityMenu(Player player, Material material, int originPage) {
        QuantityHolder holder = new QuantityHolder(material, originPage);
        Inventory inv = Bukkit.createInventory(holder, 27,
                ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Buy " + displayName(material));

        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        long[] stackCounts = {1, 5, 10, 32, 64};
        int[] slots = {10, 11, 12, 13, 14};
        for (int i = 0; i < stackCounts.length; i++) {
            long stacks = stackCounts[i];
            long amount = stacks * STACK_SIZE;
            long cost = stacks * PRICE_PER_STACK;

            ItemStack item = new ItemStack(material, STACK_SIZE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + stacks + " stack" + (stacks == 1 ? "" : "s")
                    + ChatColor.GRAY + " (" + amount + ")");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Cost: " + ChatColor.WHITE + CoinFormat.formatWithExact(cost),
                    "",
                    ChatColor.GRAY + "Capped to what fits in your",
                    ChatColor.GRAY + "inventory and what you can afford."
            ));
            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
            holder.slotAmounts[slots[i]] = amount;
        }

        ItemStack max = namedItem(Material.CHEST, ChatColor.GREEN + "" + ChatColor.BOLD + "Max I Can Afford & Hold");
        ItemMeta maxMeta = max.getItemMeta();
        maxMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Buys as many as your balance",
                ChatColor.GRAY + "and inventory space both allow."
        ));
        max.setItemMeta(maxMeta);
        inv.setItem(16, max);
        holder.slotAmounts[16] = -1; // sentinel for "max"

        inv.setItem(QTY_BACK_SLOT, namedItem(Material.ARROW, ChatColor.YELLOW + "" + ChatColor.BOLD + "<- Back to Catalog"));
        inv.setItem(QTY_CLOSE_SLOT, namedItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Close"));

        player.openInventory(inv);
    }

    /**
     * Buys up to `requested` of material for player. requested <= 0 means
     * "as much as possible" (the Max button). Always capped to whatever
     * actually fits in the player's inventory (mirrors /bm withdraw's
     * capacity check) so a purchase never costs more than what's received,
     * and in max mode also capped to what the balance can cover so it never
     * fails outright - it just buys as much as both constraints allow.
     */
    public void purchase(Player player, Material material, long requested) {
        ItemStack sample = new ItemStack(material);
        long capacity = inventoryCapacityFor(player, sample);
        if (capacity <= 0) {
            player.sendMessage(ChatColor.RED + "Your inventory is full - free up space before buying more.");
            return;
        }

        long balance = economy.getBalance(player);
        boolean maxMode = requested <= 0;
        long toBuy;

        if (maxMode) {
            long affordableItems = (balance / PRICE_PER_STACK) * STACK_SIZE;
            toBuy = Math.min(affordableItems, capacity);
            if (toBuy <= 0) {
                player.sendMessage(ChatColor.RED + "You can't afford even one stack of " + displayName(material)
                        + " (" + CoinFormat.formatWithExact(PRICE_PER_STACK) + " coins) - balance: "
                        + CoinFormat.formatWithExact(balance) + ".");
                return;
            }
        } else {
            toBuy = Math.min(requested, capacity);
        }

        long cost = (long) Math.ceil(toBuy / (double) STACK_SIZE) * PRICE_PER_STACK;

        if (!maxMode && balance < cost) {
            player.sendMessage(ChatColor.RED + "That's " + CoinFormat.formatWithExact(cost) + " Frozen Coins"
                    + ChatColor.GRAY + " for " + toBuy + "x " + displayName(material) + " - you only have "
                    + CoinFormat.formatWithExact(balance) + ".");
            return;
        }

        economy.removeBalance(player, cost);
        giveItems(player, material, toBuy);

        String note = (!maxMode && toBuy < requested)
                ? ChatColor.GRAY.toString() + " (capped to what your inventory could hold)"
                : "";
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "[Shop] " + ChatColor.RESET + ChatColor.GRAY
                + "Bought " + ChatColor.WHITE + toBuy + "x " + displayName(material) + ChatColor.GRAY
                + " for " + ChatColor.AQUA + CoinFormat.formatWithExact(cost) + ChatColor.GRAY + " Frozen Coins." + note);
        player.sendMessage(ChatColor.GRAY + "Balance: " + ChatColor.AQUA + CoinFormat.formatWithExact(economy.getBalance(player)));
    }

    /**
     * Mirrors MarketCommand's withdraw-capacity check: how many more of the
     * sample item the player's main inventory could actually accept right
     * now. This is what keeps a purchase from ever costing more than what's
     * physically received.
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

    private void giveItems(Player player, Material material, long amount) {
        int remaining = (int) amount;
        int maxStack = material.getMaxStackSize();
        while (remaining > 0) {
            int stack = Math.min(remaining, maxStack);
            player.getInventory().addItem(new ItemStack(material, stack));
            remaining -= stack;
        }
    }

    private String displayName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }

    private static ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
