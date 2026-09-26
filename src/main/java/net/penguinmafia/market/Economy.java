package net.penguinmafia.market;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;

/**
 * Frozen Coins are tracked two ways, kept in sync deliberately:
 *  - a persistent per-player balance (used by the market for buying/selling,
 *    and directly adjustable by ops with /coins)
 *  - a physical item (a plain Prismarine Shard - no renaming needed, just the
 *    vanilla item you get from killing Guardians) that players can carry,
 *    drop, or trade the old-fashioned way.
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

    /**
     * The physical Frozen Coin item - a plain, unrenamed Prismarine Shard.
     * No renaming, no commands: any Prismarine Shard from any source (killing
     * Guardians, /give, another player) works as a coin.
     */
    public static ItemStack coinItem(int amount) {
        return new ItemStack(Material.PRISMARINE_SHARD, amount);
    }

    public static boolean isCoinItem(ItemStack item) {
        return item != null && item.getType() == Material.PRISMARINE_SHARD;
    }
}
