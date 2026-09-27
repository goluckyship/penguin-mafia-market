package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the /bm inventory screens: the main browse view (one item per row,
 * click to buy, with a search bar and price sorting) and the "My Listings"
 * management view (the same layout, but clicking an item cancels it and
 * hands it back instead of buying it). A shared bottom row of controls
 * (paging, search, my-listings/back, an info tile, and a page indicator)
 * keeps both views consistent, with the unused corners filled with glass
 * panes instead of left blank.
 */
public class MarketGUI {

    public static final String BROWSE_TITLE = ChatColor.DARK_PURPLE + "Black Market";
    public static final String MY_LISTINGS_TITLE = ChatColor.DARK_PURPLE + "Black Market" + ChatColor.GRAY + " - My Listings";
    private static final int PAGE_SIZE = 45; // bottom row reserved for controls

    public static void open(Player player, MarketManager market) {
        open(player, market, 0, null);
    }

    public static void open(Player player, MarketManager market, int page) {
        open(player, market, page, null);
    }

    public static void open(Player player, MarketManager market, int page, String filter) {
        List<Listing> matches = MarketManager.filter(market.getAllListings(), filter);
        matches.sort(Comparator.comparingLong(l -> l.price));
        build(player, matches, page, MarketHolder.Mode.BROWSE, filter, BROWSE_TITLE);
    }

    public static void openMyListings(Player player, MarketManager market, int page) {
        List<Listing> mine = market.getListingsBy(player.getUniqueId());
        mine.sort(Comparator.comparingInt(l -> l.id));
        build(player, mine, page, MarketHolder.Mode.MY_LISTINGS, null, MY_LISTINGS_TITLE);
    }

    private static void build(Player player, List<Listing> shown, int page, MarketHolder.Mode mode, String filter, String title) {
        Inventory inv = Bukkit.createInventory(new MarketHolder(page, mode, filter), 54, title);

        int start = page * PAGE_SIZE;
        int totalPages = Math.max(1, (int) Math.ceil(shown.size() / (double) PAGE_SIZE));
        if (start >= shown.size() && start > 0) {
            start = Math.max(0, (totalPages - 1) * PAGE_SIZE);
            page = totalPages - 1;
        }
        int end = Math.min(start + PAGE_SIZE, shown.size());

        for (int i = start; i < end; i++) {
            Listing listing = shown.get(i);
            inv.setItem(i - start, mode == MarketHolder.Mode.MY_LISTINGS
                    ? buildMyListingItem(listing)
                    : buildDisplayItem(listing));
        }

        if (shown.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            if (mode == MarketHolder.Mode.MY_LISTINGS) {
                meta.setDisplayName(ChatColor.GRAY + "You have no active listings");
                meta.setLore(List.of(ChatColor.DARK_GRAY + "Use /bm sell <price> to list the item in your hand."));
            } else if (filter != null && !filter.isBlank()) {
                meta.setDisplayName(ChatColor.GRAY + "No listings match \"" + filter + "\"");
                meta.setLore(List.of(ChatColor.DARK_GRAY + "Click the search icon below to try a different search."));
            } else {
                meta.setDisplayName(ChatColor.GRAY + "No listings yet");
                meta.setLore(List.of(ChatColor.DARK_GRAY + "Use /bm sell <price> to list an item."));
            }
            empty.setItemMeta(meta);
            inv.setItem(22, empty);
        }

        // Bottom control row - fill it entirely so nothing looks unfinished,
        // then lay the real buttons over the filler.
        ItemStack filler = pane();
        for (int slot = 45; slot < 54; slot++) inv.setItem(slot, filler);

        if (page > 0) {
            inv.setItem(45, navItem(Material.ARROW, "Previous Page"));
        }
        if (end < shown.size()) {
            inv.setItem(53, navItem(Material.ARROW, "Next Page"));
        }

        if (mode == MarketHolder.Mode.BROWSE) {
            inv.setItem(46, toggleItem(Material.NAME_TAG, "My Listings", "View and cancel your own listings."));
            inv.setItem(47, toggleItem(Material.COMPASS, "Search", "Click, then type an item or seller name in chat.",
                    "Type \"cancel\" to back out."));
            if (filter != null && !filter.isBlank()) {
                inv.setItem(48, toggleItem(Material.BARRIER, "Clear Search", "Currently searching: " + ChatColor.WHITE + filter));
            }
        } else {
            inv.setItem(46, toggleItem(Material.ARROW, "Back to Market", "Return to browsing all listings."));
        }

        ItemStack info = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Frozen Coins");
        infoMeta.setLore(List.of(
                ChatColor.GRAY + "Left-click a listing to buy it.",
                ChatColor.GRAY + "/bm sell <price> to list your own.",
                ChatColor.GRAY + "/bm balance to check your coins.",
                ChatColor.GRAY + "/bm deposit and /bm withdraw to move",
                ChatColor.GRAY + "coins between balance and items.",
                "",
                ChatColor.DARK_GRAY + "Frozen Coins come from /bm withdraw",
                ChatColor.DARK_GRAY + "or from defeating Frozen Reavers"
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        ItemStack pageIndicator = new ItemStack(Material.PAPER);
        ItemMeta pageMeta = pageIndicator.getItemMeta();
        pageMeta.setDisplayName(ChatColor.YELLOW + "Page " + (page + 1) + " / " + totalPages);
        pageMeta.setLore(List.of(ChatColor.DARK_GRAY + shown.size() + " listing" + (shown.size() == 1 ? "" : "s")
                + (mode == MarketHolder.Mode.BROWSE ? " for sale" : " of yours")));
        pageIndicator.setItemMeta(pageMeta);
        inv.setItem(50, pageIndicator);

        player.openInventory(inv);
    }

    private static ItemStack pane() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack navItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack toggleItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + name);
        List<String> loreLines = new ArrayList<>();
        for (String line : lore) loreLines.add(ChatColor.GRAY + line);
        meta.setLore(loreLines);
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

    private static ItemStack buildMyListingItem(Listing listing) {
        ItemStack display = listing.item.clone();
        ItemMeta meta = display.getItemMeta();
        List<String> lore = new ArrayList<>();
        if (meta.hasLore()) lore.addAll(meta.getLore());
        lore.add("");
        lore.add(ChatColor.GRAY + "Price: " + ChatColor.AQUA + listing.price + " Frozen Coins");
        lore.add(ChatColor.DARK_GRAY + "Listing #" + listing.id);
        lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Click to cancel");
        lore.add(ChatColor.GRAY + "(item is returned to you)");
        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }
}
