package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Paid personal storage, on top of whatever a player can carry or build
 * themselves - every player starts with one free 9-slot row, and can spend
 * Frozen Coins to unlock more rows, up to a 6-row (54-slot) chest. A second
 * coin sink in the same spirit as the Auction House's seller fee, and a
 * genuinely useful convenience: a place to park overflow loot without
 * needing to build (and remember the location of) another chest.
 *
 * Each player's contents are kept as a live Inventory while they're online
 * (so a GUI can actually show/edit it) and serialized out to warehouse.yml
 * on close/disable, same persistence shape as the rest of this plugin's
 * YAML-backed managers.
 */
public class WarehouseManager {

    /** Slots per row, and the free starting size (row 1). */
    private static final int SLOTS_PER_ROW = 9;
    private static final int STARTING_ROWS = 1;
    public static final int MAX_ROWS = 6;

    /** Coin cost to go from row N to row N+1 - gets steeper each row, same shape as a lot of this plugin's scaling costs. */
    public static long upgradeCost(int currentRows) {
        return 200L * currentRows * currentRows;
    }

    private final PenguinMafiaMarket plugin;
    private final Economy economy;
    private final File file;
    private final YamlConfiguration config;

    private final Map<UUID, Integer> rowsByPlayer = new HashMap<>();
    private final Map<UUID, ItemStack[]> contentsByPlayer = new HashMap<>();

    public WarehouseManager(PenguinMafiaMarket plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.file = new File(plugin.getDataFolder(), "warehouse.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        if (!config.contains("players")) return;
        for (String uuidKey : config.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID id = UUID.fromString(uuidKey);
                int rows = config.getInt("players." + uuidKey + ".rows", STARTING_ROWS);
                rowsByPlayer.put(id, rows);

                java.util.List<?> raw = config.getList("players." + uuidKey + ".contents");
                if (raw != null) {
                    ItemStack[] contents = new ItemStack[rows * SLOTS_PER_ROW];
                    for (int i = 0; i < raw.size() && i < contents.length; i++) {
                        Object o = raw.get(i);
                        if (o instanceof ItemStack stack) contents[i] = stack;
                    }
                    contentsByPlayer.put(id, contents);
                }
            } catch (IllegalArgumentException ignored) {
                // skip malformed entry
            }
        }
    }

    public void save() {
        config.set("players", null);
        for (Map.Entry<UUID, Integer> entry : rowsByPlayer.entrySet()) {
            String base = "players." + entry.getKey();
            config.set(base + ".rows", entry.getValue());
            ItemStack[] contents = contentsByPlayer.get(entry.getKey());
            if (contents != null) {
                config.set(base + ".contents", java.util.Arrays.asList(contents));
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save warehouse.yml: " + e.getMessage());
        }
    }

    public int getRows(UUID playerId) {
        return rowsByPlayer.getOrDefault(playerId, STARTING_ROWS);
    }

    /** Snapshots whatever is currently open in `live` back into this player's stored contents - call on inventory close. */
    public void storeContents(UUID playerId, ItemStack[] contents) {
        contentsByPlayer.put(playerId, contents.clone());
        save();
    }

    /** Builds (or rebuilds) the Inventory a player should see when they open /warehouse, filled from their saved contents. */
    public Inventory open(Player player) {
        int rows = getRows(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(null, rows * SLOTS_PER_ROW,
                ChatColor.DARK_PURPLE + player.getName() + "'s Warehouse");

        ItemStack[] saved = contentsByPlayer.get(player.getUniqueId());
        if (saved != null) {
            for (int i = 0; i < saved.length && i < inventory.getSize(); i++) {
                if (saved[i] != null) inventory.setItem(i, saved[i]);
            }
        }
        return inventory;
    }

    public enum UpgradeResult { OK, MAXED, CANT_AFFORD }

    public UpgradeResult upgrade(Player player) {
        int rows = getRows(player.getUniqueId());
        if (rows >= MAX_ROWS) return UpgradeResult.MAXED;

        long cost = upgradeCost(rows);
        if (!economy.removeBalance(player, cost)) return UpgradeResult.CANT_AFFORD;

        int newRows = rows + 1;
        rowsByPlayer.put(player.getUniqueId(), newRows);

        // Carry existing contents over into the bigger array so nothing already stored is lost.
        ItemStack[] old = contentsByPlayer.getOrDefault(player.getUniqueId(), new ItemStack[rows * SLOTS_PER_ROW]);
        ItemStack[] grown = new ItemStack[newRows * SLOTS_PER_ROW];
        System.arraycopy(old, 0, grown, 0, Math.min(old.length, grown.length));
        contentsByPlayer.put(player.getUniqueId(), grown);

        save();
        return UpgradeResult.OK;
    }
}
