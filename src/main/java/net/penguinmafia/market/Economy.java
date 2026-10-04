package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Frozen Coins are tracked two ways, kept in sync deliberately:
 *  - a persistent per-player balance (used by the market for buying/selling,
 *    and directly adjustable by ops with /coins)
 *  - a physical item that players can carry, drop, or trade the old-fashioned
 *    way. It's built on Structure Void - a vanilla item with NO survival
 *    source whatsoever (can't be mined, fished, crafted, farmed, or dropped
 *    by anything; it only exists in creative/command inventories) - so it is
 *    unobtainable by design, not just by a hidden tag. It's also renamed,
 *    given lore, and tagged with a PersistentDataContainer marker as a
 *    belt-and-suspenders check, and MarketGUIListener blocks it from ever
 *    being placed as a block. Only this plugin can produce a real one -
 *    /bm withdraw, and Frozen Reaver kills in the Frozen Realm (see
 *    FrozenRealmMonsters) are the only two sources.
 * /bm deposit and /bm withdraw convert between the two.
 */
public class Economy {

    private final PenguinMafiaMarket plugin;
    private final File file;
    private final YamlConfiguration config;
    private final NamespacedKey coinKey;

    public Economy(PenguinMafiaMarket plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "economy.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        this.coinKey = new NamespacedKey(plugin, "frozen_coin");
    }

    public long getBalance(OfflinePlayer player) {
        return config.getLong(player.getUniqueId().toString(), 0L);
    }

    public void setBalance(OfflinePlayer player, long amount) {
        config.set(player.getUniqueId().toString(), Math.max(0L, amount));
        save();
    }

    public void addBalance(OfflinePlayer player, long amount) {
        setBalance(player, getBalance(player) + amount);
    }

    public boolean removeBalance(OfflinePlayer player, long amount) {
        long current = getBalance(player);
        if (current < amount) return false;
        setBalance(player, current - amount);
        return true;
    }

    /**
     * The top N balances on the server, highest first, for /baltop. Balances
     * live as raw UUID keys at the config root (see the class comment), while
     * "autodeposit" and "quietchat" are their own named sub-sections - so
     * simply skipping any root key that isn't a valid UUID safely excludes
     * those without needing to know their names here.
     */
    /**
     * Sum of every player's Frozen Coin balance right now - the total money
     * supply in circulation. Used by MarketBotManager to scale the Black
     * Market Dealer's prices with the server's economy instead of leaving
     * them as fixed numbers forever (see getInflationBaseline()).
     */
    public long getTotalCirculatingCoins() {
        long total = 0L;
        for (String key : config.getKeys(false)) {
            try {
                java.util.UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue; // not a player balance key (e.g. the inflation baseline, autodeposit, quietchat)
            }
            total += config.getLong(key, 0L);
        }
        return total;
    }

    /**
     * The total circulating coins recorded the first time inflation tracking
     * ran, used as the "1.0x" reference point - -1 if it's never been set.
     * Stored under a non-UUID key, so getTotalCirculatingCoins() and
     * getTopBalances() (which both skip non-UUID keys) never mistake it for
     * a player's balance.
     */
    public long getInflationBaseline() {
        return config.getLong("_inflation_baseline", -1L);
    }

    public void setInflationBaseline(long value) {
        config.set("_inflation_baseline", value);
        save();
    }

    public java.util.List<java.util.Map.Entry<java.util.UUID, Long>> getTopBalances(int limit) {
        java.util.List<java.util.Map.Entry<java.util.UUID, Long>> all = new java.util.ArrayList<>();
        for (String key : config.getKeys(false)) {
            java.util.UUID id;
            try {
                id = java.util.UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            long balance = config.getLong(key, 0L);
            if (balance > 0) {
                all.add(new java.util.AbstractMap.SimpleEntry<>(id, balance));
            }
        }
        all.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return all.subList(0, Math.min(limit, all.size()));
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save economy.yml: " + e.getMessage());
        }
    }

    /**
     * Whether this player has /autodeposit (/ad) turned on - stored under its
     * own top-level "autodeposit" section so it never collides with the raw
     * UUID keys used for balances above.
     */
    public boolean isAutoDepositEnabled(OfflinePlayer player) {
        return config.getBoolean("autodeposit." + player.getUniqueId(), false);
    }

    public void setAutoDepositEnabled(OfflinePlayer player, boolean enabled) {
        config.set("autodeposit." + player.getUniqueId(), enabled);
        save();
    }

    /**
     * Whether this player has turned off the /chat toggle - stops
     * Auto-Deposit and AFK Farm from printing a message every single time
     * they credit the player's balance, so those don't spam the screen.
     * Defaults to false (messages on), same as before this existed.
     */
    public boolean isChatQuiet(OfflinePlayer player) {
        return config.getBoolean("quietchat." + player.getUniqueId(), false);
    }

    public void setChatQuiet(OfflinePlayer player, boolean quiet) {
        config.set("quietchat." + player.getUniqueId(), quiet);
        save();
    }

    /** The vanilla item Frozen Coins are built on - has zero survival source. */
    public static final Material COIN_MATERIAL = Material.STRUCTURE_VOID;

    /**
     * The physical Frozen Coin item - a Structure Void (unobtainable through
     * any survival action) renamed and tagged. Only this method (called from
     * /bm withdraw) can produce a real one.
     */
    public ItemStack coinItem(int amount) {
        ItemStack item = new ItemStack(COIN_MATERIAL, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Frozen Coin");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Official Penguin Mafia currency.",
                ChatColor.DARK_GRAY + "Can't be mined, fished, or crafted.",
                ChatColor.DARK_GRAY + "Get some from /bm withdraw, or by",
                ChatColor.DARK_GRAY + "hunting Frozen Reavers in the Frozen Realm."
        ));
        meta.getPersistentDataContainer().set(coinKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCoinItem(ItemStack item) {
        if (item == null || item.getType() != COIN_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(coinKey, PersistentDataType.BYTE);
    }
}
