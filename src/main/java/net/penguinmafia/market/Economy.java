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
 *    being placed as a block. Only /bm withdraw can produce a real one.
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

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save economy.yml: " + e.getMessage());
        }
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
                ChatColor.DARK_GRAY + "Can't be mined, fished, or crafted -",
                ChatColor.DARK_GRAY + "only comes from /bm withdraw."
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
