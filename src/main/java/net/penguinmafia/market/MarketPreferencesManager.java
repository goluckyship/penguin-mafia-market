package net.penguinmafia.market;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers each player's last-used /bm search text, sort direction, and
 * category filter so they don't have to reselect them every time they open
 * the market - persisted to market_prefs.yml (same pattern as economy.yml
 * and listings.yml) and cached in memory once loaded.
 */
public class MarketPreferencesManager {

    private final PenguinMafiaMarket plugin;
    private final File file;
    private final YamlConfiguration config;
    private final Map<UUID, MarketPreferences> cache = new HashMap<>();

    public MarketPreferencesManager(PenguinMafiaMarket plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "market_prefs.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public MarketPreferences get(Player player) {
        return get(player.getUniqueId());
    }

    public MarketPreferences get(UUID id) {
        MarketPreferences cached = cache.get(id);
        if (cached != null) return cached;

        MarketPreferences prefs = new MarketPreferences();
        String path = id.toString();
        if (config.contains(path)) {
            prefs.filter = config.getString(path + ".filter", null);
            prefs.sortDescending = config.getBoolean(path + ".sortDescending", false);
            String categoryName = config.getString(path + ".category", MarketCategory.ALL.name());
            try {
                prefs.category = MarketCategory.valueOf(categoryName);
            } catch (IllegalArgumentException e) {
                prefs.category = MarketCategory.ALL;
            }
        }
        cache.put(id, prefs);
        return prefs;
    }

    public void setFilter(Player player, String filter) {
        MarketPreferences prefs = get(player);
        prefs.filter = (filter == null || filter.isBlank()) ? null : filter;
        persist(player.getUniqueId(), prefs);
    }

    public void setSortDescending(Player player, boolean sortDescending) {
        MarketPreferences prefs = get(player);
        prefs.sortDescending = sortDescending;
        persist(player.getUniqueId(), prefs);
    }

    public void setCategory(Player player, MarketCategory category) {
        MarketPreferences prefs = get(player);
        prefs.category = category;
        persist(player.getUniqueId(), prefs);
    }

    private void persist(UUID id, MarketPreferences prefs) {
        String path = id.toString();
        config.set(path + ".filter", prefs.filter);
        config.set(path + ".sortDescending", prefs.sortDescending);
        config.set(path + ".category", prefs.category.name());
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save market_prefs.yml: " + e.getMessage());
        }
    }
}
