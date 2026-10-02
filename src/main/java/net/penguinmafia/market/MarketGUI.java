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
 * click to buy, with a search bar, price sorting, and a category filter) and
 * the "My Listings" management view (the same layout, but clicking an item
 * cancels it and hands it back instead of buying it). A shared bottom row of
 * controls (paging, search, sort, category, my-listings/back, an info tile,
 * and a page indicator) keeps both views consistent, with the unused corners
 * filled with glass panes instead of left blank.
 *
 * Instance-based (rather than the old static-method version) so it can hold
 * a MarketPreferencesManager and remember each player's last-used search
 * text, sort direction, and category filter across GUI closes and even
 * server restarts.
 */
public class MarketGUI {

    public static final String BROWSE_TITLE = ChatColor.DARK_PURPLE + "Black Market";
    public static final String MY_LISTINGS_TITLE = ChatColor.DARK_PURPLE + "Black Market" + ChatColor.GRAY + " - My Listings";
    private static final int PAGE_SIZE = 45; // bottom row reserved for controls

    private final MarketManager market;
    private final MarketPreferencesManager prefsManager;

    public MarketGUI(MarketManager market, MarketPreferencesManager prefsManager) {
        this.market = market;
        this.prefsManager = prefsManager;
    }

    /** Opens the browse view using the player's remembered filter/sort/category. */
    public void open(Player player) {
        MarketPreferences prefs = prefsManager.get(player);
        open(player, 0, prefs.filter, prefs.sortDescending, prefs.category);
    }

    public void open(Player player, int page) {
        MarketPreferences prefs = prefsManager.get(player);
        open(player, page, prefs.filter, prefs.sortDescending, prefs.category);
    }

    /**
     * Opens the browse view straight to a given search term - the command-line
     * shortcut (/bm search &lt;item&gt;) for players who don't want to click the
     * Compass and type in chat. Saves the term the same way the in-GUI search
     * does, so it's remembered and shows the same "Clear Search" button.
     */
    public void openWithSearch(Player player, String filter) {
        prefsManager.setFilter(player, filter);
        MarketPreferences prefs = prefsManager.get(player);
        open(player, 0, prefs.filter, prefs.sortDescending, prefs.category);
    }

    public void open(Player player, int page, String filter, boolean sortDescending, MarketCategory category) {
        List<Listing> matches = MarketManager.filter(market.getAllListings(), filter);
        if (category != null && category != MarketCategory.ALL) {
            List<Listing> byCategory = new ArrayList<>();
            for (Listing listing : matches) {
                if (MarketCategory.of(listing.item) == category) byCategory.add(listing);
            }
            matches = byCategory;
        }
        Comparator<Listing> byPrice = Comparator.comparingLong(l -> l.price);
        matches.sort(sortDescending ? byPrice.reversed() : byPrice);
        build(player, matches, page, MarketHolder.Mode.BROWSE, filter, sortDescending, category, BROWSE_TITLE);
    }

    public void openMyListings(Player player, int page) {
        List<Listing> mine = market.getListingsBy(player.getUniqueId());
        mine.sort(Comparator.comparingInt(l -> l.id));
        build(player, mine, page, MarketHolder.Mode.MY_LISTINGS, null, false, MarketCategory.ALL, MY_LISTINGS_TITLE);
    }

    private void build(Player player, List<Listing> shown, int page, MarketHolder.Mode mode, String filter,
                        boolean sortDescending, MarketCategory category, String title) {
        Inventory inv = Bukkit.createInventory(new MarketHolder(page, mode, filter, sortDescending, category), 54, title);

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
            } else if ((filter != null && !filter.isBlank()) || (category != null && category != MarketCategory.ALL)) {
                meta.setDisplayName(ChatColor.GRAY + "No listings match your filters");
                meta.setLore(List.of(ChatColor.DARK_GRAY + "Try clearing the search or category filter below."));
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
                    "Type \"cancel\" to back out.", "Remembered until you search again.",
                    "Tip: /bm search <item> does this in one line."));
            inv.setItem(48, sortItem(sortDescending));
            inv.setItem(51, categoryItem(category));
            if (filter != null && !filter.isBlank()) {
                inv.setItem(52, toggleItem(Material.BARRIER, "Clear Search", "Currently searching: " + ChatColor.WHITE + filter));
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
        pageMeta.setLore(List.of(ChatColor.DARK_GRAY + "" + shown.size() + " listing" + (shown.size() == 1 ? "" : "s")
                + (mode == MarketHolder.Mode.BROWSE ? " for sale" : " of yours")));
        pageIndicator.setItemMeta(pageMeta);
        inv.setItem(50, pageIndicator);

        player.openInventory(inv);
    }

    private ItemStack sortItem(boolean sortDescending) {
        return toggleItem(Material.HOPPER, "Sort: " + (sortDescending ? "Price High to Low" : "Price Low to High"),
                "Click to flip sort direction.", "Remembered next time you open the market.");
    }

    private ItemStack categoryItem(MarketCategory category) {
        MarketCategory shown = category == null ? MarketCategory.ALL : category;
        return toggleItem(Material.CHEST, "Category: " + shown.label,
                "Click to cycle All / Blocks / Armor / Materials.", "Remembered next time you open the market.");
    }

    private ItemStack pane() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack toggleItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + name);
        List<String> loreLines = new ArrayList<>();
        for (String line : lore) loreLines.add(ChatColor.GRAY + line);
        meta.setLore(loreLines);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildDisplayItem(Listing listing) {
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

    private ItemStack buildMyListingItem(Listing listing) {
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
