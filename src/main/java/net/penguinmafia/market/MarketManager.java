package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Holds every active listing in memory, backed by listings.yml. */
public class MarketManager {

    private final PenguinMafiaMarket plugin;
    private final Economy economy;
    private final File file;
    private final YamlConfiguration config;

    private final Map<Integer, Listing> listings = new LinkedHashMap<>();
    private int nextId = 1;

    public MarketManager(PenguinMafiaMarket plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.file = new File(plugin.getDataFolder(), "listings.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        if (!config.contains("listings")) return;
        for (String key : config.getConfigurationSection("listings").getKeys(false)) {
            String path = "listings." + key;
            int id = Integer.parseInt(key);
            UUID seller = UUID.fromString(config.getString(path + ".seller"));
            String sellerName = config.getString(path + ".sellerName", "Unknown");
            long price = config.getLong(path + ".price");
            ItemStack item = config.getItemStack(path + ".item");
            if (item != null) {
                listings.put(id, new Listing(id, seller, sellerName, item, price));
                nextId = Math.max(nextId, id + 1);
            }
        }
    }

    public void save() {
        config.set("listings", null);
        for (Listing l : listings.values()) {
            String path = "listings." + l.id;
            config.set(path + ".seller", l.seller.toString());
            config.set(path + ".sellerName", l.sellerName);
            config.set(path + ".price", l.price);
            config.set(path + ".item", l.item);
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save listings.yml: " + e.getMessage());
        }
    }

    public int getListingCount() {
        return listings.size();
    }

    public List<Listing> getAllListings() {
        return new ArrayList<>(listings.values());
    }

    public List<Listing> getListingsBy(UUID seller) {
        List<Listing> mine = new ArrayList<>();
        for (Listing l : listings.values()) {
            if (l.seller.equals(seller)) mine.add(l);
        }
        return mine;
    }

    public Listing getListing(int id) {
        return listings.get(id);
    }

    /**
     * Returns the listings whose item name or seller name contains the query
     * (case-insensitive). A blank or null query returns the list unchanged.
     */
    public static List<Listing> filter(List<Listing> all, String query) {
        if (query == null || query.isBlank()) return all;
        String needle = query.trim().toLowerCase();
        List<Listing> out = new ArrayList<>();
        for (Listing l : all) {
            if (itemName(l.item).toLowerCase().contains(needle)
                    || l.sellerName.toLowerCase().contains(needle)) {
                out.add(l);
            }
        }
        return out;
    }

    private static String itemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        return item.getType().toString().toLowerCase().replace('_', ' ');
    }

    /** Lists an item for sale. Caller is responsible for having already removed it from the seller's inventory. */
    public Listing createListing(Player seller, ItemStack item, long price) {
        int id = nextId++;
        Listing listing = new Listing(id, seller.getUniqueId(), seller.getName(), item.clone(), price);
        listings.put(id, listing);
        save();
        return listing;
    }

    /**
     * Lists an item under a system/NPC seller (e.g. the Black Market Dealer)
     * rather than a real player - no item is taken from any inventory, since
     * there's no player to take it from. Does NOT call save() - callers doing
     * this in bulk (hundreds/thousands of listings at once) should add them
     * all first and call save() once at the end, rather than writing
     * listings.yml to disk on every single listing.
     */
    public Listing createSystemListing(UUID seller, String sellerName, ItemStack item, long price) {
        int id = nextId++;
        Listing listing = new Listing(id, seller, sellerName, item.clone(), price);
        listings.put(id, listing);
        return listing;
    }

    /**
     * Removes every listing belonging to the given seller UUID (used to clear
     * a system seller's old stock before restocking). Does NOT call save() -
     * pair with a save() once the caller is done making its batch of changes.
     */
    public void removeListingsBySeller(UUID seller) {
        listings.entrySet().removeIf(entry -> entry.getValue().seller.equals(seller));
    }

    /** Cancels a listing and returns the item to the seller (dropped at their feet if inventory is full). */
    public boolean cancelListing(int id, Player requester) {
        Listing listing = listings.get(id);
        if (listing == null || !listing.seller.equals(requester.getUniqueId())) return false;
        listings.remove(id);
        save();
        giveOrDrop(requester, listing.item);
        return true;
    }

    /** Buys a listing: charges the buyer, credits the seller, hands over the item. */
    public boolean buy(int id, Player buyer) {
        Listing listing = listings.get(id);
        if (listing == null) return false;
        if (listing.seller.equals(buyer.getUniqueId())) return false; // can't buy your own listing
        if (!economy.removeBalance(buyer, listing.price)) return false;

        listings.remove(id);
        save();

        giveOrDrop(buyer, listing.item);

        // The Black Market Dealer isn't a real player - buying from its stock
        // sinks the coins out of the economy instead of crediting anyone,
        // rather than quietly piling up a balance on a fake account that
        // would otherwise show up as a ghost entry on /baltop.
        if (!listing.seller.equals(MarketBotManager.SELLER_ID)) {
            OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.seller);
            economy.addBalance(seller, listing.price);

            Player sellerOnline = Bukkit.getPlayer(listing.seller);
            if (sellerOnline != null) {
                sellerOnline.sendMessage("§b[Black Market] §7" + buyer.getName() + " bought your "
                        + listing.item.getType() + " for §b" + listing.price + " Frozen Coins§7.");
            }
        }
        return true;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }
}
