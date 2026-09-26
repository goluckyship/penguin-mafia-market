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

    /** Lists an item for sale. Caller is responsible for having already removed it from the seller's inventory. */
    public Listing createListing(Player seller, ItemStack item, long price) {
        int id = nextId++;
        Listing listing = new Listing(id, seller.getUniqueId(), seller.getName(), item.clone(), price);
        listings.put(id, listing);
        save();
        return listing;
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

        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.seller);
        economy.addBalance(seller, listing.price);
        giveOrDrop(buyer, listing.item);

        Player sellerOnline = Bukkit.getPlayer(listing.seller);
        if (sellerOnline != null) {
            sellerOnline.sendMessage("§b[Black Market] §7" + buyer.getName() + " bought your "
                    + listing.item.getType() + " for §b" + listing.price + " Frozen Coins§7.");
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
