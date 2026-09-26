package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Passive playtime rewards: every player online when the timer fires gets a
 * set amount of Frozen Coins added to their balance automatically, with a
 * small chat message telling them so. Op-configurable interval and amount.
 * Persisted to plugins/PenguinMafiaMarket/playtime-reward.yml.
 */
public class PlaytimeRewardManager {

    private final PenguinMafiaMarket plugin;
    private final Economy economy;
    private final File file;

    private int intervalMinutes = 20;
    private long amount = 5;
    private boolean enabled = true;

    private BukkitTask task;

    public PlaytimeRewardManager(PenguinMafiaMarket plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.file = new File(plugin.getDataFolder(), "playtime-reward.yml");
        load();
        start();
    }

    private void load() {
        if (!file.exists()) {
            save();
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        intervalMinutes = config.getInt("interval-minutes", 20);
        amount = config.getLong("amount", 5);
        enabled = config.getBoolean("enabled", true);
    }

    public void save() {
        FileConfiguration config = new YamlConfiguration();
        config.set("interval-minutes", intervalMinutes);
        config.set("amount", amount);
        config.set("enabled", enabled);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save playtime-reward.yml", e);
        }
    }

    private void start() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        long ticks = Math.max(20L, intervalMinutes * 60L * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::payOnlinePlayers, ticks, ticks);
    }

    private void payOnlinePlayers() {
        if (!enabled || amount <= 0) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            economy.addBalance(player, amount);
            player.sendMessage(ChatColor.AQUA + "+" + amount + " Frozen Coin" + (amount == 1 ? "" : "s")
                    + ChatColor.GRAY + " for playing - balance: " + economy.getBalance(player));
        }
    }

    public void setInterval(int minutes) {
        this.intervalMinutes = Math.max(1, minutes);
        save();
        start();
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    public void setAmount(long amount) {
        this.amount = Math.max(0, amount);
        save();
    }

    public long getAmount() {
        return amount;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
