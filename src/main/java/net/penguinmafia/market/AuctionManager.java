package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * The Auction House - player-run timed auctions, separate from the Black
 * Market's fixed-price listings. A seller puts up an item with a starting
 * price and a duration; anyone can bid, each new bid has to beat the current
 * one by at least getMinIncrement(), and the highest bid when time runs out
 * wins. Money moves the moment a bid is placed (the bidder's coins are held
 * immediately, and a player who gets outbid is refunded immediately) so a
 * seller's payout at the end is never blocked on a buyer who logged off with
 * an empty balance.
 *
 * Offline delivery reuses the same idea as AfkFarmManager's mailbox: an item
 * that needs to reach a player who isn't online (an auction's winning item,
 * or an unsold item back to its seller, or a full inventory at delivery time)
 * is parked in a per-player mailbox list and handed over the next time they
 * join, instead of being lost.
 *
 * Persisted to auctions.yml, loaded once at startup and written back out
 * after every change - the same load-once-cache-forever pattern as
 * MarketManager and JobsManager.
 */
public class AuctionManager implements Listener {

    /** How often the expiry sweep runs. */
    private static final long CHECK_INTERVAL_TICKS = 20L * 15L; // every 15 seconds

    /** Minimum and maximum duration a seller can choose for a new auction. */
    public static final long MIN_DURATION_MINUTES = 5L;
    public static final long MAX_DURATION_MINUTES = TimeUnit.DAYS.toMinutes(3);

    /** A simple flat cut taken from the seller's proceeds when an auction sells - a coin sink, same spirit as nothing else in this plugin taxing trades, kept deliberately small. */
    private static final double SELLER_FEE_RATE = 0.05;

    private final PenguinMafiaMarket plugin;
    private final Economy economy;
    private final File file;
    private final YamlConfiguration config;

    private final Map<Integer, Auction> auctions = new HashMap<>();
    private final Map<UUID, List<ItemStack>> mailbox = new HashMap<>();
    private int nextId = 1;

    /** A completed auction's outcome, for /auction history - kept separately from the live `auctions` map once it's settled. */
    public static class HistoryEntry {
        public final int id;
        public final String sellerName;
        public final String itemDescription;
        public final long finalPrice;
        public final String winnerName; // null if nobody bid
        public final long timestampMillis;

        public HistoryEntry(int id, String sellerName, String itemDescription, long finalPrice,
                             String winnerName, long timestampMillis) {
            this.id = id;
            this.sellerName = sellerName;
            this.itemDescription = itemDescription;
            this.finalPrice = finalPrice;
            this.winnerName = winnerName;
            this.timestampMillis = timestampMillis;
        }

        public boolean sold() {
            return winnerName != null;
        }
    }

    private static final int MAX_HISTORY = 200;
    private final List<HistoryEntry> history = new ArrayList<>();

    public AuctionManager(PenguinMafiaMarket plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        nextId = config.getInt("nextId", 1);

        if (config.contains("auctions")) {
            for (String key : config.getConfigurationSection("auctions").getKeys(false)) {
                try {
                    int id = Integer.parseInt(key);
                    String base = "auctions." + key + ".";
                    UUID sellerId = UUID.fromString(config.getString(base + "sellerId"));
                    String sellerName = config.getString(base + "sellerName");
                    ItemStack item = (ItemStack) config.get(base + "item");
                    long startingPrice = config.getLong(base + "startingPrice");
                    long endTime = config.getLong(base + "endTime");

                    Auction auction = new Auction(id, sellerId, sellerName, item, startingPrice, endTime);
                    auction.currentBid = config.getLong(base + "currentBid", 0L);
                    String bidderId = config.getString(base + "currentBidderId", null);
                    if (bidderId != null) {
                        auction.currentBidderId = UUID.fromString(bidderId);
                        auction.currentBidderName = config.getString(base + "currentBidderName");
                    }
                    auctions.put(id, auction);
                } catch (Exception e) {
                    plugin.getLogger().warning("Skipping malformed auction entry '" + key + "': " + e.getMessage());
                }
            }
        }

        if (config.contains("mailbox")) {
            for (String uuidKey : config.getConfigurationSection("mailbox").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(uuidKey);
                    List<?> raw = config.getList("mailbox." + uuidKey, new ArrayList<>());
                    List<ItemStack> items = new ArrayList<>();
                    for (Object o : raw) {
                        if (o instanceof ItemStack stack) items.add(stack);
                    }
                    if (!items.isEmpty()) mailbox.put(id, items);
                } catch (IllegalArgumentException ignored) {
                    // not a UUID key, skip
                }
            }
        }

        for (Map<?, ?> row : config.getMapList("history")) {
            try {
                int id = row.get("id") instanceof Number ? ((Number) row.get("id")).intValue() : -1;
                String sellerName = String.valueOf(row.get("seller"));
                String itemDescription = String.valueOf(row.get("item"));
                long finalPrice = row.get("price") instanceof Number ? ((Number) row.get("price")).longValue() : 0L;
                String winnerName = row.get("winner") != null ? String.valueOf(row.get("winner")) : null;
                long time = row.get("time") instanceof Number ? ((Number) row.get("time")).longValue() : 0L;
                history.add(new HistoryEntry(id, sellerName, itemDescription, finalPrice, winnerName, time));
            } catch (Exception e) {
                // skip a malformed row
            }
        }
    }

    public void save() {
        config.set("auctions", null);
        config.set("nextId", nextId);
        for (Auction auction : auctions.values()) {
            String base = "auctions." + auction.id + ".";
            config.set(base + "sellerId", auction.sellerId.toString());
            config.set(base + "sellerName", auction.sellerName);
            config.set(base + "item", auction.item);
            config.set(base + "startingPrice", auction.startingPrice);
            config.set(base + "endTime", auction.endTimeMillis);
            config.set(base + "currentBid", auction.currentBid);
            config.set(base + "currentBidderId", auction.currentBidderId != null ? auction.currentBidderId.toString() : null);
            config.set(base + "currentBidderName", auction.currentBidderName);
        }

        config.set("mailbox", null);
        for (Map.Entry<UUID, List<ItemStack>> entry : mailbox.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                config.set("mailbox." + entry.getKey(), entry.getValue());
            }
        }

        List<Map<String, Object>> serializedHistory = new ArrayList<>();
        for (HistoryEntry entry : history) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", entry.id);
            row.put("seller", entry.sellerName);
            row.put("item", entry.itemDescription);
            row.put("price", entry.finalPrice);
            row.put("winner", entry.winnerName);
            row.put("time", entry.timestampMillis);
            serializedHistory.add(row);
        }
        config.set("history", serializedHistory);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save auctions.yml: " + e.getMessage());
        }
    }

    /** The smallest a new bid is allowed to beat the current price by - bigger on big-ticket auctions so bidding wars don't crawl up by 1 coin at a time. */
    public long getMinIncrement(Auction auction) {
        long base = auction.priceToBeat();
        return Math.max(1L, Math.round(base * 0.05));
    }

    public Auction createAuction(Player seller, ItemStack item, long startingPrice, long durationMinutes) {
        long clampedMinutes = Math.max(MIN_DURATION_MINUTES, Math.min(MAX_DURATION_MINUTES, durationMinutes));
        long endTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(clampedMinutes);
        Auction auction = new Auction(nextId++, seller.getUniqueId(), seller.getName(), item.clone(), startingPrice, endTime);
        auctions.put(auction.id, auction);
        save();
        return auction;
    }

    public Auction get(int id) {
        return auctions.get(id);
    }

    public List<Auction> getActiveAuctions() {
        List<Auction> list = new ArrayList<>(auctions.values());
        list.sort(Comparator.comparingLong(a -> a.endTimeMillis));
        return list;
    }

    public List<Auction> getAuctionsBySeller(UUID sellerId) {
        List<Auction> list = new ArrayList<>();
        for (Auction auction : auctions.values()) {
            if (auction.sellerId.equals(sellerId)) list.add(auction);
        }
        list.sort(Comparator.comparingLong(a -> a.endTimeMillis));
        return list;
    }

    public enum BidResult { OK, NOT_FOUND, EXPIRED, OWN_AUCTION, TOO_LOW, CANT_AFFORD }

    /**
     * Places a bid, immediately holding the bidder's coins and immediately
     * refunding whoever was outbid - so a balance never has to be chased down
     * after the fact. Returns the specific failure reason so the caller can
     * give a precise message.
     */
    public BidResult bid(Player bidder, int auctionId, long amount) {
        Auction auction = auctions.get(auctionId);
        if (auction == null) return BidResult.NOT_FOUND;
        if (auction.isExpired()) return BidResult.EXPIRED;
        if (auction.sellerId.equals(bidder.getUniqueId())) return BidResult.OWN_AUCTION;

        long minimumValid = auction.hasBid() ? auction.currentBid + getMinIncrement(auction) : auction.startingPrice;
        if (amount < minimumValid) return BidResult.TOO_LOW;
        if (!economy.removeBalance(bidder, amount)) return BidResult.CANT_AFFORD;

        // Refund whoever we're outbidding, now that the new bid is confirmed and held.
        if (auction.hasBid()) {
            OfflinePlayer previous = Bukkit.getOfflinePlayer(auction.currentBidderId);
            economy.addBalance(previous, auction.currentBid);
            if (previous.isOnline() && !economy.isChatQuiet(previous)) {
                Player previousPlayer = (Player) previous;
                previousPlayer.sendMessage(ChatColor.GOLD + "[Auction] " + ChatColor.GRAY
                        + "You were outbid on auction #" + auction.id + " - refunded "
                        + CoinFormat.format(auction.currentBid) + " Frozen Coins.");
            }
        }

        auction.currentBid = amount;
        auction.currentBidderId = bidder.getUniqueId();
        auction.currentBidderName = bidder.getName();
        save();
        return BidResult.OK;
    }

    public enum CancelResult { OK, NOT_FOUND, NOT_OWNER, HAS_BIDS }

    /** Cancels an auction with no bids yet, returning the item to the seller. Ops may force-cancel (see AuctionCommand) even with a bid, refunding the bidder. */
    public CancelResult cancel(OfflinePlayer requester, int auctionId, boolean allowWithBids) {
        Auction auction = auctions.get(auctionId);
        if (auction == null) return CancelResult.NOT_FOUND;
        if (!auction.sellerId.equals(requester.getUniqueId()) && !allowWithBids) return CancelResult.NOT_OWNER;
        if (auction.hasBid() && !allowWithBids) return CancelResult.HAS_BIDS;

        if (auction.hasBid()) {
            OfflinePlayer bidder = Bukkit.getOfflinePlayer(auction.currentBidderId);
            economy.addBalance(bidder, auction.currentBid);
        }
        deliver(auction.sellerId, auction.item);
        auctions.remove(auctionId);
        recordHistory(auction, null); // cancelled - never counts as a sale, even if it had a bid
        save();
        return CancelResult.OK;
    }

    private void recordHistory(Auction auction, String winnerName) {
        history.add(new HistoryEntry(auction.id, auction.sellerName, describe(auction.item),
                winnerName != null ? auction.currentBid : 0L, winnerName, System.currentTimeMillis()));
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }

    private String describe(ItemStack item) {
        String name = item.getType().name().toLowerCase().replace('_', ' ');
        return (item.getAmount() > 1 ? item.getAmount() + "x " : "") + name;
    }

    /** Past auctions a player was involved in as either seller or winner, most recent first. */
    public List<HistoryEntry> getHistoryFor(String playerName, int limit) {
        List<HistoryEntry> matches = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0 && matches.size() < limit; i--) {
            HistoryEntry entry = history.get(i);
            if (entry.sellerName.equalsIgnoreCase(playerName)
                    || (entry.winnerName != null && entry.winnerName.equalsIgnoreCase(playerName))) {
                matches.add(entry);
            }
        }
        return matches;
    }

    /** Runs the expiry sweep: settles every auction whose time is up, paying the seller (minus the fee) and delivering the item to the winner, or returning it to the seller if nobody bid. */
    public void checkExpired() {
        List<Auction> expired = new ArrayList<>();
        for (Auction auction : auctions.values()) {
            if (auction.isExpired()) expired.add(auction);
        }
        if (expired.isEmpty()) return;

        for (Auction auction : expired) {
            if (auction.hasBid()) {
                long fee = Math.round(auction.currentBid * SELLER_FEE_RATE);
                long payout = Math.max(0L, auction.currentBid - fee);
                OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.sellerId);
                economy.addBalance(seller, payout);
                notifyIfOnline(seller, ChatColor.GOLD + "[Auction] " + ChatColor.GRAY + "Your auction #"
                        + auction.id + " sold for " + CoinFormat.format(auction.currentBid)
                        + " - you received " + CoinFormat.format(payout) + " after the 5% fee.");

                OfflinePlayer winner = Bukkit.getOfflinePlayer(auction.currentBidderId);
                deliver(auction.currentBidderId, auction.item);
                notifyIfOnline(winner, ChatColor.GOLD + "[Auction] " + ChatColor.GRAY
                        + "You won auction #" + auction.id + "! The item has been delivered to your inventory"
                        + " (or mailed if it didn't fit).");
                recordHistory(auction, auction.currentBidderName);
            } else {
                OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.sellerId);
                deliver(auction.sellerId, auction.item);
                notifyIfOnline(seller, ChatColor.GOLD + "[Auction] " + ChatColor.GRAY + "Your auction #"
                        + auction.id + " ended with no bids - the item has been returned to you.");
                recordHistory(auction, null);
            }
            auctions.remove(auction.id);
        }
        save();
    }

    private void notifyIfOnline(OfflinePlayer player, String message) {
        if (player.isOnline()) {
            ((Player) player).sendMessage(message);
        }
    }

    /** Hands an item straight to the player if they're online and have room, otherwise parks it in their mailbox for next login. */
    private void deliver(UUID playerId, ItemStack item) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        if (offline.isOnline()) {
            Player player = (Player) offline;
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            if (leftover.isEmpty()) return;
            for (ItemStack remainder : leftover.values()) {
                mailbox.computeIfAbsent(playerId, k -> new ArrayList<>()).add(remainder);
            }
            player.sendMessage(ChatColor.GRAY + "[Auction] Your inventory was full - the rest was mailed to you.");
        } else {
            mailbox.computeIfAbsent(playerId, k -> new ArrayList<>()).add(item.clone());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<ItemStack> waiting = mailbox.remove(player.getUniqueId());
        if (waiting == null || waiting.isEmpty()) return;

        List<ItemStack> stillWaiting = new ArrayList<>();
        for (ItemStack item : waiting) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            stillWaiting.addAll(leftover.values());
        }
        if (!stillWaiting.isEmpty()) {
            mailbox.put(player.getUniqueId(), stillWaiting);
        }
        save();

        int delivered = waiting.size() - stillWaiting.size();
        if (delivered > 0) {
            player.sendMessage(ChatColor.GOLD + "[Auction] " + ChatColor.GRAY + "You had " + delivered
                    + " item(s) waiting from the Auction House - delivered to your inventory.");
        }
    }

    public static AuctionManager start(PenguinMafiaMarket plugin, Economy economy) {
        AuctionManager manager = new AuctionManager(plugin, economy);
        new BukkitRunnable() {
            @Override
            public void run() {
                manager.checkExpired();
            }
        }.runTaskTimer(plugin, 100L, CHECK_INTERVAL_TICKS);
        return manager;
    }
}
