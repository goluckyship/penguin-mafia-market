package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the /bm inventory screen: one item per row, showing the seller and
 * price, click-to-buy. Uses a plain Inventory with a fixed title so
 * MarketGUIListener can recognize it by title when a click comes in.
 */
public class MarketGUI {

    public static final String TITLE = ChatColor.DARK_PURPLE + "Black Market";
    private static final int PAGE_SIZE = 45; // bottom row reserved for paging/info

    public static void open(Player player, MarketManager market) {
        open(player, market, 0);
    }

    public static void open(Player player, MarketManager market, int page) {
        List<Listing> all = market.getAllListings();
        Inventory inv = org.bukkit.Bukkit.createInventory(new MarketHolder(page), 54, TITLE);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, all.size());

        for (int i = start; i < end; i++) {
            Listing listing = all.get(i);
            inv.setItem(i - start, buildDisplayItem(listing));
        }

        if (all.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.setDisplayName(ChatColor.GRAY + "No listings yet");
            meta.setLore(List.of(ChatColor.DARK_GRAY + "Use /bm sell <price> to list an item."));
            empty.setItemMeta(meta);
            inv.setItem(22, empty);
        }

        if (page > 0) {
            inv.setItem(45, navItem(Material.ARROW, "Previous Page"));
        }
        if (end < all.size()) {
            inv.setItem(53, navItem(Material.ARROW, "Next Page"));
        }

        ItemStack info = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.AQUA + "Frozen Coins");
        infoMeta.setLore(List.of(
                ChatColor.GRAY + "Left-click an item to buy it.",
                ChatColor.GRAY + "/bm sell <price> to list your own.",
                ChatColor.GRAY + "/bm balance to check your coins."
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        player.openInventory(inv);
    }

    private static ItemStack navItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildDisplayItem(Listing listing) {
        ItemStack display = listing.item.clone();
        ItemMeta meta = display.getItemMeta();
        List<String> lore = new ArrayList<>();
        if (meta.hasLore()) lore.addAll(meta.getLore());
        lore.add("");
        lore.add(ChatColor.GRAY + "Seller: " + ChatColor.WHITE + listing.sellerName);
        lore.add(ChatColor.GRAY + "Price: " + ChatColor.AQUA + listing.price + " Frozen Coins");
        lore.add(ChatColor.DARK_GRAY + "Listing #" + listing.id);
        lore.add(ChatColor.YELLOW + "Click to buy");
        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }
}
