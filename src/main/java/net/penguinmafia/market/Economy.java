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
 *    way. It's a Prismarine Shard visually, but it's tagged with a hidden
 *    plugin-only marker (a PersistentDataContainer flag) so a Prismarine
 *    Shard a player mines, fishes, or gets from killing a Guardian does NOT
 *    count as a coin - only ones minted by this plugin (via /bm withdraw,
 *    or /coins for balance) do. That marker can't be replicated by normal
 *    survival gameplay, so it can't be farmed.
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

    /**
     * The physical Frozen Coin item - a Prismarine Shard with a hidden
     * plugin-only tag. Only this method (called from /bm withdraw) can
     * produce a real one; a plain mined/fished/looted Prismarine Shard will
     * not pass {@link #isCoinItem}.
     */
    public ItemStack coinItem(int amount) {
        ItemStack item = new ItemStack(Material.PRISMARINE_SHARD, amount);
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
        if (item == null || item.getType() != Material.PRISMARINE_SHARD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(coinKey, PersistentDataType.BYTE);
    }
}
