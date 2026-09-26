package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;

/**
 * Frozen Coins are tracked two ways, kept in sync deliberately:
 *  - a persistent per-player balance (used by the market for buying/selling)
 *  - a physical item (renamed Prismarine Shard, matching the data pack's item)
 *    that players can carry, drop, or trade the old-fashioned way.
 * /bm deposit and /bm withdraw convert between the two.
 */
public class Economy {

    private final PenguinMafiaMarket plugin;
    private final File file;
    private final YamlConfiguration config;

    public Economy(PenguinMafiaMarket plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "economy.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
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

    /** The physical Frozen Coin item - matches the data pack's renamed Prismarine Shard. */
    public static ItemStack coinItem(int amount) {
        ItemStack item = new ItemStack(Material.PRISMARINE_SHARD, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Frozen Coin");
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isCoinItem(ItemStack item) {
        if (item == null || item.getType() != Material.PRISMARINE_SHARD) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && ChatColor.stripColor(meta.getDisplayName()).equals("Frozen Coin");
    }
}
