package net.penguinmafia.market;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A rolling record of completed Black Market sales, kept so a dispute ("I
 * never got paid for my diamonds") has an actual paper trail instead of
 * relying on memory or server logs. Every successful MarketManager.buy()
 * records one Sale here. Only the most recent MAX_ENTRIES sales are kept
 * (oldest dropped first) so transactions.yml can't grow without bound on a
 * long-lived server - this is a recent-activity ledger for support lookups,
 * not a permanent audit log.
 */
public class TransactionLedger {

    public static class Sale {
        public final UUID sellerId;
        public final String sellerName;
        public final UUID buyerId;
        public final String buyerName;
        public final String itemDescription;
        public final long price;
        public final long timestampMillis;

        public Sale(UUID sellerId, String sellerName, UUID buyerId, String buyerName,
                    String itemDescription, long price, long timestampMillis) {
            this.sellerId = sellerId;
            this.sellerName = sellerName;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
            this.itemDescription = itemDescription;
            this.price = price;
            this.timestampMillis = timestampMillis;
        }
    }

    private static final int MAX_ENTRIES = 500;
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final PenguinMafiaMarket plugin;
    private final File file;
    private final YamlConfiguration config;

    /** Newest entry last, same order they happened in - trimmed from the front when over MAX_ENTRIES. */
    private final List<Sale> sales = new ArrayList<>();

    public TransactionLedger(PenguinMafiaMarket plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "transactions.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        for (Map<?, ?> row : config.getMapList("sales")) {
            try {
                UUID sellerId = UUID.fromString(String.valueOf(row.get("seller")));
                String sellerName = String.valueOf(row.get("sellerName"));
                UUID buyerId = UUID.fromString(String.valueOf(row.get("buyer")));
                String buyerName = String.valueOf(row.get("buyerName"));
                String item = String.valueOf(row.get("item"));
                long price = row.get("price") instanceof Number ? ((Number) row.get("price")).longValue() : 0L;
                long time = row.get("time") instanceof Number ? ((Number) row.get("time")).longValue() : 0L;
                sales.add(new Sale(sellerId, sellerName, buyerId, buyerName, item, price, time));
            } catch (Exception e) {
                // skip a malformed row rather than failing the whole load
            }
        }
    }

    public void save() {
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (Sale sale : sales) {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("seller", sale.sellerId.toString());
            row.put("sellerName", sale.sellerName);
            row.put("buyer", sale.buyerId.toString());
            row.put("buyerName", sale.buyerName);
            row.put("item", sale.itemDescription);
            row.put("price", sale.price);
            row.put("time", sale.timestampMillis);
            serialized.add(row);
        }
        config.set("sales", serialized);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save transactions.yml: " + e.getMessage());
        }
    }

    public void record(UUID sellerId, String sellerName, UUID buyerId, String buyerName,
                        String itemDescription, long price) {
        sales.add(new Sale(sellerId, sellerName, buyerId, buyerName, itemDescription, price, System.currentTimeMillis()));
        while (sales.size() > MAX_ENTRIES) {
            sales.remove(0);
        }
        save();
    }

    /** Every sale a player appears in as either buyer or seller, most recent first, capped at `limit`. */
    public List<Sale> getHistoryFor(UUID playerId, int limit) {
        List<Sale> matches = new ArrayList<>();
        for (int i = sales.size() - 1; i >= 0 && matches.size() < limit; i--) {
            Sale sale = sales.get(i);
            if (sale.sellerId.equals(playerId) || sale.buyerId.equals(playerId)) {
                matches.add(sale);
            }
        }
        return matches;
    }

    /** The most recent sales server-wide, regardless of who was involved. */
    public List<Sale> getRecent(int limit) {
        List<Sale> recent = new ArrayList<>();
        for (int i = sales.size() - 1; i >= 0 && recent.size() < limit; i--) {
            recent.add(sales.get(i));
        }
        return recent;
    }

    /**
     * Total coin revenue per seller across every recorded sale (not just the
     * most recent MAX_ENTRIES - this sums whatever's currently in memory,
     * same data getRecent()/getHistoryFor() draw from), for /bm top. Sales to
     * the Black Market Dealer's own listings don't appear here since the
     * dealer's purchases from players ARE recorded as normal sales with a
     * real seller, so this naturally reflects real player-to-player plus
     * player-to-dealer-stock income.
     */
    public List<Map.Entry<UUID, Long>> getTopSellers(int limit) {
        Map<UUID, Long> totals = new java.util.HashMap<>();
        Map<UUID, String> names = new java.util.HashMap<>();
        for (Sale sale : sales) {
            totals.merge(sale.sellerId, sale.price, Long::sum);
            names.putIfAbsent(sale.sellerId, sale.sellerName);
        }

        List<Map.Entry<UUID, Long>> ranked = new ArrayList<>(totals.entrySet());
        ranked.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return ranked.subList(0, Math.min(limit, ranked.size()));
    }

    public static String formatTimestamp(long millis) {
        return TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(millis));
    }
}
