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
import java.util.concurrent.atomic.AtomicBoolean;

/** Holds every active listing in memory, backed by listings.yml. */
public class MarketManager {

    private final PenguinMafiaMarket plugin;
    private final Economy economy;
    private final File file;
    private final YamlConfiguration config;

    private final Map<Integer, Listing> listings = new LinkedHashMap<>();
    private int nextId = 1;

    /** Coalesces save() calls into at most one disk write in flight at a time - see save(). */
    private final AtomicBoolean saveInProgress = new AtomicBoolean(false);
    private volatile boolean saveQueued = false;

    /** Optional - set via setLedger() once PenguinMafiaMarket creates one, so every completed buy() gets recorded for /bm history. Left null and skipped if never set. */
    private TransactionLedger ledger;

    public void setLedger(TransactionLedger ledger) {
        this.ledger = ledger;
    }

    public MarketManager(PenguinMafiaMarket plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.file = new File(plugin.getDataFolder(), "listings.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        load();
    }

    public Economy getEconomy() {
        return economy;
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

    /**
     * Persists every listing to disk - called after every buy/sell/cancel/
     * admin edit, and once per batch from the Black Market Dealer's restock.
     * The in-memory `listings` map (and so the actual game effect of
     * whatever just happened) is already final the instant this is called;
     * only the YAML write is deferred to a background thread, coalesced so
     * at most one write is ever in flight. With up to several thousand
     * dealer listings now that every item is stocked many stacks deep,
     * re-serializing all of them synchronously on the main thread for every
     * single /bm click used to visibly stall the server and swallow fast
     * repeated clicks - see saveNow() for the one place a synchronous write
     * is still needed (plugin shutdown, where no async task gets to run).
     */
    public void save() {
        if (saveInProgress.compareAndSet(false, true)) {
            runAsyncSave();
        } else {
            // A write is already in flight - don't pile up a second one behind
            // it with a now-stale snapshot; just note that another pass is
            // needed once the current write finishes.
            saveQueued = true;
        }
    }

    private void runAsyncSave() {
        List<ListingSnapshot> snapshot = snapshotListings();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            writeSnapshot(snapshot);
            saveInProgress.set(false);
            if (saveQueued) {
                saveQueued = false;
                // Hop back onto the main thread to safely read `listings` again
                // (it's not thread-safe) before starting the next write.
                Bukkit.getScheduler().runTask(plugin, this::save);
            }
        });
    }

    /**
     * Writes out the current listings immediately and synchronously, on
     * whatever thread calls it - only safe/needed at plugin shutdown, since
     * Bukkit won't run a newly scheduled async task (what save() normally
     * uses) once the plugin is disabling.
     */
    public void saveNow() {
        writeSnapshot(snapshotListings());
    }

    /** Clones each listing's mutable state so the background thread never touches live objects. */
    private List<ListingSnapshot> snapshotListings() {
        List<ListingSnapshot> snapshot = new ArrayList<>(listings.size());
        for (Listing l : listings.values()) {
            snapshot.add(new ListingSnapshot(l.id, l.seller, l.sellerName, l.price, l.item.clone()));
        }
        return snapshot;
    }

    private void writeSnapshot(List<ListingSnapshot> snapshot) {
        YamlConfiguration out = new YamlConfiguration();
        for (ListingSnapshot l : snapshot) {
            String path = "listings." + l.id();
            out.set(path + ".seller", l.seller().toString());
            out.set(path + ".sellerName", l.sellerName());
            out.set(path + ".price", l.price());
            out.set(path + ".item", l.item());
        }
        try {
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save listings.yml: " + e.getMessage());
        }
    }

    /** Immutable, thread-safe copy of a Listing's fields at the moment of a save() call. */
    private record ListingSnapshot(int id, UUID seller, String sellerName, long price, ItemStack item) {}

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

    /**
     * Removes a single listing outright with no side effects - unlike
     * cancelListing(), nothing is handed back to anyone. Only safe to use on
     * a system/NPC listing (e.g. merging the Black Market Dealer's
     * duplicate stacks of the same material back into one listing); never
     * call this on a real player's listing, since they'd lose the item with
     * no refund.
     */
    public void removeListing(int id) {
        listings.remove(id);
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

        if (ledger != null) {
            String itemDescription = listing.item.getAmount() + "x " + listing.item.getType().toString().toLowerCase().replace('_', ' ');
            ledger.record(listing.seller, listing.sellerName, buyer.getUniqueId(), buyer.getName(), itemDescription, listing.price);
        }

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

    /**
     * Op admin tool (/bm admin refund): removes a listing and hands the item
     * back to its seller, but only if they're online right now to receive it
     * - returns false and leaves the listing untouched otherwise, so nothing
     * is lost silently. No coins change hands either way; this is for
     * returning a stuck item, not reversing a completed sale.
     */
    public boolean adminRefund(int id) {
        Listing listing = listings.get(id);
        if (listing == null) return false;
        Player seller = Bukkit.getPlayer(listing.seller);
        if (seller == null) return false;
        listings.remove(id);
        save();
        giveOrDrop(seller, listing.item);
        return true;
    }

    /**
     * Op admin tool (/bm admin wipe): deletes a listing outright with no
     * refund to anyone - for a bugged/stuck listing an admin has decided
     * isn't worth (or isn't possible to) hand back.
     */
    public boolean adminWipe(int id) {
        if (!listings.containsKey(id)) return false;
        listings.remove(id);
        save();
        return true;
    }

    /** Op admin tool (/bm admin setprice): corrects a mispriced listing without touching the item or seller. */
    public boolean adminSetPrice(int id, long price) {
        Listing listing = listings.get(id);
        if (listing == null || price <= 0) return false;
        listing.price = price;
        save();
        return true;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }
}
